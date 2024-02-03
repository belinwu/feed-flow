package com.prof18.feedflow.android.home.components

import FeedFlowTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.pullrefresh.PullRefreshState
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prof18.feedflow.core.model.FeedFilter
import com.prof18.feedflow.core.model.FeedItem
import com.prof18.feedflow.core.model.FeedItemId
import com.prof18.feedflow.core.model.FeedItemUrlInfo
import com.prof18.feedflow.shared.domain.model.FeedUpdateStatus
import com.prof18.feedflow.shared.domain.model.FinishedFeedUpdateStatus
import com.prof18.feedflow.shared.domain.model.InProgressFeedUpdateStatus
import com.prof18.feedflow.shared.domain.model.NoFeedSourcesStatus
import com.prof18.feedflow.shared.presentation.preview.feedItemsForPreview
import com.prof18.feedflow.shared.ui.home.components.EmptyFeedView
import com.prof18.feedflow.shared.ui.home.components.NoFeedsSourceView
import com.prof18.feedflow.shared.ui.preview.FeedFlowPhonePreview
import kotlinx.collections.immutable.ImmutableList

@Composable
internal fun HomeScreenContent(
    paddingValues: PaddingValues,
    loadingState: FeedUpdateStatus,
    feedState: ImmutableList<FeedItem>,
    pullRefreshState: PullRefreshState,
    listState: LazyListState,
    currentFeedFilter: FeedFilter,
    onRefresh: () -> Unit = {},
    updateReadStatus: (Int) -> Unit,
    onFeedItemClick: (FeedItemUrlInfo) -> Unit,
    onBookmarkClick: (FeedItemId, Boolean) -> Unit,
    onReadStatusClick: (FeedItemId, Boolean) -> Unit,
    onCommentClick: (FeedItemUrlInfo) -> Unit,
    onAddFeedClick: () -> Unit,
    requestMoreItems: () -> Unit,
    onBackToTimelineClick: () -> Unit,
) {
    when {
        loadingState is NoFeedSourcesStatus -> {
            NoFeedsSourceView(
                modifier = Modifier
                    .padding(paddingValues),
                onAddFeedClick = {
                    onAddFeedClick()
                },
            )
        }

        !loadingState.isLoading() && feedState.isEmpty() -> {
            EmptyFeedView(
                modifier = Modifier
                    .padding(paddingValues),
                currentFeedFilter = currentFeedFilter,
                onReloadClick = onRefresh,
                onBackToTimelineClick = onBackToTimelineClick,
            )
        }

        else -> FeedWithContentView(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            feedUpdateStatus = loadingState,
            pullRefreshState = pullRefreshState,
            feedItems = feedState,
            lazyListState = listState,
            updateReadStatus = updateReadStatus,
            onFeedItemClick = onFeedItemClick,
            requestMoreItems = requestMoreItems,
            onBookmarkClick = onBookmarkClick,
            onReadStatusClick = onReadStatusClick,
            onCommentClick = onCommentClick,
        )
    }
}

@FeedFlowPhonePreview
@Composable
private fun HomeScreeContentLoadingPreview() {
    FeedFlowTheme {
        HomeScreenContent(
            paddingValues = PaddingValues(0.dp),
            loadingState = InProgressFeedUpdateStatus(
                refreshedFeedCount = 10,
                totalFeedCount = 42,
            ),
            feedState = feedItemsForPreview,
            pullRefreshState = rememberPullRefreshState(
                refreshing = false,
                onRefresh = { },
            ),
            listState = rememberLazyListState(),
            currentFeedFilter = FeedFilter.Timeline,
            updateReadStatus = {},
            onFeedItemClick = {},
            onAddFeedClick = {},
            onRefresh = {},
            requestMoreItems = {},
            onCommentClick = {},
            onBookmarkClick = { _, _ -> },
            onReadStatusClick = { _, _ -> },
            onBackToTimelineClick = {},
        )
    }
}

@FeedFlowPhonePreview
@Composable
private fun HomeScreeContentLoadedPreview() {
    FeedFlowTheme {
        HomeScreenContent(
            paddingValues = PaddingValues(0.dp),
            loadingState = FinishedFeedUpdateStatus,
            feedState = feedItemsForPreview,
            pullRefreshState = rememberPullRefreshState(
                refreshing = false,
                onRefresh = { },
            ),
            listState = rememberLazyListState(),
            currentFeedFilter = FeedFilter.Timeline,
            updateReadStatus = {},
            onFeedItemClick = {},
            onAddFeedClick = {},
            requestMoreItems = {},
            onCommentClick = {},
            onBookmarkClick = { _, _ -> },
            onReadStatusClick = { _, _ -> },
            onBackToTimelineClick = {},
        )
    }
}
