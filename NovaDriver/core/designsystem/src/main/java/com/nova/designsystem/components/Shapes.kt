package com.nova.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.graphics.shapes.RoundedPolygon

/** The expressive shapes Nova uses, backed by Material's official [MaterialShapes]. */
enum class NovaShape {
    /** Avatars, selected states, badges. */
    Cookie,
    /** The Nova brand mark, loading, celebration. */
    Burst,
    /** Illustration backdrops, SOS. */
    Sunny,
    Cookie4,
    Pill;

    val polygon: RoundedPolygon
        get() = when (this) {
            Cookie -> MaterialShapes.Cookie9Sided
            Burst -> MaterialShapes.SoftBurst
            Sunny -> MaterialShapes.Sunny
            Cookie4 -> MaterialShapes.Cookie4Sided
            Pill -> MaterialShapes.Pill
        }
}

@Composable
fun NovaShape.toShape(): Shape = polygon.toShape()

/** A solid expressive shape with centred content (avatar, icon badge, brand mark). */
@Composable
fun ShapeBadge(
    shape: NovaShape,
    size: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape.toShape())
            .background(color),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
