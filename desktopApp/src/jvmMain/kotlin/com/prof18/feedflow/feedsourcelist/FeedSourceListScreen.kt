package com.prof18.feedflow.feedsourcelist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.onClick
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.prof18.feedflow.MR
import com.prof18.feedflow.addfeed.AddFeedScreen
import com.prof18.feedflow.koin
import com.prof18.feedflow.presentation.FeedSourceListViewModel
import com.prof18.feedflow.ui.style.FeedFlowTheme
import com.prof18.feedflow.ui.style.Spacing
import dev.icerock.moko.resources.compose.stringResource

val viewModel = koin.get<FeedSourceListViewModel>()

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FeedSourceListScreen(
    navigateBack: () -> Unit,
) {

    FeedFlowTheme {

        var dialogState by remember { mutableStateOf(false) }

        Dialog(visible = dialogState, onCloseRequest = { dialogState = false }) {
            AddFeedScreen(
                onFeedAdded = {
                    dialogState = false
                }
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(resource = MR.strings.feeds_title)
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                navigateBack()
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = null,
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
//                            onAddFeedClick()
                                dialogState = true
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->

            val feeds by viewModel.feedSourcesState.collectAsState()

            if (feeds.isEmpty()) {
                Box(
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize()
                ) {
                    Text(
                        modifier = Modifier
                            .padding(Spacing.regular),
                        text = stringResource(resource = MR.strings.no_feeds_add_one_message),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .padding(paddingValues),
                    contentPadding = PaddingValues(Spacing.regular),
                ) {
                    items(
                        items = feeds,
                    ) { feedSource ->

                        var showFeedMenu by remember {
                            mutableStateOf(
                                false,
                            )
                        }

                        val interactionSource = remember { MutableInteractionSource() }

                        Column(
                            modifier = Modifier
                                .onClick(
                                    enabled = true,
                                    interactionSource = interactionSource,
                                    matcher = PointerMatcher.mouse(PointerButton.Secondary), // Right Mouse Button
                                    onClick = {
                                        showFeedMenu = true
                                    }
                                )
                        ) {
                            Text(
                                modifier = Modifier
                                    .padding(top = Spacing.small),
                                text = feedSource.title,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                modifier = Modifier
                                    .padding(top = Spacing.xsmall)
                                    .padding(bottom = Spacing.small),
                                text = feedSource.url,
                                style = MaterialTheme.typography.labelLarge
                            )

                            DropdownMenu(
                                expanded = showFeedMenu,
                                onDismissRequest = { showFeedMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(resource = MR.strings.delete_feed)
                                        )
                                    },
                                    onClick = {
                                        viewModel.deleteFeedSource(feedSource)
                                        showFeedMenu = false
                                    }
                                )
                            }

                            Divider(
                                modifier = Modifier,
                                thickness = 0.2.dp,
                                color = Color.Gray,
                            )
                        }
                    }
                }
            }
        }
    }
}
