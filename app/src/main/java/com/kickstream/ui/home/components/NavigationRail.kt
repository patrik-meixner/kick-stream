package com.kickstream.ui.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kickstream.R
import com.kickstream.ui.home.HomeSection
import com.kickstream.ui.theme.DarkSurface
import com.kickstream.ui.theme.KickGreen
import com.kickstream.ui.theme.OnDarkSurface
import com.kickstream.ui.theme.OnDarkSurfaceVariant

@Composable
fun NavigationRail(
    selectedSection: HomeSection,
    onSectionSelected: (HomeSection) -> Unit,
    onLogout: () -> Unit,
    onFocusContent: () -> Unit,
    modifier: Modifier = Modifier,
    railFocusRequester: FocusRequester = remember { FocusRequester() },
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(IntrinsicSize.Min)
            .background(DarkSurface)
            .padding(horizontal = 10.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        // Logo
        Image(
            painter = painterResource(id = R.drawable.ic_kick_k),
            contentDescription = "Kick",
            modifier = Modifier.size(32.dp),
            contentScale = ContentScale.Fit,
        )

        Spacer(Modifier.height(32.dp))

        // Navigation items
        HomeSection.entries.forEachIndexed { index, section ->
            val isSelected = section == selectedSection
            val focusRequester = remember { FocusRequester() }

            // Attach railFocusRequester to the currently selected item
            // so content area can focus back to it
            val effectiveFocusRequester = if (isSelected) railFocusRequester else focusRequester

            RailItem(
                icon = when (section) {
                    HomeSection.FOLLOWING -> R.drawable.ic_following
                    HomeSection.SEARCH -> R.drawable.ic_search
                },
                label = when (section) {
                    HomeSection.FOLLOWING -> "Following"
                    HomeSection.SEARCH -> "Search"
                },
                isSelected = isSelected,
                onClick = { onSectionSelected(section) },
                onFocusContent = onFocusContent,
                focusRequester = effectiveFocusRequester,
            )

            if (index < HomeSection.entries.size - 1) {
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.weight(1f))

        // Logout button
        RailItem(
            icon = R.drawable.ic_logout,
            label = "Logout",
            isSelected = false,
            onClick = onLogout,
            onFocusContent = onFocusContent,
            focusRequester = remember { FocusRequester() },
        )
    }
}

@Composable
private fun RailItem(
    icon: Int,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onFocusContent: () -> Unit,
    focusRequester: FocusRequester,
) {
    val isFocused = remember { androidx.compose.runtime.mutableStateOf(false) }

    // Four visual states: selected+focused, selected, focused, idle.
    // When the already-selected item is focused, bump the background brightness
    // and add a border so the user can clearly see the D-pad cursor position.
    val bgColor = when {
        isSelected && isFocused.value -> KickGreen.copy(alpha = 0.25f)
        isSelected -> KickGreen.copy(alpha = 0.15f)
        isFocused.value -> KickGreen.copy(alpha = 0.08f)
        else -> DarkSurface
    }
    val iconTint = when {
        isSelected -> KickGreen
        isFocused.value -> OnDarkSurface
        else -> OnDarkSurfaceVariant
    }
    val textColor = when {
        isSelected -> KickGreen
        isFocused.value -> OnDarkSurface
        else -> OnDarkSurfaceVariant
    }
    val shape = RoundedCornerShape(12.dp)
    val borderModifier = if (isFocused.value) {
        Modifier.border(1.5.dp, KickGreen.copy(alpha = 0.6f), shape)
    } else {
        Modifier
    }

    Column(
        modifier = Modifier
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused.value = it.isFocused }
            .onPreviewKeyEvent { event ->
                when (event.key) {
                    Key.DirectionRight -> {
                        if (event.type == KeyEventType.KeyDown) onFocusContent()
                        true // consume both KeyDown and KeyUp to prevent leak
                    }
                    Key.Enter, Key.DirectionCenter -> {
                        if (event.type == KeyEventType.KeyDown) onClick()
                        true // consume KeyUp too — prevents the Card from firing onClick
                    }
                    else -> false
                }
            }
            .focusable()
            .clip(shape)
            .then(borderModifier)
            .fillMaxWidth()
            .background(bgColor)
            .padding(vertical = 8.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = iconTint,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            maxLines = 1,
        )
    }
}
