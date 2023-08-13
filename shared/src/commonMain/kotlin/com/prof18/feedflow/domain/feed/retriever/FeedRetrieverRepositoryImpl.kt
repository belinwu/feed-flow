package com.prof18.feedflow.domain.feed.retriever

import co.touchlab.kermit.Logger
import com.prof18.feedflow.data.DatabaseHelper
import com.prof18.feedflow.domain.DateFormatter
import com.prof18.feedflow.domain.HtmlParser
import com.prof18.feedflow.domain.feed.retriever.model.RssChannelResult
import com.prof18.feedflow.domain.model.FeedItem
import com.prof18.feedflow.domain.model.FeedItemId
import com.prof18.feedflow.domain.model.FeedSource
import com.prof18.feedflow.domain.model.FeedUpdateStatus
import com.prof18.feedflow.domain.model.FinishedFeedUpdateStatus
import com.prof18.feedflow.domain.model.InProgressFeedUpdateStatus
import com.prof18.feedflow.domain.model.NoFeedSourcesStatus
import com.prof18.feedflow.domain.model.StartedFeedUpdateStatus
import com.prof18.feedflow.presentation.model.DatabaseError
import com.prof18.feedflow.presentation.model.ErrorState
import com.prof18.feedflow.presentation.model.FeedErrorState
import com.prof18.feedflow.utils.DispatcherProvider
import com.prof18.feedflow.utils.getLimitedNumberOfConcurrentFeedSavers
import com.prof18.feedflow.utils.getLimitedNumberOfConcurrentParsingRequests
import com.prof18.feedflow.utils.getNumberOfConcurrentFeedSavers
import com.prof18.feedflow.utils.getNumberOfConcurrentParsingRequests
import com.prof18.rssparser.RssParser
import com.prof18.rssparser.model.RssChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("TooManyFunctions")
@OptIn(ExperimentalCoroutinesApi::class)
internal class FeedRetrieverRepositoryImpl(
    private val parser: RssParser,
    private val databaseHelper: DatabaseHelper,
    private val dispatcherProvider: DispatcherProvider,
    private val htmlParser: HtmlParser,
    private val logger: Logger,
    private val dateFormatter: DateFormatter,
) : FeedRetrieverRepository {

    private val updateMutableState: MutableStateFlow<FeedUpdateStatus> = MutableStateFlow(
        FinishedFeedUpdateStatus,
    )
    override val updateState = updateMutableState.asStateFlow()

    private val errorMutableState: MutableStateFlow<ErrorState?> = MutableStateFlow(null)
    override val errorState = errorMutableState.asStateFlow()

    private val feedToUpdate = hashSetOf<String>()

    override fun getFeeds(): Flow<List<FeedItem>> =
        databaseHelper.getFeedItems().map { feedList ->
            feedList.map { selectedFeed ->
                FeedItem(
                    id = selectedFeed.url_hash,
                    url = selectedFeed.url,
                    title = selectedFeed.title,
                    subtitle = selectedFeed.subtitle,
                    content = null,
                    imageUrl = selectedFeed.image_url,
                    feedSource = FeedSource(
                        id = selectedFeed.feed_source_id,
                        url = selectedFeed.feed_source_url,
                        title = selectedFeed.feed_source_title,
                        lastSyncTimestamp = selectedFeed.feed_source_last_sync_timestamp,
                    ),
                    isRead = selectedFeed.is_read,
                    pubDateMillis = selectedFeed.pub_date,
                    dateString = if (selectedFeed.pub_date != null) {
                        dateFormatter.formatDate(selectedFeed.pub_date)
                    } else {
                        null
                    },
                    commentsUrl = selectedFeed.comments_url,
                )
            }
        }.catch {
            logger.e(it) { "Something wrong while getting data from Database" }
            errorMutableState.update {
                DatabaseError
            }
        }.flowOn(dispatcherProvider.io)

    override suspend fun updateReadStatus(itemsToUpdates: List<FeedItemId>) =
        databaseHelper.updateReadStatus(itemsToUpdates)

    override suspend fun fetchFeeds(updateLoadingInfo: Boolean, forceRefresh: Boolean) =
        withContext(dispatcherProvider.io) {
            if (updateLoadingInfo) {
                updateMutableState.update { StartedFeedUpdateStatus }
            } else {
                updateMutableState.update { FinishedFeedUpdateStatus }
            }
            val feedSourceUrls = databaseHelper.getFeedSources()
            feedToUpdate.clear()
            feedToUpdate.addAll(feedSourceUrls.map { it.url })
            if (feedSourceUrls.isEmpty()) {
                updateMutableState.update {
                    NoFeedSourcesStatus
                }
            } else {
                databaseHelper.updateNewStatus()
                createFetchingPipeline(
                    feedSourceUrls = feedSourceUrls,
                    updateLoadingInfo = updateLoadingInfo,
                    forceRefresh = forceRefresh,
                )
            }
        }

    override suspend fun markAllFeedAsRead() {
        databaseHelper.markAllFeedAsRead()
    }

    @Suppress("MagicNumber")
    override suspend fun deleteOldFeeds() {
        // One week
        // (((1 hour in seconds) * 24 hours) * 7 days)
        val oneWeekInMillis = (((60 * 60) * 24) * 7) * 1000L
        val threshold = dateFormatter.currentTimeMillis() - oneWeekInMillis
        databaseHelper.deleteOldFeedItems(threshold)
    }

    private fun CoroutineScope.produceFeedSources(
        feedSourceUrls: List<FeedSource>,
        updateLoadingInfo: Boolean,
    ) = produce {
        if (updateLoadingInfo) {
            updateMutableState.emit(
                InProgressFeedUpdateStatus(
                    refreshedFeedCount = 0,
                    totalFeedCount = feedSourceUrls.size,
                ),
            )
        }
        for (feedSource in feedSourceUrls) {
            send(feedSource)
        }
    }

    private suspend fun createFetchingPipeline(
        feedSourceUrls: List<FeedSource>,
        updateLoadingInfo: Boolean,
        forceRefresh: Boolean,
    ) = coroutineScope {
        val feedSourcesChannel = produceFeedSources(feedSourceUrls, updateLoadingInfo)

        val feedToSaveChannel = setupParsersPipeline(
            feedSourceUrls = feedSourceUrls,
            feedSourcesChannel = feedSourcesChannel,
            forceRefresh = forceRefresh,
            updateLoadingInfo = updateLoadingInfo,
        )

        setupSaversPipeline(
            feedSourceUrls = feedSourceUrls,
            feedToSaveChannel = feedToSaveChannel,
            updateLoadingInfo = updateLoadingInfo,
        )
    }

    @Suppress("TooGenericExceptionCaught")
    private fun CoroutineScope.setupParsersPipeline(
        feedSourceUrls: List<FeedSource>,
        feedSourcesChannel: ReceiveChannel<FeedSource>,
        forceRefresh: Boolean,
        updateLoadingInfo: Boolean,
    ): ReceiveChannel<RssChannelResult> {
        val concurrentParsingRequests = if (feedSourceUrls.size > MAX_FEED_SIZE) {
            getLimitedNumberOfConcurrentParsingRequests()
        } else {
            getNumberOfConcurrentParsingRequests()
        }

        val feedToSaveChannel = produce(capacity = UNLIMITED) {
            repeat(concurrentParsingRequests) {
                launch(dispatcherProvider.default) {
                    for (feedSource in feedSourcesChannel) {
                        logger.d { "-> Getting ${feedSource.url}" }
                        try {
                            val shouldRefresh = shouldRefreshFeed(feedSource, forceRefresh)

                            if (shouldRefresh) {
                                val rssChannel = parser.getRssChannel(feedSource.url)
                                val result = RssChannelResult(
                                    rssChannel = rssChannel,
                                    feedSource = feedSource,
                                )
                                send(result)
                            } else {
                                logger.d { "One hour is not passed, skipping: ${feedSource.url}}" }
                                feedToUpdate.remove(feedSource.url)
                                if (updateLoadingInfo) {
                                    updateRefreshCount()
                                }
                            }
                        } catch (e: Throwable) {
                            logger.e(e) { "Something went wrong, skipping: ${feedSource.url}}" }
                            e.printStackTrace()
                            errorMutableState.update {
                                FeedErrorState(
                                    failingSourceName = feedSource.title,
                                )
                            }
                            feedToUpdate.remove(feedSource.url)
                            if (updateLoadingInfo) {
                                updateRefreshCount()
                            }
                        }
                    }
                }
            }
        }
        return feedToSaveChannel
    }

    private fun CoroutineScope.setupSaversPipeline(
        feedSourceUrls: List<FeedSource>,
        feedToSaveChannel: ReceiveChannel<RssChannelResult>,
        updateLoadingInfo: Boolean,
    ) {
        val concurrentSavers = if (feedSourceUrls.size > MAX_FEED_SIZE) {
            getLimitedNumberOfConcurrentFeedSavers()
        } else {
            getNumberOfConcurrentFeedSavers()
        }

        repeat(concurrentSavers) {
            launch(dispatcherProvider.io) {
                for (rssChannelResult in feedToSaveChannel) {
                    logger.d {
                        "<- Got back ${rssChannelResult.rssChannel.title}"
                    }

                    feedToUpdate.remove(rssChannelResult.feedSource.url)
                    if (updateLoadingInfo) {
                        updateRefreshCount()
                    }

                    val feedItems = rssChannelResult.rssChannel.getFeedItems(
                        feedSource = rssChannelResult.feedSource,
                    )
                    databaseHelper.insertFeedItems(feedItems)
                    databaseHelper.updateLastSyncTimestamp(
                        rssChannelResult.feedSource,
                        timestamp = dateFormatter.currentTimeMillis(),
                    )
                }
            }
        }
    }

    @Suppress("MagicNumber")
    private fun shouldRefreshFeed(
        feedSource: FeedSource,
        forceRefresh: Boolean,
    ): Boolean {
        val lastSyncTimestamp = feedSource.lastSyncTimestamp
        val oneHourInMillis = (60 * 60) * 1000
        val currentTime = dateFormatter.currentTimeMillis()
        return forceRefresh ||
            lastSyncTimestamp == null ||
            currentTime - lastSyncTimestamp >= oneHourInMillis
    }

    private fun updateRefreshCount() {
        updateMutableState.update { oldUpdate ->
            val refreshedFeedCount = oldUpdate.refreshedFeedCount + 1
            val totalFeedCount = oldUpdate.totalFeedCount

            if (feedToUpdate.isEmpty()) {
                FinishedFeedUpdateStatus
            } else {
                InProgressFeedUpdateStatus(
                    refreshedFeedCount = refreshedFeedCount,
                    totalFeedCount = totalFeedCount,
                )
            }
        }
    }

    @Suppress("MagicNumber")
    private fun RssChannel.getFeedItems(feedSource: FeedSource): List<FeedItem> =
        this.items.mapNotNull { rssItem ->

            val title = rssItem.title
            val url = rssItem.link
            val pubDate = rssItem.pubDate

            val dateMillis = if (pubDate != null) {
                dateFormatter.getDateMillisFromString(pubDate)
            } else {
                null
            }

            val imageUrl = if (rssItem.image?.contains("http:") == true) {
                rssItem.image?.replace("http:", "https:")
            } else {
                rssItem.image
            }

            if (title == null || url == null) {
                logger.d { "Skipping: $rssItem" }
                null
            } else {
                FeedItem(
                    id = url.hashCode(),
                    url = url,
                    title = title,
                    subtitle = rssItem.description?.let { description ->
                        htmlParser.getTextFromHTML(description)
                    },
                    content = null,
                    imageUrl = imageUrl,
                    feedSource = feedSource,
                    isRead = false,
                    pubDateMillis = dateMillis,
                    dateString = if (dateMillis != null) {
                        dateFormatter.formatDate(dateMillis)
                    } else {
                        null
                    },
                    commentsUrl = rssItem.commentsUrl,
                )
            }
        }

    private companion object {
        const val MAX_FEED_SIZE = 40
    }
}
