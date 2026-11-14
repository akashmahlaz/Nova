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

enum class RiderTab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Ride("Ride", NovaIcons.Car, NovaIcons.RideFilled),
    Activity("Activity", NovaIcons.Receipt, NovaIcons.ReceiptFilled),
    Wallet("Wallet", NovaIcons.Wallet, NovaIcons.WalletFilled),
    Account("Account", NovaIcons.User, NovaIcons.UserFilled),
}

/** M3 Expressive short navigation bar (pill indicator around the icon). */
@Composable
fun RiderNavigationBar(selected: RiderTab, onSelect: (RiderTab) -> Unit, modifier: Modifier = Modifier) {
    ShortNavigationBar(modifier = modifier, containerColor = NovaTheme.colors.navBar) {
        RiderTab.entries.forEach { tab ->
            val isSelected = tab == selected
            ShortNavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(tab) },
                icon = { Icon(if (isSelected) tab.selectedIcon else tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
                colors = ShortNavigationBarItemDefaults.colors(
                    selectedIndicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColorTopIconPosition = MaterialTheme.colorScheme.onSurface,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
