package com.dessmonitor.smartess.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState

@Composable
fun FloatingNavigationBar(
    items: List<NavigationItem>,
    currentRoute: String?,
    onItemClick: (String) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    val selectedIndex = remember(currentRoute) {
        items.indexOfFirst { 
            it.route == currentRoute || (currentRoute != null && currentRoute.startsWith("${it.route}?"))
        }.coerceAtLeast(0)
    }

    val itemWidths = remember { mutableStateMapOf<Int, Int>() }
    val density = LocalDensity.current

    val indicatorOffset by animateDpAsState(
        targetValue = if (itemWidths.containsKey(selectedIndex)) {
            val totalOffset = (0 until selectedIndex).sumOf { itemWidths[it] ?: 0 }
            with(density) { totalOffset.toDp() }
        } else 0.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "indicatorOffset"
    )

    val currentItemWidth by animateDpAsState(
        targetValue = if (itemWidths.containsKey(selectedIndex)) {
            with(density) { (itemWidths[selectedIndex] ?: 0).toDp() }
        } else 0.dp,
        label = "itemWidth"
    )

    Box(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 20.dp)
            .fillMaxWidth()
            .height(72.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassSurface(
            hazeState = hazeState,
            cornerRadius = 36.dp,
            blurRadius = 30.dp,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                // Animated Liquid Selection Pill Indicator
                if (currentItemWidth > 0.dp) {
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(with(density) { indicatorOffset.toPx() }.toInt(), 0) }
                            .width(currentItemWidth)
                            .fillMaxHeight()
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                    )
                                )
                            )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEachIndexed { index, item ->
                        val isSelected = index == selectedIndex
                        val contentColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            label = "contentColor"
                        )
                        
                        val itemScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.15f else 1.0f,
                            animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
                            label = "itemScale"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .onGloballyPositioned { coords ->
                                    itemWidths[index] = coords.size.width
                                }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onItemClick(item.route) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.scale(itemScale)
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.title,
                                    tint = contentColor,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.title,
                                    color = contentColor,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

data class NavigationItem(
    val route: String,
    val title: String,
    val icon: ImageVector
)
