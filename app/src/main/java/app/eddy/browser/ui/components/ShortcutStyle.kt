package app.eddy.browser.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** Selectable glyphs for shortcuts; "auto" (not listed) means favicon or initial. */
object ShortcutIcons {
    const val AUTO = "auto"
    val all: Map<String, ImageVector> = linkedMapOf(
        "globe" to Icons.Rounded.Language,
        "star" to Icons.Rounded.Star,
        "heart" to Icons.Rounded.Favorite,
        "home" to Icons.Rounded.Home,
        "mail" to Icons.Rounded.Mail,
        "video" to Icons.Rounded.PlayCircle,
        "music" to Icons.Rounded.MusicNote,
        "news" to Icons.Rounded.Article,
        "shop" to Icons.Rounded.ShoppingBag,
        "map" to Icons.Rounded.Map,
        "book" to Icons.Rounded.MenuBook,
        "code" to Icons.Rounded.Code,
        "game" to Icons.Rounded.SportsEsports,
        "photo" to Icons.Rounded.Photo,
        "work" to Icons.Rounded.Work,
    )
}

/** Shortcut colours come from the live colour scheme so they follow dynamic colour and dark mode. */
object ShortcutColors {
    const val COUNT = 8

    fun container(index: Int, s: ColorScheme): Color = when (index.mod(COUNT)) {
        0 -> s.primaryContainer
        1 -> s.secondaryContainer
        2 -> s.tertiaryContainer
        3 -> s.errorContainer
        4 -> s.surfaceContainerHighest
        5 -> s.primary
        6 -> s.secondary
        else -> s.tertiary
    }

    fun content(index: Int, s: ColorScheme): Color = when (index.mod(COUNT)) {
        0 -> s.onPrimaryContainer
        1 -> s.onSecondaryContainer
        2 -> s.onTertiaryContainer
        3 -> s.onErrorContainer
        4 -> s.onSurface
        5 -> s.onPrimary
        6 -> s.onSecondary
        else -> s.onTertiary
    }
}
