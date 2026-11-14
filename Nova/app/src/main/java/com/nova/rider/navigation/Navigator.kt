package com.nova.rider.navigation

import androidx.compose.runtime.Stable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.nova.designsystem.components.RiderTab

/** Thin wrapper over the Navigation 3 back stack so screens never touch the list directly. */
@Stable
class Navigator(private val backStack: NavBackStack<NavKey>) {

    /** Push a screen. */
    fun go(route: Route) {
        backStack.add(route)
    }

    /** Pop one screen (system back does the same through NavDisplay's onBack). */
    fun back() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    /** Replace the current screen (e.g. "finding driver" becomes "driver en route"). */
    fun replaceTop(route: Route) {
        if (backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex)
        backStack.add(route)
    }

    /** Clear everything and start fresh at [route] (e.g. after sign-in, or leaving a trip). */
    fun reset(route: Route) {
        backStack.clear()
        backStack.add(route)
    }

    /** Pop back to [route] if it is on the stack, otherwise reset to it. */
    fun popTo(route: Route) {
        val index = backStack.lastIndexOf(route)
        if (index >= 0) {
            while (backStack.lastIndex > index) backStack.removeAt(backStack.lastIndex)
        } else {
            reset(route)
        }
    }

    /** Bottom-navigation tab switch: keep Home as the root, replace the tab above it. */
    fun tab(tab: RiderTab) {
        val target: Route = when (tab) {
            RiderTab.Ride -> Home
            RiderTab.Activity -> Activity
            RiderTab.Wallet -> Wallet
            RiderTab.Account -> Account
        }
        backStack.clear()
        backStack.add(Home)
        if (target != Home) backStack.add(target)
    }
}
