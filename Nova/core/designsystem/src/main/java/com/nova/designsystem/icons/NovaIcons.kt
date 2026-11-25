package com.nova.designsystem.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Nova icon set. Every icon is generated from the exact SVG paths used on the Nova design
 * canvas (24x24 grid, 2.2 stroke, round caps/joins), so the app matches the design 1:1.
 * Tint them with [androidx.compose.material3.Icon]'s `tint`.
 */
object NovaIcons {
    val Access: ImageVector by lazy { strokeIcon("Access", 2.2f, "M10.2 4.5a1.8 1.8 0 1 0 3.6 0a1.8 1.8 0 1 0 -3.6 0", "M5 8h14M12 8v6M9 21l3-7 3 7") }
    val Alert: ImageVector by lazy { strokeIcon("Alert", 2.2f, "M12 7v6M12 17v.5") }
    val ArrowRight: ImageVector by lazy { strokeIcon("ArrowRight", 2.2f, "M5 12h14m-6-6 6 6-6 6") }
    val Back: ImageVector by lazy { strokeIcon("Back", 2.2f, "M19 12H5m6-6-6 6 6 6") }
    val Bank: ImageVector by lazy { strokeIcon("Bank", 2.2f, "M3 10 12 4l9 6M5 10v8M19 10v8M9 10v8M15 10v8M3 20h18") }
    val Battery: ImageVector by lazy { strokeIcon("Battery", 2.2f, "M5 7h12a2 2 0 0 1 2 2v6a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2v-6a2 2 0 0 1 2 -2z", "M21 11v2") }
    val Bell: ImageVector by lazy { strokeIcon("Bell", 2.2f, "M6 16V11a6 6 0 0 1 12 0v5l2 2H4z", "M10 21h4") }
    val Calendar: ImageVector by lazy { strokeIcon("Calendar", 2.2f, "M6 5h12a3 3 0 0 1 3 3v10a3 3 0 0 1 -3 3h-12a3 3 0 0 1 -3 -3v-10a3 3 0 0 1 3 -3z", "M3 10h18M8 3v4M16 3v4") }
    val Camera: ImageVector by lazy { strokeIcon("Camera", 2.2f, "M4 8h3l2-3h6l2 3h3v11H4z", "M8.5 13a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0 -7 0") }
    val Car: ImageVector by lazy { strokeIcon("Car", 2.2f, "M5 16V11l2-5h10l2 5v5M3 16h18v3H3z") }
    val Card: ImageVector by lazy { strokeIcon("Card", 2.2f, "M5 5h14a3 3 0 0 1 3 3v8a3 3 0 0 1 -3 3h-14a3 3 0 0 1 -3 -3v-8a3 3 0 0 1 3 -3z", "M2 10h20") }
    val Check: ImageVector by lazy { strokeIcon("Check", 2.2f, "m5 12 5 5 9-10") }
    val Chev: ImageVector by lazy { strokeIcon("Chev", 2.2f, "m9 6 6 6-6 6") }
    val Clock: ImageVector by lazy { strokeIcon("Clock", 2.2f, "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0", "M12 7v5l3 2") }
    val Close: ImageVector by lazy { strokeIcon("Close", 2.2f, "M6 6l12 12M18 6 6 18") }
    val Copy: ImageVector by lazy { strokeIcon("Copy", 2.2f, "M10 8h8a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-8a2 2 0 0 1 -2 -2v-8a2 2 0 0 1 2 -2z", "M16 8V5a1 1 0 0 0-1-1H5a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h3") }
    val Doc: ImageVector by lazy { strokeIcon("Doc", 2.2f, "M6 3h9l4 4v14H6z", "M15 3v4h4") }
    val Down: ImageVector by lazy { strokeIcon("Down", 2.2f, "m6 9 6 6 6-6") }
    val Download: ImageVector by lazy { strokeIcon("Download", 2.2f, "M12 3v12m-5-5 5 5 5-5M5 21h14") }
    val Drag: ImageVector by lazy { strokeIcon("Drag", 3f, "M9 6h.01M9 12h.01M9 18h.01M15 6h.01M15 12h.01M15 18h.01") }
    val Edit: ImageVector by lazy { strokeIcon("Edit", 2.2f, "M4 20h4L19 9l-4-4L4 16z") }
    val Finger: ImageVector by lazy {
        strokeIcon(
            "Finger",
            2.0f,
            "M12 10a2 2 0 0 0-2 2c0 1.02-.1 2.51-.26 4",
            "M14 13.12c0 2.38 0 6.38-1 8.88",
            "M17.29 21.02c.12-.6.43-2.3.5-3.02",
            "M2 12a10 10 0 0 1 18-6",
            "M2 16h.01",
            "M21.8 16c.2-2 .131-5.354 0-6",
            "M5 19.5C5.5 18 6 15 6 12a6 6 0 0 1 .34-2",
            "M8.65 22c.21-.66.45-1.32.57-2",
            "M9 6.8a6 6 0 0 1 9 5.2v2",
        )
    }
    val Flag: ImageVector by lazy { strokeIcon("Flag", 2.2f, "M5 21V4h11l-2 4 2 4H5") }
    val Flash: ImageVector by lazy { strokeIcon("Flash", 2.2f, "M13 2 4 14h7l-1 8 9-12h-7z") }
    val Gift: ImageVector by lazy { strokeIcon("Gift", 2.2f, "M4 8h16a1 1 0 0 1 1 1v3a1 1 0 0 1 -1 1h-16a1 1 0 0 1 -1 -1v-3a1 1 0 0 1 1 -1z", "M5 13v8h14v-8M12 8v13M12 8c-2-4-5-4-5-2s3 2 5 2c2 0 5 0 5-2s-3-2-5 2") }
    val Globe: ImageVector by lazy { strokeIcon("Globe", 2.2f, "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0", "M3 12h18M12 3c3 3 3 15 0 18M12 3c-3 3-3 15 0 18") }
    val Grid: ImageVector by lazy { strokeIcon("Grid", 2.2f, "M5.5 4h3a1.5 1.5 0 0 1 1.5 1.5v3a1.5 1.5 0 0 1 -1.5 1.5h-3a1.5 1.5 0 0 1 -1.5 -1.5v-3a1.5 1.5 0 0 1 1.5 -1.5z", "M15.5 4h3a1.5 1.5 0 0 1 1.5 1.5v3a1.5 1.5 0 0 1 -1.5 1.5h-3a1.5 1.5 0 0 1 -1.5 -1.5v-3a1.5 1.5 0 0 1 1.5 -1.5z", "M5.5 14h3a1.5 1.5 0 0 1 1.5 1.5v3a1.5 1.5 0 0 1 -1.5 1.5h-3a1.5 1.5 0 0 1 -1.5 -1.5v-3a1.5 1.5 0 0 1 1.5 -1.5z", "M15.5 14h3a1.5 1.5 0 0 1 1.5 1.5v3a1.5 1.5 0 0 1 -1.5 1.5h-3a1.5 1.5 0 0 1 -1.5 -1.5v-3a1.5 1.5 0 0 1 1.5 -1.5z") }
    val Help: ImageVector by lazy { strokeIcon("Help", 2.2f, "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0", "M9.5 9a2.5 2.5 0 1 1 3.5 2.3c-.6.3-1 .9-1 1.7M12 17v.5") }
    val Home: ImageVector by lazy { strokeIcon("Home", 2.2f, "M3 10.5 12 3l9 7.5V21h-6v-6H9v6H3z") }
    val Image: ImageVector by lazy { strokeIcon("Image", 2.2f, "M6 4h12a3 3 0 0 1 3 3v10a3 3 0 0 1 -3 3h-12a3 3 0 0 1 -3 -3v-10a3 3 0 0 1 3 -3z", "M7 10a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", "m21 16-5-5-9 9") }
    val Info: ImageVector by lazy { strokeIcon("Info", 2.2f, "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0", "M12 8v5M12 16v.5") }
    val Keypad: ImageVector by lazy { strokeIcon("Keypad", 2.2f, "M6 5h.01M12 5h.01M18 5h.01M6 11h.01M12 11h.01M18 11h.01M6 17h.01M12 17h.01M18 17h.01") }
    val Link: ImageVector by lazy { strokeIcon("Link", 2.2f, "M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1") }
    val LocationOff: ImageVector by lazy { strokeIcon("LocationOff", 2.2f, "M12 21s-7-6.2-7-12a7 7 0 0 1 11-5.7M18.6 7.5c.3.5.4 1 .4 1.5 0 5.8-7 12-7 12", "M3 3l18 18") }
    val Lock: ImageVector by lazy { strokeIcon("Lock", 2.2f, "M7 11h10a2 2 0 0 1 2 2v6a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-6a2 2 0 0 1 2 -2z", "M8 11V7a4 4 0 0 1 8 0v4") }
    val Logout: ImageVector by lazy { strokeIcon("Logout", 2.2f, "M15 4h4v16h-4M10 8l-4 4 4 4M6 12h11") }
    val Mail: ImageVector by lazy { strokeIcon("Mail", 2.2f, "M6 5h12a3 3 0 0 1 3 3v8a3 3 0 0 1 -3 3h-12a3 3 0 0 1 -3 -3v-8a3 3 0 0 1 3 -3z", "m3 7 9 6 9-6") }
    val Menu: ImageVector by lazy { strokeIcon("Menu", 2.2f, "M4 7h16M4 12h16M4 17h10") }
    val Mic: ImageVector by lazy { strokeIcon("Mic", 2.2f, "M12 3h0a3 3 0 0 1 3 3v5a3 3 0 0 1 -3 3h-0a3 3 0 0 1 -3 -3v-5a3 3 0 0 1 3 -3z", "M5 11a7 7 0 0 0 14 0M12 18v3") }
    val Micoff: ImageVector by lazy { strokeIcon("Micoff", 2.2f, "M15 9.3V6a3 3 0 0 0-5.7-1.3M9 9v2a3 3 0 0 0 5 2.2M5 11a7 7 0 0 0 11.5 5.4M19 11a7 7 0 0 1-.4 2.3M12 18v3M3 3l18 18") }
    val Minus: ImageVector by lazy { strokeIcon("Minus", 2.2f, "M5 12h14") }
    val Moon: ImageVector by lazy { strokeIcon("Moon", 2.2f, "M20 14A8 8 0 1 1 10 4a7 7 0 0 0 10 10z") }
    val Msg: ImageVector by lazy { strokeIcon("Msg", 2.2f, "M21 12a8 8 0 0 1-11.5 7.2L4 21l1.8-5.2A8 8 0 1 1 21 12z") }
    val Paw: ImageVector by lazy { strokeIcon("Paw", 2.2f, "M5 10a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", "M15 10a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", "M8 5.5a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", "M12 5.5a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", "M12 13c-3 0-5 3-5 5s2 2 5 2 5 0 5-2-2-5-5-5z") }
    val People: ImageVector by lazy { strokeIcon("People", 2.2f, "M5.5 8a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0 -7 0", "M3 20c1-3.5 3.5-5 6-5s5 1.5 6 5M16 4.5a3.5 3.5 0 0 1 0 7M21 20c-.5-2.5-2-4-4-4.6") }
    val Pin: ImageVector by lazy { strokeIcon("Pin", 2.2f, "M12 21s-7-6.2-7-12a7 7 0 0 1 14 0c0 5.8-7 12-7 12z", "M9.5 9a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0") }
    val Plus: ImageVector by lazy { strokeIcon("Plus", 2.2f, "M12 5v14M5 12h14") }
    val Power: ImageVector by lazy { strokeIcon("Power", 2.2f, "M12 3v9", "M6.4 6.4a8 8 0 1 0 11.2 0") }
    val Receipt: ImageVector by lazy { strokeIcon("Receipt", 2.2f, "M5 3h14v18l-3-2-2 2-2-2-2 2-2-2-3 2z", "M9 8h6M9 12h6") }
    val Route: ImageVector by lazy { strokeIcon("Route", 2.2f, "M4 19a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", "M16 5a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", "M8 19h7a3.5 3.5 0 0 0 0-7H9a3.5 3.5 0 0 1 0-7h7") }
    val Route2: ImageVector by lazy { strokeIcon("Route2", 2.2f, "M7 20v-7a4 4 0 0 1 4-4h8", "m15 5 4 4-4 4") }
    val Search: ImageVector by lazy { strokeIcon("Search", 2.2f, "M4 11a7 7 0 1 0 14 0a7 7 0 1 0 -14 0", "m20 20-3.5-3.5") }
    val Send: ImageVector by lazy { strokeIcon("Send", 2.2f, "M4 12 20 4l-6 16-3-7z") }
    val Share: ImageVector by lazy { strokeIcon("Share", 2.2f, "M12 3v13M7 8l5-5 5 5M5 14v5a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-5") }
    val Shield: ImageVector by lazy { strokeIcon("Shield", 2.2f, "M12 3 4 6v6c0 5 3.5 8 8 9 4.5-1 8-4 8-9V6z") }
    val ShieldCheck: ImageVector by lazy { strokeIcon("ShieldCheck", 2.2f, "M12 3 4 6v6c0 5 3.5 8 8 9 4.5-1 8-4 8-9V6z", "m8.5 12 2.5 2.5 4.5-5") }
    val Signal: ImageVector by lazy { strokeIcon("Signal", 2.2f, "M4 20V14M9 20v-9M14 20V8M19 20V4") }
    val Sliders: ImageVector by lazy { strokeIcon("Sliders", 2.2f, "M4 7h10M18 7h2M4 17h4M12 17h8", "M14 7a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", "M8 17a2 2 0 1 0 4 0a2 2 0 1 0 -4 0") }
    val Smartphone: ImageVector by lazy { strokeIcon("Smartphone", 2.2f, "M9 2h6a3 3 0 0 1 3 3v14a3 3 0 0 1 -3 3h-6a3 3 0 0 1 -3 -3v-14a3 3 0 0 1 3 -3z", "M11 18h2") }
    val Speaker: ImageVector by lazy { strokeIcon("Speaker", 2.2f, "M4 9h4l5-4v14l-5-4H4z", "M17 9a4 4 0 0 1 0 6M19.5 6.5a8 8 0 0 1 0 11") }
    val Tag: ImageVector by lazy { strokeIcon("Tag", 2.2f, "M20 12 12 20 4 12V4h8z", "M7 8.5a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0 -3 0") }
    val Trash: ImageVector by lazy { strokeIcon("Trash", 2.2f, "M4 7h16M10 11v6M14 11v6M6 7l1 14h10l1-14M9 7V4h6v3") }
    val TrendUp: ImageVector by lazy { strokeIcon("TrendUp", 2.2f, "M4 17 10 11l4 4 6-7", "M15 8h5v5") }
    val Trophy: ImageVector by lazy { strokeIcon("Trophy", 2.2f, "M8 4h8v5a4 4 0 0 1-8 0zM8 6H4a3 3 0 0 0 4 4M16 6h4a3 3 0 0 1-4 4M12 13v4M8 21h8") }
    val User: ImageVector by lazy { strokeIcon("User", 2.2f, "M8 8a4 4 0 1 0 8 0a4 4 0 1 0 -8 0", "M4 21c1.5-4 4.5-6 8-6s6.5 2 8 6") }
    val Volume: ImageVector by lazy { strokeIcon("Volume", 2.2f, "M4 9h4l5-4v14l-5-4H4z", "M17 9a4 4 0 0 1 0 6") }
    val Wallet: ImageVector by lazy { strokeIcon("Wallet", 2.2f, "M6 6h12a3 3 0 0 1 3 3v8a3 3 0 0 1 -3 3h-12a3 3 0 0 1 -3 -3v-8a3 3 0 0 1 3 -3z", "M3 10h18M16 15h2") }
    val Warning: ImageVector by lazy { strokeIcon("Warning", 2.2f, "M12 3 2 20h20z", "M12 10v4M12 17v.5") }
    val WifiOff: ImageVector by lazy { strokeIcon("WifiOff", 2.2f, "M2 8.5a15 15 0 0 1 4-2.6M9.5 4.9A15 15 0 0 1 22 8.5M5 12a10 10 0 0 1 3.5-2.2M15 10.3a10 10 0 0 1 4 1.7M8.5 15.5a5 5 0 0 1 7 0M12 19.5v.1M3 3l18 18") }
    val Work: ImageVector by lazy { strokeIcon("Work", 2.2f, "M5 7h14a2 2 0 0 1 2 2v9a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-9a2 2 0 0 1 2 -2z", "M9 7V5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v2") }
    val Dots: ImageVector by lazy { fillIcon("Dots", 24f, "M10 5a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", "M10 12a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", "M10 19a2 2 0 1 0 4 0a2 2 0 1 0 -4 0") }
    val Endcall: ImageVector by lazy { fillIcon("Endcall", 24f, "M3 14c5-4.5 13-4.5 18 0l-2 3-3.5-1.2v-2.5a10 10 0 0 0-5 0v2.5L7 17z") }
    val FlagFilled: ImageVector by lazy { fillIcon("FlagFilled", 24f, "M5 21V4h11l-2 4 2 4H5") }
    val Flame: ImageVector by lazy { fillIcon("Flame", 24f, "M12 2c1 4 6 6 6 12a6 6 0 0 1-12 0c0-3 2-5 3-6 0 2 1 3 2 3 0-4-1-6 1-9z") }
    val HomeFilled: ImageVector by lazy { fillIcon("HomeFilled", 24f, "M3 10.5 12 3l9 7.5V21h-6v-6H9v6H3z") }
    val Nav: ImageVector by lazy { fillIcon("Nav", 24f, "M12 2 19 21 12 17 5 21z") }
    val Phone: ImageVector by lazy { fillIcon("Phone", 24f, "M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2") }
    val Play: ImageVector by lazy { fillIcon("Play", 24f, "M8 5v14l11-7z") }
    val ReceiptFilled: ImageVector by lazy { fillIcon("ReceiptFilled", 24f, "M5 3h14v18l-3-2-2 2-2-2-2 2-2-2-3 2z") }
    val RideFilled: ImageVector by lazy { fillIcon("RideFilled", 24f, "M5 11 7 6h10l2 5v6h-2v2h-2v-2H9v2H7v-2H5z") }
    val Star: ImageVector by lazy { fillIcon("Star", 24f, "m12 3 2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z") }
    val UserFilled: ImageVector by lazy { fillIcon("UserFilled", 24f, "M8 8a4 4 0 1 0 8 0a4 4 0 1 0 -8 0", "M4 21c1.5-4 4.5-6 8-6s6.5 2 8 6z") }
    val WalletFilled: ImageVector by lazy { fillIcon("WalletFilled", 24f, "M6 6h12a3 3 0 0 1 3 3v8a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V9a3 3 0 0 1 3-3z") }
    /** Side-view car used in ride options and empty states (30x22 artboard). */
    val CarSide: ImageVector by lazy { fillIcon("CarSide", 30f, "M4 12 7 5h16l3 7v6h-3v2h-3v-2H10v2H7v-2H4z", viewportHeight = 22f) }
}

private fun strokeIcon(name: String, strokeWidth: Float, vararg paths: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        paths.forEach { d ->
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

private fun fillIcon(
    name: String,
    viewportWidth: Float,
    vararg paths: String,
    viewportHeight: Float = viewportWidth,
): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = (24f * viewportWidth / viewportHeight).dp,
        defaultHeight = 24.dp,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
    ).apply {
        paths.forEach { d ->
            addPath(pathData = PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.Black))
        }
    }.build()
