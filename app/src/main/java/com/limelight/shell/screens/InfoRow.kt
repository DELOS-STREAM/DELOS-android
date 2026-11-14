package com.limelight.shell.screens

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.limelight.shell.data.GameGenre
import com.limelight.shell.ui.GameColors
import com.limelight.shell.ui.LocalDelosColors
import com.limelight.shell.ui.glass
import com.limelight.shell.ui.genreIcon
import com.limelight.shell.ui.genreName
import com.limelight.shell.ui.textResources
import com.limelight.shell.ui.themeName
import com.limelight.shell.ui.hsl
import com.limelight.shell.ui.themeIcon

/** One icon and label inside an info pill; a pill holds one entry, or two set apart by a divider. */
@Immutable
internal class PillEntry(val icon: String, val label: String)

/** The colors of the genre pill: a soft tint of one hue, its text and icon a light shade of it. */
@Immutable
private class GenreTint(val fill: Color, val border: Color, val content: Color)

private fun genreTint(hue: Float, saturation: Float, dark: Boolean): GenreTint {
    val s = saturation.coerceIn(20f, 95f)
    return if (dark) GenreTint(hsl(hue, s, 34f, .35f), hsl(hue, s, 55f, .55f), hsl(hue, s, 85f))
    else GenreTint(hsl(hue, s, 50f, .2f), hsl(hue, s, 40f, .5f), hsl(hue, s, 24f))
}

/**
 * The game's genres (one or two, in one pill): the size of every info pill, filled with a tint of the game's main
 * color (the accent's while the game's colors are off or unknown), so it is the row's accent.
 */
@Composable
internal fun GenrePill(genres: List<GameGenre>, gameColors: GameColors?) {
    val res = textResources()
    val colors = LocalDelosColors.current
    val hue = gameColors?.hues?.firstOrNull() ?: colors.accentHue
    // How vivid the game's color is (1 = as the palette's) sets the pill's saturation; the accent's is 1
    val vividness = gameColors?.saturations?.firstOrNull() ?: 1f
    val tint = genreTint(hue, vividness * 80f, colors.dark)
    Pill(genres.map { PillEntry(genreIcon(it.id), genreName(res, it)) }, tint.content, tint.content, Modifier.glass(colors.surface).background(tint.fill), tint.border)
}

/** How far the theme's hue lies from the genre's when the game has no second color. */
private const val THEME_HUE_SHIFT = 40f

/** The theme's pill: the genre's recipe in the game's second color, but fainter, so the genre stays the accent. */
private fun themeTint(hue: Float, saturation: Float, dark: Boolean): GenreTint {
    val s = saturation.coerceIn(20f, 95f)
    return if (dark) GenreTint(hsl(hue, s, 30f, .16f), hsl(hue, s, 55f, .45f), hsl(hue, s, 80f, .9f))
    else GenreTint(hsl(hue, s, 50f, .14f), hsl(hue, s, 40f, .45f), hsl(hue, s, 26f, .92f))
}

/**
 * The game's themes (one or two, in one pill): tinted with the game's second cover color (its main color shifted
 * along the hue wheel when it has only one, the palette's accent color while the game's colors are off or unknown),
 * fainter than the genre.
 */
@Composable
internal fun ThemePill(themes: List<GameGenre>, gameColors: GameColors?) {
    val res = textResources()
    val colors = LocalDelosColors.current
    val hues = gameColors?.hues.orEmpty()
    val hue = hues.getOrNull(1) ?: if (hues.isEmpty()) colors.accent3Hue else hues[0] + THEME_HUE_SHIFT
    val saturations = gameColors?.saturations.orEmpty()
    val vividness = saturations.getOrNull(1) ?: saturations.firstOrNull() ?: 1f
    val tint = themeTint(hue, vividness * 80f * .7f, colors.dark)
    Pill(themes.map { PillEntry(themeIcon(it.id), themeName(res, it)) }, tint.content, tint.content, Modifier.glass(colors.surface).background(tint.fill), tint.border)
}
