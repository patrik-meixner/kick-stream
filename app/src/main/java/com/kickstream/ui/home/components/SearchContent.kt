package com.kickstream.ui.home.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kickstream.R
import com.kickstream.data.repository.FollowedChannel

@Composable
fun SearchContent(
    query: String,
    onQueryChanged: (String) -> Unit,
    results: List<FollowedChannel>?,
    isLoading: Boolean,
    isSearchActive: Boolean,
    onChannelSelected: (String) -> Unit,
    onClearSearch: () -> Unit,
    onNavigateToRail: () -> Unit,
    modifier: Modifier = Modifier,
    searchFocusRequester: FocusRequester = remember { FocusRequester() },
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    // Track whether the keyboard was recently dismissed by a back press.
    // First Back hides the keyboard; second Back exits the search section.
    var keyboardDismissedByBack by remember { mutableStateOf(false) }

    // Auto-focus the search bar when entering the Search section
    LaunchedEffect(Unit) {
        try {
            searchFocusRequester.requestFocus()
        } catch (_: IllegalStateException) { }
    }

    // Back from search: first press hides keyboard, second press exits search
    BackHandler(enabled = isSearchActive) {
        if (!keyboardDismissedByBack) {
            keyboardController?.hide()
            keyboardDismissedByBack = true
        } else {
            onClearSearch()
        }
    }

    // Reset the flag when the user starts typing again (keyboard reopens)
    LaunchedEffect(query) {
        keyboardDismissedByBack = false
    }

    val isSearching = results != null || isLoading

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 240.dp),
        modifier = modifier
            .fillMaxSize()
            .padding(start = 32.dp, top = 24.dp, end = 48.dp),
        contentPadding = PaddingValues(bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Search bar (full-width)
        // onBackPressed is called for EVERY Back/Escape key, including
        // IME-generated KEYCODE_BACK (backspace on empty field). We must
        // NOT call onClearSearch() here — that switches sections and
        // destroys the text field. Only navigate away when truly empty.
        item(span = { GridItemSpan(maxLineSpan) }) {
            SearchBar(
                query = query,
                onQueryChanged = onQueryChanged,
                onBackPressed = {
                    // IME sends KEYCODE_BACK on backspace-on-empty — just consume it.
                    // Real "exit search" is handled by the BackHandler above.
                },
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .focusRequester(searchFocusRequester),
            )
        }

        if (isLoading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    SearchLoader()
                }
            }
        } else if (results != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = if (results.isEmpty()) "No channels found for \"$query\""
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
            // No search yet — show hint with Kick logo
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 64.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            painter = painterResource(R.drawable.ic_kick_k),
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .alpha(0.3f),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Type to search for channels",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Small inline pulsing Kick K logo — used while search results are loading. */
@Composable
private fun SearchLoader() {
    val transition = rememberInfiniteTransition(label = "search-pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "search-pulse-alpha",
    )
    Image(
        painter = painterResource(id = R.drawable.ic_kick_k),
        contentDescription = "Searching",
        modifier = Modifier
            .size(48.dp)
            .alpha(alpha),
    )
}
