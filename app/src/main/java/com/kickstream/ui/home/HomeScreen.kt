package com.kickstream.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kickstream.R
import com.kickstream.data.repository.FollowedChannel
import com.kickstream.ui.components.KickLoader
import com.kickstream.ui.home.components.ContentRow
import com.kickstream.ui.home.components.FollowedChannelCard
import com.kickstream.ui.home.components.NavigationRail
import com.kickstream.ui.home.components.SearchContent
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    onChannelSelected: (String) -> Unit,
    onLogout: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        uiState.isLoading -> {
            KickLoader()
        }

        uiState.error != null && uiState.followedChannels.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Error: ${uiState.error}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { viewModel.refresh() }) {
                        Text("Retry")
                    }
                }
            }
        }

        else -> {
            MainContent(
                uiState = uiState,
                viewModel = viewModel,
                onChannelSelected = onChannelSelected,
                onLogout = onLogout,
            )
        }
    }
}

@Composable
private fun MainContent(
    uiState: HomeUiState,
    viewModel: HomeViewModel,
    onChannelSelected: (String) -> Unit,
    onLogout: () -> Unit,
) {
    val railFocusRequester = remember { FocusRequester() }
    val followingFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }

    Row(Modifier.fillMaxSize()) {
        // Left navigation rail
        NavigationRail(
            selectedSection = uiState.selectedSection,
            onSectionSelected = { viewModel.selectSection(it) },
            onLogout = { viewModel.logout(onLogoutComplete = onLogout) },
            onFocusContent = {
                val target = when (uiState.selectedSection) {
                    HomeSection.FOLLOWING -> followingFocusRequester
                    HomeSection.SEARCH -> searchFocusRequester
                }
                try {
                    target.requestFocus()
                } catch (_: IllegalStateException) { }
            },
            railFocusRequester = railFocusRequester,
        )

        // Content area — switches based on selected section
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            when (uiState.selectedSection) {
                HomeSection.FOLLOWING -> FollowingContent(
                    followedChannels = uiState.followedChannels,
                    onChannelSelected = onChannelSelected,
                    onRefresh = { viewModel.refresh() },
                    onNavigateToRail = {
                        try {
                            railFocusRequester.requestFocus()
                        } catch (_: IllegalStateException) { }
                    },
                    contentFocusRequester = followingFocusRequester,
                )

                HomeSection.SEARCH -> SearchContent(
                    query = uiState.searchQuery,
                    onQueryChanged = { viewModel.onSearchQueryChanged(it) },
                    results = uiState.searchResults,
                    isLoading = uiState.isSearchLoading,
                    isSearchActive = uiState.isSearchActive,
                    onChannelSelected = onChannelSelected,
                    onClearSearch = { viewModel.clearSearch() },
                    onNavigateToRail = {
                        try {
                            railFocusRequester.requestFocus()
                        } catch (_: IllegalStateException) { }
                    },
                    searchFocusRequester = searchFocusRequester,
                )
            }
        }
    }
}

@Composable
private fun FollowingContent(
    followedChannels: List<FollowedChannel>,
    onChannelSelected: (String) -> Unit,
    onRefresh: () -> Unit,
    onNavigateToRail: () -> Unit,
    contentFocusRequester: FocusRequester,
) {
    val liveChannels = followedChannels.filter { it.isLive }
    val offlineChannels = followedChannels.filterNot { it.isLive }

    if (followedChannels.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                        onNavigateToRail()
                        true
                    } else false
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(R.drawable.ic_kick_k),
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .alpha(0.3f),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "No followed channels",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Use Search to find channels, then star them from the player",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onRefresh,
                    modifier = Modifier.focusRequester(contentFocusRequester),
                ) {
                    Text("Refresh")
                }
            }
        }
    } else {
        // Track visibility for entrance animations
        var showLiveRow by remember { mutableStateOf(false) }
        var showOfflineRow by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            showLiveRow = true
            delay(150)
            showOfflineRow = true
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 32.dp, top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (liveChannels.isNotEmpty()) {
                item(key = "live-row") {
                    AnimatedVisibility(
                        visible = showLiveRow,
                        enter = fadeIn(animationSpec = tween(400)) +
                            slideInVertically(animationSpec = tween(400)) { it / 3 },
                    ) {
                        ContentRow(
                            title = "Live Now",
                            items = liveChannels,
                            itemKey = { it.slug },
                            firstItemFocusRequester = contentFocusRequester,
                        ) { channel, focusReq ->
                            FollowedChannelCard(
                                channel = channel,
                                onClick = { onChannelSelected(channel.slug) },
                                focusRequester = focusReq,
                            )
                        }
                    }
                }
            }

            if (offlineChannels.isNotEmpty()) {
                item(key = "offline-row") {
                    AnimatedVisibility(
                        visible = showOfflineRow,
                        enter = fadeIn(animationSpec = tween(400)) +
                            slideInVertically(animationSpec = tween(400)) { it / 3 },
                    ) {
                        ContentRow(
                            title = "Offline",
                            items = offlineChannels,
                            itemKey = { it.slug },
                            firstItemFocusRequester = if (liveChannels.isEmpty()) contentFocusRequester else null,
                        ) { channel, focusReq ->
                            FollowedChannelCard(
                                channel = channel,
                                onClick = { onChannelSelected(channel.slug) },
                                focusRequester = focusReq,
                            )
                        }
                    }
                }
            }

            item(key = "bottom-spacer") {
                Spacer(Modifier.height(32.dp))
            }
        }

        // Don't auto-focus content here — focus stays on the rail after
        // section selection. The user presses Right to enter content.
        // This prevents the Enter key from the rail leaking into the Card.
    }
}
