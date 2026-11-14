package com.nova.designsystem.components

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.ShortNavigationBarItemDefaults
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

/** M3 Expressive short navigation bar (pill indicator around the icon). */
@Composable
fun DriverNavigationBar(selected: DriverTab, onSelect: (DriverTab) -> Unit, modifier: Modifier = Modifier) {
    ShortNavigationBar(modifier = modifier, containerColor = NovaTheme.colors.navBar) {
        DriverTab.entries.forEach { tab ->
            val isSelected = tab == selected
            ShortNavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(tab) },
                icon = { Icon(if (isSelected) tab.selectedIcon else tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
                colors = ShortNavigationBarItemDefaults.colors(
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
