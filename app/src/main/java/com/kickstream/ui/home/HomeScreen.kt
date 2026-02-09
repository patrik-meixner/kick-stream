package com.kickstream.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kickstream.R
import com.kickstream.ui.home.components.FollowedChannelCard
import com.kickstream.ui.home.components.LiveChannelCard
import com.kickstream.ui.home.components.SearchBar

@Composable
fun HomeScreen(
    onChannelSelected: (String) -> Unit,
    onLogout: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        uiState.isLoading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Loading...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        uiState.error != null && uiState.livestreams.isEmpty() && uiState.followedChannels.isEmpty() -> {
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
            val isSearching = uiState.searchResults != null
            val displayStreams = uiState.searchResults ?: uiState.livestreams

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 260.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 48.dp, top = 24.dp, end = 48.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 0. Header: KICK logo + Logout button
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.kick_wordmark),
                            contentDescription = "Kick",
                            modifier = Modifier.height(36.dp),
                            contentScale = ContentScale.Fit,
                        )
                        Button(
                            onClick = { viewModel.logout(onLogoutComplete = onLogout) },
                            enabled = !uiState.isLoggingOut,
                        ) {
                            Text(if (uiState.isLoggingOut) "Logging out..." else "Log out")
                        }
                    }
                }

                // 1. Search bar (full-width)
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SearchBar(
                        query = uiState.searchQuery,
                        onQueryChanged = { viewModel.onSearchQueryChanged(it) },
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }

                // When searching, only show search results
                if (isSearching) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = if (displayStreams.isEmpty()) "No results for \"${uiState.searchQuery}\""
                            else "Results for \"${uiState.searchQuery}\"",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }

                    items(displayStreams, key = { it.broadcasterUserId }) { stream ->
                        LiveChannelCard(
                            stream = stream,
                            onClick = { onChannelSelected(stream.slug) },
                        )
                    }
                } else {
                    // 2. Followed Channels section (if any)
                    if (uiState.followedChannels.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = "Followed Channels",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(bottom = 8.dp),
                            ) {
                                items(
                                    uiState.followedChannels,
                                    key = { it.slug },
                                ) { channel ->
                                    FollowedChannelCard(
                                        channel = channel,
                                        onClick = { onChannelSelected(channel.slug) },
                                    )
                                }
                            }
                        }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    // 3. "Live on Kick" section header
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "Live on Kick",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }

                    // 4. Grid of live stream cards
                    if (uiState.livestreams.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = "No live streams right now",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(16.dp))
                                Button(onClick = { viewModel.refresh() }) {
                                    Text("Refresh")
                                }
                            }
                        }
                    } else {
                        items(uiState.livestreams, key = { it.broadcasterUserId }) { stream ->
                            LiveChannelCard(
                                stream = stream,
                                onClick = { onChannelSelected(stream.slug) },
                            )
                        }
                    }
                }
            }
        }
    }
}
