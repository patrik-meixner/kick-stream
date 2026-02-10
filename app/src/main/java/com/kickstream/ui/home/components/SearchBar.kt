package com.kickstream.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kickstream.ui.theme.DarkSurfaceVariant
import com.kickstream.ui.theme.KickGreen
import com.kickstream.ui.theme.OnDarkSurface
import com.kickstream.ui.theme.OnDarkSurfaceVariant

/**
 * TV-friendly search bar that allows D-pad navigation out of the text field.
 * Up/Down arrow keys move focus to adjacent items instead of being consumed.
 *
 * Back/Escape key handling: On Android TV, when a soft keyboard backspace hits
 * an empty text field, the IME's BaseInputConnection converts it to KEYCODE_BACK.
 * We MUST consume this here to prevent the activity from finishing. The actual
 * back-to-exit logic lives in HomeScreen's BackHandler (which only fires for
 * real D-pad Back presses, not IME-generated ones intercepted here).
 */
@Composable
fun SearchBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search streams...",
) {
    val focusManager = LocalFocusManager.current

    BasicTextField(
        value = query,
        onValueChange = onQueryChanged,
        textStyle = TextStyle(
            color = OnDarkSurface,
            fontSize = 16.sp,
        ),
        cursorBrush = SolidColor(KickGreen),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = { focusManager.moveFocus(FocusDirection.Down) },
        ),
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
        modifier = modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionDown -> {
                            focusManager.moveFocus(FocusDirection.Down)
                            true
                        }
                        Key.DirectionUp -> {
                            focusManager.moveFocus(FocusDirection.Up)
                            true
                        }
                        // Consume Back key to prevent IME-generated back from
                        // reaching the activity's OnBackPressedDispatcher.
                        // Delegates actual back logic to the caller.
                        Key.Back, Key.Escape -> {
                            onBackPressed()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            },
    )
}
