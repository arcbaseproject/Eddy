package app.eddy.browser.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Central layout tokens. Never hardcode sizes in screens; add a token here instead. */
object Dimens {
    val touchTarget: Dp = 48.dp
    val gutter: Dp = 16.dp
    val gutterSmall: Dp = 8.dp
    val gutterLarge: Dp = 24.dp

    val omniboxHeight: Dp = 52.dp
    val omniboxHeightCompact: Dp = 32.dp
    val toolbarHeight: Dp = 52.dp
    val chromeGap: Dp = 6.dp
    /** Space the web page must reserve while the chrome is fully expanded. */
    val chromeExpanded: Dp = omniboxHeight + toolbarHeight + chromeGap * 3
    val chromeCollapsed: Dp = omniboxHeightCompact + chromeGap * 2

    val progressHeight: Dp = 3.dp
    val favicon: Dp = 24.dp
    val faviconLarge: Dp = 40.dp
    val shortcutSize: Dp = 64.dp
    val shortcutSizeCompact: Dp = 52.dp
    val tabCardThumbHeight: Dp = 148.dp
    val sheetMaxWidth: Dp = 640.dp
    val screenMaxWidth: Dp = 720.dp
}
