package com.prof18.feedflow.database

import app.cash.sqldelight.Transacter
import app.cash.sqldelight.TransactionWithoutReturn
import app.cash.sqldelight.adapter.primitive.IntColumnAdapter
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrDefault
import app.cash.sqldelight.db.SqlDriver
import co.touchlab.kermit.Logger
import com.prof18.feedflow.core.model.CategoryName
import com.prof18.feedflow.core.model.FeedFilter
import com.prof18.feedflow.core.model.FeedItem
import com.prof18.feedflow.core.model.FeedItemId
import com.prof18.feedflow.core.model.FeedSource
import com.prof18.feedflow.core.model.FeedSourceCategory
import com.prof18.feedflow.core.model.ParsedFeedSource
import com.prof18.feedflow.db.FeedFlowDB
import com.prof18.feedflow.db.Feed_item
import com.prof18.feedflow.db.Feed_source
import com.prof18.feedflow.db.SelectFeedUrls
import com.prof18.feedflow.db.SelectFeeds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

@Suppress("TooManyFunctions")
class DatabaseHelper(
    sqlDriver: SqlDriver,
    private val backgroundDispatcher: CoroutineDispatcher,
    private val logger: Logger,
) {
    private val dbRef: FeedFlowDB = FeedFlowDB(
        sqlDriver,
        feed_itemAdapter = Feed_item.Adapter(
            url_hashAdapter = IntColumnAdapter,
            feed_source_idAdapter = IntColumnAdapter,
        ),
        feed_sourceAdapter = Feed_source.Adapter(
            url_hashAdapter = IntColumnAdapter,
        ),
    )

    fun getDatabaseVersion(): Long =
        FeedFlowDB.Schema.version

    suspend fun getFeedSources(): List<FeedSource> = withContext(backgroundDispatcher) {
        dbRef.feedSourceQueries
            .selectFeedUrls()
            .executeAsList()
            .map(::transformToFeedSource)
    }

    fun getFeedSourcesFlow(): Flow<List<FeedSource>> =
        dbRef.feedSourceQueries
            .selectFeedUrls()
            .asFlow()
            .catch {
                logger.e(it) { "Something wrong while getting data from Database" }
            }
            .mapToList(backgroundDispatcher)
            .map { feedSources ->
                feedSources.map(::transformToFeedSource)
            }
            .flowOn(backgroundDispatcher)

    suspend fun getFeedItems(
        feedFilter: FeedFilter,
        pageSize: Long,
        offset: Long,
    ): List<SelectFeeds> = withContext(backgroundDispatcher) {
        dbRef.feedItemQueries
            .selectFeeds(
                feedSourceId = feedFilter.getFeedSourceId(),
                feedSourceCategoryId = feedFilter.getCategoryId(),
                isRead = feedFilter.getIsReadFlag(),
                pageSize = pageSize,
                offset = offset,
            )
            .executeAsList()
    }

    suspend fun insertCategories(categories: List<CategoryName>) =
        dbRef.transactionWithContext(backgroundDispatcher) {
            categories.forEach { category ->
                dbRef.feedSourceCategoryQueries.insertFeedSourceCategory(category.name)
            }
        }

    suspend fun insertFeedSource(feedSource: List<ParsedFeedSource>) {
        dbRef.transactionWithContext(backgroundDispatcher) {
            feedSource.forEach { feedSource ->
                if (feedSource.categoryName != null) {
                    dbRef.feedSourceQueries.insertFeedSource(
                        url_hash = feedSource.hashCode(),
                        url = feedSource.url,
                        title = feedSource.title,
                        title_ = feedSource.categoryName?.name.toString(),
                        logo_url = feedSource.logoUrl,
                    )
                } else {
                    dbRef.feedSourceQueries.insertFeedSourceWithNoCategory(
                        url_hash = feedSource.hashCode(),
                        url = feedSource.url,
                        title = feedSource.title,
                        logo_url = feedSource.logoUrl,
                    )
                }
            }
        }
    }

    suspend fun insertFeedItems(feedItems: List<FeedItem>, lastSyncTimestamp: Long) =
        dbRef.transactionWithContext(backgroundDispatcher) {
            for (feedItem in feedItems) {
                with(feedItem) {
                    dbRef.feedItemQueries.insertFeedItem(
                        url_hash = id,
                        url = url,
                        title = title,
                        subtitle = subtitle,
                        content = null,
                        image_url = imageUrl,
                        feed_source_id = feedSource.id,
                        pub_date = pubDateMillis,
                        comments_url = commentsUrl,
                    )

                    dbRef.feedSourceQueries.updateLastSyncTimestamp(lastSyncTimestamp, feedSource.id)
                }
            }
        }

    suspend fun markAsRead(itemsToUpdates: List<FeedItemId>) =
        dbRef.transactionWithContext(backgroundDispatcher) {
            for (item in itemsToUpdates) {
                dbRef.feedItemQueries.markAsRead(item.id)
            }
        }

    suspend fun markAllFeedAsRead(feedFilter: FeedFilter) =
        dbRef.transactionWithContext(backgroundDispatcher) {
            when (feedFilter) {
                is FeedFilter.Category -> {
                    dbRef.feedItemQueries.markAllReadByCategory(feedFilter.feedCategory.id)
                }

                is FeedFilter.Source -> {
                    dbRef.feedItemQueries.markAllReadByFeedSource(feedFilter.feedSource.id)
                }

                FeedFilter.Timeline -> {
                    dbRef.feedItemQueries.markAllRead()
                }

                FeedFilter.Read -> {
                    // Do nothing
                }
            }
        }

    suspend fun deleteOldFeedItems(timeThreshold: Long) =
        dbRef.transactionWithContext(backgroundDispatcher) {
            dbRef.feedItemQueries.clearOldItems(timeThreshold)
        }

    suspend fun deleteFeedSource(feedSource: FeedSource) =
        dbRef.transactionWithContext(backgroundDispatcher) {
            dbRef.feedSourceQueries.deleteFeedSource(feedSource.id)
            dbRef.feedItemQueries.deleteAllWithFeedSource(feedSource.id)
        }

    fun observeFeedSourceCategories(): Flow<List<FeedSourceCategory>> =
        dbRef.feedSourceCategoryQueries.selectAll()
            .asFlow()
            .mapToList(backgroundDispatcher)
            .map { categories ->
                categories.map {
                    FeedSourceCategory(
                        id = it.id,
                        title = it.title,
                    )
                }
            }
            .flowOn(backgroundDispatcher)

    suspend fun getFeedSourceCategories(): List<FeedSourceCategory> =
        withContext(backgroundDispatcher) {
            dbRef.feedSourceCategoryQueries.selectAll()
                .executeAsList()
                .map { categories ->
                    FeedSourceCategory(
                        id = categories.id,
                        title = categories.title,
                    )
                }
        }

    suspend fun updateFeedSourceLogo(feedSource: FeedSource) =
        withContext(backgroundDispatcher) {
            dbRef.feedSourceQueries.updateLogoUrl(
                logoUrl = feedSource.logoUrl,
                urlHash = feedSource.id,
            )
        }

    fun deleteAllFeeds() =
        dbRef.transaction {
            dbRef.feedItemQueries.deleteAll()
            dbRef.feedSourceQueries.deleteAllLastSync()
        }

    fun getUnreadFeedCountFlow(feedFilter: FeedFilter): Flow<Long> =
        dbRef.feedItemQueries
            .countUnreadFeeds(
                feedSourceId = feedFilter.getFeedSourceId(),
                feedSourceCategoryId = feedFilter.getCategoryId(),
            )
            .asFlow()
            .catch {
                logger.e(it) { "Something wrong while getting data from Database" }
            }
            .mapToOneOrDefault(0, backgroundDispatcher)
            .flowOn(backgroundDispatcher)

    suspend fun deleteCategory(id: Long) =
        dbRef.transactionWithContext(backgroundDispatcher) {
            dbRef.feedSourceQueries.resetCategory(categoryId = id)
            dbRef.feedSourceCategoryQueries.delete(id = id)
        }

    private suspend fun Transacter.transactionWithContext(
        coroutineContext: CoroutineContext,
        noEnclosing: Boolean = false,
        body: TransactionWithoutReturn.() -> Unit,
    ) {
        withContext(coroutineContext) {
            this@transactionWithContext.transaction(noEnclosing) {
                body()
            }
        }
    }

    private fun FeedFilter.getFeedSourceId(): Int? {
        return when (this) {
            is FeedFilter.Source -> feedSource.id

            is FeedFilter.Category,
            FeedFilter.Timeline,
            FeedFilter.Read,
            -> null
        }
    }

    private fun FeedFilter.getCategoryId(): Long? {
        return when (this) {
            is FeedFilter.Category -> feedCategory.id

            is FeedFilter.Source,
            FeedFilter.Timeline,
            FeedFilter.Read,
            -> null
        }
    }

    private fun FeedFilter.getIsReadFlag(): Boolean? {
        return when (this) {
            is FeedFilter.Read -> true

            is FeedFilter.Category,
            is FeedFilter.Source,
            FeedFilter.Timeline,
            -> false
        }
    }

    private fun transformToFeedSource(feedSource: SelectFeedUrls): FeedSource {
        val category = if (feedSource.category_title != null && feedSource.category_id != null) {
            FeedSourceCategory(
                id = requireNotNull(feedSource.category_id),
                title = requireNotNull(feedSource.category_title),
            )
        } else {
            null
        }

        return FeedSource(
            id = feedSource.url_hash,
            url = feedSource.url,
            title = feedSource.feed_source_title,
            category = category,
            lastSyncTimestamp = feedSource.last_sync_timestamp,
            logoUrl = feedSource.feed_source_logo_url,
        )
    }

    internal companion object {
        const val DB_FILE_NAME_WITH_EXTENSION = "FeedFlow.db"
        const val DATABASE_NAME = "FeedFlowDB"
    }
}
