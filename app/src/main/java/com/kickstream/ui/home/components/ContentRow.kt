package com.kickstream.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
fun <T> ContentRow(
    title: String,
    items: List<T>,
    itemKey: (T) -> String,
    modifier: Modifier = Modifier,
    firstItemFocusRequester: FocusRequester? = null,
    itemContent: @Composable (item: T, focusRequester: FocusRequester?) -> Unit,
) {
    Column(modifier = modifier) {
        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            // Start padding gives room for the 1.05x focus scale animation
            // on the first card so it doesn't clip against the left edge.
            contentPadding = PaddingValues(start = 8.dp, end = 48.dp),
        ) {
            itemsIndexed(
                items = items,
                key = { _, item -> itemKey(item) },
            ) { index, item ->
                itemContent(
                    item,
                    if (index == 0) firstItemFocusRequester else null,
                )
            }
        }
    }
}
