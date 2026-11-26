package com.nova.designsystem.adaptive

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material 3 window size classes (m3.material.io › Foundations › Layout › Window size classes):
 * width compact < 600dp ≤ medium < 840dp ≤ expanded; height compact < 480dp (e.g. phone landscape).
 * Phones in portrait are always Compact, so their layouts never change.
 */
enum class WidthClass { Compact, Medium, Expanded }

@Composable
fun widthClass(): WidthClass {
    val width = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    return when {
        width >= 840.dp -> WidthClass.Expanded
        width >= 600.dp -> WidthClass.Medium
        else -> WidthClass.Compact
    }
}

@Composable
fun isHeightCompact(): Boolean =
    with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() } < 480.dp

/** Readable line length on tablets / unfolded / landscape: content stops growing at this width. */
val MaxContentWidth: Dp = 640.dp

/** Fill the width up to [MaxContentWidth]; centre it with the parent's alignment. */
fun Modifier.contentWidth(max: Dp = MaxContentWidth): Modifier = widthIn(max = max).fillMaxWidth()
