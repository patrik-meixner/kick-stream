package com.kickstream.ui.home

import android.app.Activity
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kickstream.R
import com.kickstream.data.repository.FollowedChannel
import com.kickstream.ui.components.KickLoader
import com.kickstream.ui.home.components.FollowedChannelCard
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
            val context = LocalContext.current
            // BackHandler covers real D-pad Back presses when search is active.
            // When search is NOT active, we don't enable it — the default
            // activity back behavior (finish) takes over.
            BackHandler(
                enabled = uiState.searchQuery.isNotEmpty() || uiState.searchResults != null,
            ) {
                viewModel.clearSearch()
            }
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }

            val isSearching = uiState.searchResults != null

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 260.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 48.dp, top = 24.dp, end = 48.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Header: KICK logo + Logout button
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
                        IconButton(
                            onClick = { viewModel.logout(onLogoutComplete = onLogout) },
                            enabled = !uiState.isLoggingOut,
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_logout),
                                contentDescription = "Log out",
                            )
                        }
                    }
                }

                // Search bar (full-width) — search by channel slug
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SearchBar(
                        query = uiState.searchQuery,
                        onQueryChanged = { viewModel.onSearchQueryChanged(it) },
                        onBackPressed = {
                            if (uiState.searchQuery.isNotEmpty() || uiState.searchResults != null) {
                                viewModel.clearSearch()
                            } else {
                                (context as? Activity)?.finish()
                            }
                        },
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .focusRequester(focusRequester),
                    )
                }

                if (isSearching) {
                    // ── Search results ──────────────────────────────
                    val results = uiState.searchResults!!
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = if (results.isEmpty()) "No channels found for \"${uiState.searchQuery}\""
                            else "Search results",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }

                    items(results, key = { it.slug }) { channel ->
                        FollowedChannelCard(
                            channel = channel,
                            onClick = { onChannelSelected(channel.slug) },
                        )
                    }
                } else {
                    // ── Followed Channels (default view) ────────────
                    if (uiState.followedChannels.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = "Followed Channels",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }

                        items(
                            uiState.followedChannels,
                            key = { it.slug },
                        ) { channel ->
                            FollowedChannelCard(
                                channel = channel,
                                onClick = { onChannelSelected(channel.slug) },
                            )
                        }
                    } else {
                        // No followed channels yet — empty state
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 64.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = "No followed channels yet",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "Search for a channel above, then follow it from the player",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(24.dp))
                                Button(onClick = { viewModel.refresh() }) {
                                    Text("Refresh")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
