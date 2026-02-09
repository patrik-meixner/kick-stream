package com.kickstream.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kickstream.ui.theme.DarkSurfaceVariant
import com.kickstream.ui.theme.KickGreen
import com.kickstream.ui.theme.OnDarkSurface
import com.kickstream.ui.theme.OnDarkSurfaceVariant

/**
 * TV-friendly search bar with dark background and green cursor.
 */
@Composable
fun SearchBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search streams...",
) {
    BasicTextField(
        value = query,
        onValueChange = onQueryChanged,
        textStyle = TextStyle(
            color = OnDarkSurface,
            fontSize = 16.sp,
        ),
        cursorBrush = SolidColor(KickGreen),
        singleLine = true,
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurfaceVariant, RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = OnDarkSurfaceVariant,
                        )
                    }
                    innerTextField()
                }
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}
