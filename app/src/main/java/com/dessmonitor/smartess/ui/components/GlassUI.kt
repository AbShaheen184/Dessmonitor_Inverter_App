package com.dessmonitor.smartess.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild

/**
 * A frosted/liquid glass surface that blurs whatever is rendered *behind* it
 * (a true backdrop blur), enhanced with specular highlight border and subtle inner glow.
 *
 * @param hazeState the shared [HazeState] that links this child to its background source.
 * @param tint translucent color layered on top of the blur for the frosted look.
 * @param blurRadius amount of backdrop blur to apply.
 */
@Composable
fun GlassSurface(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 36.dp,
    tint: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
    blurRadius: Dp = 25.dp,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val isDark = MaterialTheme.colorScheme.surface.let { color ->
        (color.red * 0.299 + color.green * 0.587 + color.blue * 0.114) < 0.5f
    }

    Box(modifier = modifier.clip(shape)) {
        // 1) Real backdrop blur via Haze
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeChild(state = hazeState) {
                    this.backgroundColor = tint
                    this.blurRadius = blurRadius
                }
        )

        // 2) Liquid glass specular highlight border (bright top-left to transparent bottom-right)
        val specularBorderBrush = Brush.linearGradient(
            colors = if (isDark) {
                listOf(
                    Color.White.copy(alpha = 0.45f),
                    Color.White.copy(alpha = 0.15f),
                    Color.White.copy(alpha = 0.05f),
                    Color.White.copy(alpha = 0.20f)
                )
            } else {
                listOf(
                    Color.White.copy(alpha = 0.80f),
                    Color.White.copy(alpha = 0.35f),
                    Color.White.copy(alpha = 0.10f),
                    Color.White.copy(alpha = 0.50f)
                )
            }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (isDark) 0.08f else 0.25f),
                            Color.Transparent,
                            Color.Black.copy(alpha = if (isDark) 0.15f else 0.05f)
                        )
                    )
                )
                .border(
                    width = 1.5.dp,
                    brush = specularBorderBrush,
                    shape = shape
                )
        )

        // 3) Content layer
        content()
    }
}