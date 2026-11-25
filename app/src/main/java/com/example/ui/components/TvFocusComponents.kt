package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.example.ui.theme.Accent
import com.example.ui.theme.Background
import com.example.ui.theme.LiveBadgeBg
import com.example.ui.theme.LiveRed
import com.example.ui.theme.SurfaceFocus

fun Modifier.tvFocusClickable(
    shape: Shape = RoundedCornerShape(8.dp),
    defaultBackground: Color = Color.Transparent,
    focusedBackground: Color = SurfaceFocus,
    defaultBorder: Color = Color.Transparent,
    focusedBorder: Color = Accent,
    focusScale: Float = 1.0f,
    onFocusChange: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    val inputModeManager = LocalInputModeManager.current
    val showFocusVisual = isFocused && inputModeManager.inputMode != InputMode.Touch

    val scale by animateFloatAsState(
        targetValue = if (showFocusVisual) focusScale else 1f,
        animationSpec = spring(stiffness = 500f),
        label = "tv_focus_scale"
    )
    val bgColor by animateColorAsState(
        targetValue = if (showFocusVisual) focusedBackground else defaultBackground,
        animationSpec = tween(150),
        label = "tv_focus_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (showFocusVisual) focusedBorder else defaultBorder,
        animationSpec = tween(150),
        label = "tv_focus_border"
    )
    val interactionSource = remember { MutableInteractionSource() }

    this
        .scale(scale)
        .clip(shape)
        .background(bgColor, shape)
        .then(
            if (showFocusVisual || defaultBorder != Color.Transparent) {
                Modifier.border(
                    width = if (showFocusVisual) 2.dp else 1.dp,
                    color = borderColor,
                    shape = shape
                )
            } else {
                Modifier
            }
        )
        .onFocusChanged { state ->
            isFocused = state.isFocused
            onFocusChange?.invoke(state.isFocused)
        }
        .focusable(interactionSource = interactionSource)
        .clickable(
            interactionSource = interactionSource,
            indication = ripple(color = Accent),
            onClick = onClick
        )
}

@Composable
fun ChannelLogoBox(
    logoUrl: String?,
    channelName: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(Background),
        contentAlignment = Alignment.Center
    ) {
        if (!logoUrl.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = logoUrl,
                contentDescription = channelName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(size)
                    .padding(4.dp),
                error = {
                    ChannelLogoFallback(channelName = channelName)
                },
                loading = {
                    ChannelLogoFallback(channelName = channelName)
                }
            )
        } else {
            ChannelLogoFallback(channelName = channelName)
        }
    }
}

@Composable
private fun ChannelLogoFallback(channelName: String) {
    val initial = remember(channelName) {
        channelName.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: ""
    }
    if (initial.isNotEmpty()) {
        Text(
            text = initial,
            style = MaterialTheme.typography.titleMedium,
            color = Accent,
            fontWeight = FontWeight.SemiBold
        )
    } else {
        Icon(
            imageVector = Icons.Default.Tv,
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun LiveBadge(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(LiveBadgeBg)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "● AO VIVO",
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = LiveRed
        )
    }
}
