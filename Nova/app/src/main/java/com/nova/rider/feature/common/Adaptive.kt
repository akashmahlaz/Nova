package com.nova.rider.feature.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

/**
 * Material 3 window width size classes (m3.material.io > Foundations > Layout > Window size classes):
 *   Compact  < 600 dp : phones in portrait. Navigation bar at the bottom, single pane.
 *   Medium   600-839  : foldables open, small tablets, phones in landscape. Navigation rail.
 *   Expanded >= 840 dp: tablets, desktop windows. Navigation rail, content kept to a readable width.
 *
 * Computed from the window this app is drawn in (not the physical screen), so split-screen and
 * freeform windows get the right layout too. Recomposes on resize, fold and rotation.
 */
enum class WidthClass { Compact, Medium, Expanded }

@Composable
@ReadOnlyComposable
fun currentWidthClass(): WidthClass {
    val widthDp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    return when {
        widthDp < 600.dp -> WidthClass.Compact
        widthDp < 840.dp -> WidthClass.Medium
        else -> WidthClass.Expanded
    }
}

/**
 * Longest comfortable line for lists, forms and settings. Material's guidance is to keep reading
 * content to a column rather than stretch it edge to edge on large windows; 640 dp holds a
 * full list row with its trailing action without lines getting hard to follow.
 */
val ReadableContentWidth = 640.dp
