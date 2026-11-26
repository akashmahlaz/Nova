package com.nova.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationItemIconPosition
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarArrangement
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.nova.designsystem.icons.NovaIcons
import com.nova.designsystem.theme.NovaTheme

enum class DriverTab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Drive("Drive", NovaIcons.Car, NovaIcons.RideFilled),
    Earnings("Earnings", NovaIcons.TrendUp, NovaIcons.TrendUp),
    Wallet("Wallet", NovaIcons.Wallet, NovaIcons.WalletFilled),
    Profile("Profile", NovaIcons.User, NovaIcons.UserFilled),
}

/**
 * M3 Expressive short navigation bar (pill indicator around the icon).
 * [wide] = medium window width: items centred with the label beside the icon, as the spec shows
 * for 600–840dp. Compact (phones) keeps the icon-over-label layout.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DriverNavigationBar(selected: DriverTab, onSelect: (DriverTab) -> Unit, modifier: Modifier = Modifier, wide: Boolean = false) {
    ShortNavigationBar(
        modifier = modifier, containerColor = NovaTheme.colors.navBar,
        arrangement = if (wide) ShortNavigationBarArrangement.Centered else ShortNavigationBarArrangement.EqualWeight,
    ) {
        DriverTab.entries.forEach { tab ->
            val isSelected = tab == selected
            ShortNavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(tab) },
                icon = { Icon(if (isSelected) tab.selectedIcon else tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
                iconPosition = if (wide) NavigationItemIconPosition.Start else NavigationItemIconPosition.Top,
                colors = ShortNavigationBarItemDefaults.colors(
                    selectedIndicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColorTopIconPosition = MaterialTheme.colorScheme.onSurface,
                    selectedTextColorStartIconPosition = MaterialTheme.colorScheme.onSurface,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

/** Expanded windows (≥ 840dp): the same tabs as a collapsed M3 Expressive wide navigation rail. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DriverNavigationRail(selected: DriverTab, onSelect: (DriverTab) -> Unit, modifier: Modifier = Modifier) {
    WideNavigationRail(
        modifier = modifier,
        colors = WideNavigationRailDefaults.colors(containerColor = NovaTheme.colors.navBar),
        arrangement = Arrangement.Center,
    ) {
        DriverTab.entries.forEach { tab ->
            val isSelected = tab == selected
            WideNavigationRailItem(
                selected = isSelected,
                onClick = { onSelect(tab) },
                icon = { Icon(if (isSelected) tab.selectedIcon else tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
                railExpanded = false,
                colors = WideNavigationRailItemDefaults.colors(
                    selectedIndicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColorTopIconPosition = MaterialTheme.colorScheme.onSurface,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
