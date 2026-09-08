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
    tint: Color? = null,
    blurRadius: Dp = 25.dp,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val isDark = MaterialTheme.colorScheme.surface.let { color ->
        (color.red * 0.299 + color.green * 0.587 + color.blue * 0.114) < 0.5f
    }
    
    // Adaptive tint: more opaque for better visibility
    val surfaceTint = tint ?: if (isDark) {
        Color.Black.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
    }

    Box(modifier = modifier.clip(shape)) {
        // 1) Real backdrop blur via Haze
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeChild(state = hazeState) {
                    this.backgroundColor = surfaceTint
                    this.blurRadius = blurRadius
                }
        )

        // 2) accent-colored border
        val accentBorderBrush = Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (isDark) 0.02f else 0.08f),
                            Color.Transparent,
                            Color.White.copy(alpha = if (isDark) 0.01f else 0.03f)
                        )
                    )
                )
                .border(
                    width = 1.2.dp, 
                    brush = accentBorderBrush,
                    shape = shape
                )
        )

        // 3) Content layer
        content()
    }
}