package com.prof18.feedflow.desktop.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuOpen
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import com.prof18.feedflow.MR
import com.prof18.feedflow.core.model.FeedFilter
import com.prof18.feedflow.core.model.FeedItem
import com.prof18.feedflow.core.model.FeedItemClickedInfo
import com.prof18.feedflow.core.model.NavDrawerState
import com.prof18.feedflow.desktop.openInBrowser
import com.prof18.feedflow.desktop.ui.components.FeedSourceLogoImage
import com.prof18.feedflow.desktop.utils.WindowWidthSizeClass
import com.prof18.feedflow.desktop.utils.calculateWindowSizeClass
import com.prof18.feedflow.shared.domain.model.FeedUpdateStatus
import com.prof18.feedflow.shared.domain.model.NoFeedSourcesStatus
import com.prof18.feedflow.shared.presentation.HomeViewModel
import com.prof18.feedflow.shared.ui.home.components.Drawer
import com.prof18.feedflow.shared.ui.home.components.EmptyFeedView
import com.prof18.feedflow.shared.ui.home.components.FeedItemView
import com.prof18.feedflow.shared.ui.home.components.FeedList
import com.prof18.feedflow.shared.ui.home.components.NoFeedsInfoContent
import com.prof18.feedflow.shared.ui.home.components.NoFeedsSourceView
import com.prof18.feedflow.shared.ui.style.Spacing
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.launch

@Suppress("LongMethod")
@Composable
internal fun HomeScreen(
    window: ComposeWindow,
    paddingValues: PaddingValues,
    homeViewModel: HomeViewModel,
    snackbarHostState: SnackbarHostState,
    listState: LazyListState,
    onAddFeedClick: () -> Unit,
    onImportExportClick: () -> Unit,
) {
    val loadingState by homeViewModel.loadingState.collectAsState()
    val feedState by homeViewModel.feedState.collectAsState()
    val navDrawerState by homeViewModel.navDrawerState.collectAsState()
    val currentFeedFilter by homeViewModel.currentFeedFilter.collectAsState()
    val unReadCount by homeViewModel.unreadCountFlow.collectAsState(initial = 0)

    LaunchedEffect(Unit) {
        homeViewModel.errorState.collect { errorState ->
            snackbarHostState.showSnackbar(
                errorState!!.message.localized(),
                duration = SnackbarDuration.Short,
            )
        }
    }

    val windowSize = calculateWindowSizeClass(window)

    when (windowSize.widthSizeClass) {
        WindowWidthSizeClass.Compact -> {
            CompactView(
                navDrawerState = navDrawerState,
                currentFeedFilter = currentFeedFilter,
                homeViewModel = homeViewModel,
                paddingValues = paddingValues,
                loadingState = loadingState,
                feedState = feedState,
                listState = listState,
                unReadCount = unReadCount,
                onAddFeedClick = onAddFeedClick,
                onImportExportClick = onImportExportClick,
            )
        }

        WindowWidthSizeClass.Medium -> {
            MediumView(
                navDrawerState = navDrawerState,
                currentFeedFilter = currentFeedFilter,
                homeViewModel = homeViewModel,
                paddingValues = paddingValues,
                loadingState = loadingState,
                feedState = feedState,
                listState = listState,
                unReadCount = unReadCount,
                onAddFeedClick = onAddFeedClick,
                onImportExportClick = onImportExportClick,
            )
        }

        WindowWidthSizeClass.Expanded -> {
            ExpandedView(
                navDrawerState = navDrawerState,
                currentFeedFilter = currentFeedFilter,
                homeViewModel = homeViewModel,
                paddingValues = paddingValues,
                loadingState = loadingState,
                feedState = feedState,
                listState = listState,
                unReadCount = unReadCount,
                onAddFeedClick = onAddFeedClick,
                onImportExportClick = onImportExportClick,
            )
        }
    }
}

@Suppress("LongMethod", "LongParameterList")
@Composable
private fun CompactView(
    navDrawerState: NavDrawerState,
    currentFeedFilter: FeedFilter,
    homeViewModel: HomeViewModel,
    paddingValues: PaddingValues,
    loadingState: FeedUpdateStatus,
    feedState: List<FeedItem>,
    listState: LazyListState,
    unReadCount: Long,
    onAddFeedClick: () -> Unit,
    onImportExportClick: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    if (feedState.isEmpty() && navDrawerState.isEmpty()) {
        HomeScreenContent(
            paddingValues = paddingValues,
            loadingState = loadingState,
            feedState = feedState,
            listState = listState,
            unReadCount = unReadCount,
            onRefresh = {
                homeViewModel.getNewFeeds()
            },
            updateReadStatus = { lastVisibleIndex ->
                homeViewModel.markAsReadOnScroll(lastVisibleIndex)
            },
            onFeedItemClick = { feedInfo ->
                openInBrowser(feedInfo.url)
                homeViewModel.markAsRead(feedInfo.id)
            },
            onFeedItemLongClick = { feedInfo ->
                openInBrowser(feedInfo.url)
                homeViewModel.markAsRead(feedInfo.id)
            },
            onAddFeedClick = {
                onAddFeedClick()
            },
            showDrawerMenu = true,
            currentFeedFilter = currentFeedFilter,
            onDrawerMenuClick = {
                scope.launch {
                    if (drawerState.isOpen) {
                        drawerState.close()
                    } else {
                        drawerState.open()
                    }
                }
            },
            requestMoreItems = {
                homeViewModel.requestNewFeedsPage()
            },
            onImportExportClick = onImportExportClick,
        )
    } else {
        ModalNavigationDrawer(
            drawerContent = {
                ModalDrawerSheet {
                    Drawer(
                        navDrawerState = navDrawerState,
                        currentFeedFilter = currentFeedFilter,
                        feedSourceImage = { imageUrl ->
                            FeedSourceImage(imageUrl)
                        },
                        onFeedFilterSelected = { feedFilter ->
                            homeViewModel.onFeedFilterSelected(feedFilter)
                            scope.launch {
                                drawerState.close()
                                listState.animateScrollToItem(0)
                            }
                        },
                    )
                }
            },
            drawerState = drawerState,
        ) {
            HomeScreenContent(
                paddingValues = paddingValues,
                loadingState = loadingState,
                feedState = feedState,
                listState = listState,
                unReadCount = unReadCount,
                currentFeedFilter = currentFeedFilter,
                onRefresh = {
                    homeViewModel.getNewFeeds()
                },
                updateReadStatus = { lastVisibleIndex ->
                    homeViewModel.markAsReadOnScroll(lastVisibleIndex)
                },
                onFeedItemClick = { feedInfo ->
                    openInBrowser(feedInfo.url)
                    homeViewModel.markAsRead(feedInfo.id)
                },
                onFeedItemLongClick = { feedInfo ->
                    openInBrowser(feedInfo.url)
                    homeViewModel.markAsRead(feedInfo.id)
                },
                onAddFeedClick = {
                    onAddFeedClick()
                },
                showDrawerMenu = true,
                onDrawerMenuClick = {
                    scope.launch {
                        if (drawerState.isOpen) {
                            drawerState.close()
                        } else {
                            drawerState.open()
                        }
                    }
                },
                requestMoreItems = {
                    homeViewModel.requestNewFeedsPage()
                },
                onImportExportClick = onImportExportClick,
            )
        }
    }
}

@Suppress("LongMethod", "LongParameterList")
@Composable
private fun MediumView(
    navDrawerState: NavDrawerState,
    currentFeedFilter: FeedFilter,
    homeViewModel: HomeViewModel,
    paddingValues: PaddingValues,
    loadingState: FeedUpdateStatus,
    feedState: List<FeedItem>,
    listState: LazyListState,
    unReadCount: Long,
    onAddFeedClick: () -> Unit,
    onImportExportClick: () -> Unit,
) {
    var isDrawerMenuFullVisible by remember {
        mutableStateOf(true)
    }
    val scope = rememberCoroutineScope()

    Row {
        AnimatedVisibility(
            modifier = Modifier
                .weight(1f),
            visible = isDrawerMenuFullVisible && (feedState.isNotEmpty() || navDrawerState.isNotEmpty()),
        ) {
            Scaffold { paddingValues ->
                Drawer(
                    modifier = Modifier
                        .padding(paddingValues),
                    navDrawerState = navDrawerState,
                    currentFeedFilter = currentFeedFilter,
                    feedSourceImage = { imageUrl ->
                        FeedSourceImage(imageUrl)
                    },
                    onFeedFilterSelected = { feedFilter ->
                        homeViewModel.onFeedFilterSelected(feedFilter)
                        scope.launch {
                            listState.animateScrollToItem(0)
                        }
                    },
                )
            }
        }

        HomeScreenContent(
            modifier = Modifier
                .weight(2f),
            paddingValues = paddingValues,
            loadingState = loadingState,
            feedState = feedState,
            listState = listState,
            unReadCount = unReadCount,
            onRefresh = {
                homeViewModel.getNewFeeds()
            },
            updateReadStatus = { lastVisibleIndex ->
                homeViewModel.markAsReadOnScroll(lastVisibleIndex)
            },
            onFeedItemClick = { feedInfo ->
                openInBrowser(feedInfo.url)
                homeViewModel.markAsRead(feedInfo.id)
            },
            onFeedItemLongClick = { feedInfo ->
                openInBrowser(feedInfo.url)
                homeViewModel.markAsRead(feedInfo.id)
            },
            onAddFeedClick = {
                onAddFeedClick()
            },
            showDrawerMenu = true,
            currentFeedFilter = currentFeedFilter,
            isDrawerMenuOpen = isDrawerMenuFullVisible,
            onDrawerMenuClick = {
                isDrawerMenuFullVisible = !isDrawerMenuFullVisible
            },
            requestMoreItems = {
                homeViewModel.requestNewFeedsPage()
            },
            onImportExportClick = onImportExportClick,
        )
    }
}

@Suppress("LongParameterList")
@Composable
private fun ExpandedView(
    navDrawerState: NavDrawerState,
    currentFeedFilter: FeedFilter,
    homeViewModel: HomeViewModel,
    paddingValues: PaddingValues,
    loadingState: FeedUpdateStatus,
    feedState: List<FeedItem>,
    listState: LazyListState,
    unReadCount: Long,
    onAddFeedClick: () -> Unit,
    onImportExportClick: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    Row {
        AnimatedVisibility(
            modifier = Modifier
                .weight(1f),
            visible = feedState.isNotEmpty() || navDrawerState.isNotEmpty(),
        ) {
            Scaffold { paddingValues ->
                Drawer(
                    modifier = Modifier
                        .padding(paddingValues),
                    navDrawerState = navDrawerState,
                    currentFeedFilter = currentFeedFilter,
                    feedSourceImage = { imageUrl ->
                        FeedSourceImage(imageUrl)
                    },
                    onFeedFilterSelected = { feedFilter ->
                        homeViewModel.onFeedFilterSelected(feedFilter)
                        scope.launch {
                            listState.animateScrollToItem(0)
                        }
                    },
                )
            }
        }

        HomeScreenContent(
            modifier = Modifier
                .weight(2f),
            paddingValues = paddingValues,
            loadingState = loadingState,
            feedState = feedState,
            listState = listState,
            unReadCount = unReadCount,
            currentFeedFilter = currentFeedFilter,
            onRefresh = {
                homeViewModel.getNewFeeds()
            },
            updateReadStatus = { lastVisibleIndex ->
                homeViewModel.markAsReadOnScroll(lastVisibleIndex)
            },
            onFeedItemClick = { feedInfo ->
                openInBrowser(feedInfo.url)
                homeViewModel.markAsRead(feedInfo.id)
            },
            onFeedItemLongClick = { feedInfo ->
                openInBrowser(feedInfo.url)
                homeViewModel.markAsRead(feedInfo.id)
            },
            onAddFeedClick = {
                onAddFeedClick()
            },
            requestMoreItems = {
                homeViewModel.requestNewFeedsPage()
            },
            onImportExportClick = onImportExportClick,
        )
    }
}

@Composable
private fun FeedSourceImage(imageUrl: String) {
    FeedSourceLogoImage(
        size = 24.dp,
        imageUrl = imageUrl,
    )
}

@Suppress("LongParameterList")
@Composable
private fun HomeScreenContent(
    paddingValues: PaddingValues,
    loadingState: FeedUpdateStatus,
    feedState: List<FeedItem>,
    listState: LazyListState,
    unReadCount: Long,
    showDrawerMenu: Boolean = false,
    isDrawerMenuOpen: Boolean = false,
    currentFeedFilter: FeedFilter,
    modifier: Modifier = Modifier,
    onDrawerMenuClick: () -> Unit = {},
    onRefresh: () -> Unit = {},
    updateReadStatus: (Int) -> Unit,
    onFeedItemClick: (FeedItemClickedInfo) -> Unit,
    onFeedItemLongClick: (FeedItemClickedInfo) -> Unit,
    onAddFeedClick: () -> Unit,
    requestMoreItems: () -> Unit,
    onImportExportClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .padding(paddingValues),
    ) {
        FeedContentToolbar(
            unReadCount = unReadCount,
            showDrawerMenu = showDrawerMenu,
            isDrawerOpen = isDrawerMenuOpen,
            onDrawerMenuClick = onDrawerMenuClick,
            currentFeedFilter = currentFeedFilter,
        )

        var showDialog by remember { mutableStateOf(false) }
        NoFeedsDialog(
            showDialog = showDialog,
            onDismissRequest = {
                showDialog = false
            },
            onAddFeedClick = onAddFeedClick,
            onImportExportClick = onImportExportClick,
        )

        when {
            loadingState is NoFeedSourcesStatus -> NoFeedsSourceView(
                onAddFeedClick = {
                    showDialog = true
                },
            )

            !loadingState.isLoading() && feedState.isEmpty() -> EmptyFeedView(
                onReloadClick = {
                    onRefresh()
                },
            )

            else -> FeedWithContentView(
                paddingValues = paddingValues,
                feedState = feedState,
                loadingState = loadingState,
                listState = listState,
                updateReadStatus = updateReadStatus,
                onFeedItemClick = onFeedItemClick,
                onFeedItemLongClick = onFeedItemLongClick,
                requestMoreItems = requestMoreItems,
            )
        }
    }
}

@Composable
private fun NoFeedsDialog(
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
    onAddFeedClick: () -> Unit,
    onImportExportClick: () -> Unit,
) {
    val dialogTitle = stringResource(MR.strings.no_feed_modal_title)
    DialogWindow(
        title = dialogTitle,
        visible = showDialog,
        onCloseRequest = onDismissRequest,
    ) {
        Scaffold {
            NoFeedsInfoContent(
                showTitle = false,
                onDismissRequest = onDismissRequest,
                onAddFeedClick = {
                    onDismissRequest()
                    onAddFeedClick()
                },
                onImportExportClick = {
                    onDismissRequest()
                    onImportExportClick()
                },
            )
        }
    }
}

@Composable
private fun FeedWithContentView(
    modifier: Modifier = Modifier,
    paddingValues: PaddingValues,
    feedState: List<FeedItem>,
    loadingState: FeedUpdateStatus,
    listState: LazyListState,
    updateReadStatus: (Int) -> Unit,
    onFeedItemClick: (FeedItemClickedInfo) -> Unit,
    onFeedItemLongClick: (FeedItemClickedInfo) -> Unit,
    requestMoreItems: () -> Unit,
) {
    Column(
        modifier = modifier,
    ) {
        FeedLoader(loadingState = loadingState)

        Box(
            modifier = Modifier.fillMaxSize()
                .padding(paddingValues)
                .padding(end = 4.dp),
        ) {
            FeedList(
                modifier = Modifier,
                feedItems = feedState,
                listState = listState,
                requestMoreItems = requestMoreItems,
                updateReadStatus = { index ->
                    updateReadStatus(index)
                },
            ) { feedItem ->
                FeedItemView(
                    feedItem = feedItem,
                    onFeedItemClick = onFeedItemClick,
                    onFeedItemLongClick = onFeedItemLongClick,
                    feedItemImage = { url ->
                        FeedItemImage(
                            modifier = Modifier
                                .padding(start = Spacing.regular),
                            url = url,
                            width = 96.dp,
                        )
                    },
                )
            }

            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                adapter = rememberScrollbarAdapter(
                    scrollState = listState,
                ),
            )
        }
    }
}

@Composable
private fun ColumnScope.FeedLoader(loadingState: FeedUpdateStatus) {
    AnimatedVisibility(loadingState.isLoading()) {
        val feedRefreshCounter = """
                    ${loadingState.refreshedFeedCount}/${loadingState.totalFeedCount}
        """.trimIndent()
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.regular),
            text = stringResource(
                resource = MR.strings.loading_feed_message,
                feedRefreshCounter,
            ),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedContentToolbar(
    unReadCount: Long,
    showDrawerMenu: Boolean,
    isDrawerOpen: Boolean,
    currentFeedFilter: FeedFilter,
    onDrawerMenuClick: () -> Unit,
) {
    TopAppBar(
        navigationIcon = if (showDrawerMenu) {
            {
                IconButton(
                    onClick = {
                        onDrawerMenuClick()
                    },
                ) {
                    Icon(
                        imageVector = if (isDrawerOpen) {
                            Icons.Default.MenuOpen
                        } else {
                            Icons.Default.Menu
                        },
                        contentDescription = null,
                    )
                }
            }
        } else {
            { }
        },
        title = {
            Row {
                Text(
                    modifier = Modifier
                        .weight(1f, fill = false),
                    text = currentFeedFilter.getTitle(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.width(4.dp))

                Text(text = "($unReadCount)")
            }
        },
    )
}

@Composable
private fun FeedFilter.getTitle(): String =
    when (this) {
        is FeedFilter.Category -> this.feedCategory.title
        is FeedFilter.Source -> this.feedSource.title
        FeedFilter.Timeline -> stringResource(resource = MR.strings.app_name)
    }