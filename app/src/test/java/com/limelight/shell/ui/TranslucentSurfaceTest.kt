package com.limelight.shell.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * docs/GUI.md, "Glass": every semi-transparent surface blurs what lies behind it for real (Haze: [glass], [backdropBlur], the
 * menu's glass). A flat translucent fill (`.background(...)` of a palette surface or scrim, a colour with an alpha below FF or
 * a `.copy(alpha = …)`) on a line without glass fails, unless [ALLOWED] names it and says why it is not a surface that needs a
 * blur (a full-screen dim, a divider, a track, a fill nested in glass, a fill under opaque art). An entry that matches no line
 * any more fails too, so the list never outlives the code.
 */
class TranslucentSurfaceTest {
    private val shell = listOf(File("src/main/java/com/limelight/shell"), File("app/src/main/java/com/limelight/shell")).first { it.isDirectory }

    /** One allowed fill: the file (its path under shell/), a piece of the line that is unique to it, and why it is not a surface to blur. */
    private class Allowed(val file: String, val line: String, val why: String)

    @Test fun translucentFillsAreGlass() {
        val used = HashSet<Allowed>()
        val offenders = ArrayList<String>()
        shell.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { source ->
            val path = source.relativeTo(shell).invariantSeparatorsPath
            if (EXEMPT_DIRS.any { path.startsWith(it) }) return@forEach
            source.readLines().forEachIndexed { index, line ->
                if (GLASS.any { it in line } || fills(line).none(::translucent)) return@forEachIndexed
                val allowed = ALLOWED.firstOrNull { it.file == path && it.line in line }
                if (allowed != null) used += allowed else offenders += "$path:${index + 1}: ${line.trim()}"
            }
        }
        assertTrue("Translucent fills without glass (use glass(), backdropBlur(), or allow them with a reason):\n" + offenders.joinToString("\n"), offenders.isEmpty())
        val stale = ALLOWED.filter { it !in used }.map { "${it.file}: ${it.line}" }
        assertTrue("Allowed fills that no longer exist:\n" + stale.joinToString("\n"), stale.isEmpty())
    }

    @Test fun theCheckSeesTranslucentFills() {
        assertTrue(fills(".clip(CircleShape).background(Color(0x8C050A14))").all(::translucent))
        assertTrue(fills(".background(colors.surface2)").all(::translucent))
        assertTrue(fills(".background(Color.White.copy(alpha = .06f))").all(::translucent))
        assertTrue(fills(".background(Color(0xFF17223C))").none(::translucent))
        assertTrue(fills(".background(colors.accent)").none(::translucent))
        assertTrue(fills(".background(Brush.verticalGradient(0f to Color.White.copy(alpha = .1f), 1f to Color.Transparent))").none(::translucent))
    }

    /** The arguments of every `.background(…)` on [line]. */
    private fun fills(line: String): List<String> {
        val out = ArrayList<String>()
        var from = 0
        while (true) {
            val at = line.indexOf(".background(", from).takeIf { it >= 0 } ?: return out
            var i = at + ".background(".length
            val start = i
            var depth = 1
            while (i < line.length && depth > 0) {
                if (line[i] == '(') depth++ else if (line[i] == ')') depth--
                i++
            }
            out += line.substring(start, (i - 1).coerceAtLeast(start))
            from = i
        }
    }

    /** A fill that lets what is behind it show: a translucent palette surface or scrim, an alpha below FF, a copy with an alpha. */
    private fun translucent(fill: String): Boolean {
        if ("Brush" in fill || "Gradient" in fill) return false
        return TRANSLUCENT.findAll(fill).any { m -> m.groups[1]?.value?.uppercase()?.let { it != "FF" } ?: true }
    }

    private companion object {
        val GLASS = listOf("glass(", "Glass(", "backdropBlur(", "hazeEffect")

        val TRANSLUCENT = Regex("""Color\(0x([0-9A-Fa-f]{2})[0-9A-Fa-f]{6}\)|colors\.(?:surface|surface2|surface3|scrim)\b|\.copy\(alpha""")

        /** The companion panel (docs/DUAL_SCREEN.md) is a flat opaque `bg`: a blur behind its surfaces would draw the same colour. */
        val EXEMPT_DIRS = listOf("companion/")

        val ALLOWED = listOf(
            // Over the stream's video (a SurfaceView): nothing can blur it, the documented fallback is the darker tint
            Allowed("StreamStats.kt", ".background(Color.Black.copy(alpha = .7f))", "over the stream's video: the darker tint"),
            // Full-screen dims and veils (scrims)
            Allowed("ui/ButtonCapture.kt", ".background(colors.scrim)", "full-screen scrim under the card"),
            Allowed("ui/ProblemOfferCard.kt", "Color.Black.copy(alpha = OFFER_SCRIM)", "full-screen dim under the offer cards"),
            Allowed("ui/PortalOverlay.kt", "colors.bg.copy(alpha = .45f)", "full-screen dim of the portal"),
            Allowed("screens/ArtworkScreen.kt", "Color(0x73050A14)", "full-screen scrim under the filter sheet"),
            Allowed("screens/ArtworkScreen.kt", "Color(0x99050A14)), contentAlignment", "loading veil over the whole tile"),
            Allowed("screens/OverviewVideo.kt", "Color.Black.copy(alpha = .62f)", "veil over the whole video thumbnail"),
            Allowed("screens/SideMenuHeader.kt", ".background(colors.surface)", "the streaming card inside the side menu's glass panel (no blur nested in a blur)"),
            // Dividers, tracks, bar segments, dots
            Allowed("screens/TutorialParts.kt", "i < index -> colors.accent.copy(alpha = .55f)", "the tutorial's progress dots"),
            Allowed("screens/TutorialParts.kt", ".background(TutorialGreen.copy(alpha = .14f))", "the small Connected badge on a choice card's glass"),
            Allowed("ui/ContextMenuLayer.kt", "height(1.px).background(Color.White.copy(alpha = .10f))", "divider"),
            Allowed("screens/InfoBar.kt", "width(2.px).height(28.px)", "divider"),
            Allowed("screens/HomeScreen.kt", ".background(textColor.copy(alpha = .55f))", "divider"),
            Allowed("screens/GameOverview.kt", "width(2.px).height(38.px)", "divider"),
            Allowed("ui/Rows.kt", "width = trackWidth.px", "slider track"),
            Allowed("ui/ArtworkProgressBar.kt", "height(8.px).clip(CircleShape).background(colors.surface2)", "progress track"),
            Allowed("ui/ImpactChips.kt", "colors.text3.copy(alpha = .3f)", "bar segment"),
            Allowed("screens/ArtworkScreen.kt", "Color(0x99050A14))) {", "download track"),
            Allowed("screens/GameOverview.kt", "height(10.px).clip(RoundedCornerShape(5.px))", "playtime track"),
            // Nested in glass: the panel around them blurs already (no blur inside a blur)
            Allowed("ui/StreamResultLayer.kt", ".background(colors.surface)", "details box in the result layer's glass"),
            Allowed("ui/StreamResultLayer.kt", "colors.accent.copy(alpha = .16f)", "chip in the result layer's glass"),
            Allowed("ui/ContextMenuLayer.kt", "clip(CircleShape).background(Color.White.copy(alpha = .10f))", "back disc in the menu's glass"),
            Allowed("ui/RowPicker.kt", ".background(colors.surface2)", "value field in the popup's glass"),
            Allowed("screens/OverviewDock.kt", "background(tint.copy(alpha = .45f))", "dock tint over video where the player window cannot be blurred"),
            Allowed("screens/OverviewDock.kt", "Color(10, 16, 32).copy(alpha = .8f)", "seek time label in the dock's glass"),
            Allowed("screens/OverviewDock.kt", "background(Color.White.copy(alpha = .08f))", "page-dots pill in the dock's glass"),
            Allowed("screens/OverviewDock.kt", "dot.background(Color.White.copy(alpha = .4f))", "page dot in the dock's glass"),
            Allowed("ui/Rows.kt", "background(tint.copy(alpha = .22f))", "reset pill in the focused row's glass"),
            Allowed("ui/ToastHost.kt", "gold.copy(alpha = .16f)", "icon disc on the opaque toast"),
            Allowed("screens/ArtworkScreen.kt", "colors.accent2.copy(alpha = .2f)", "icon disc in the game chip's glass"),
            Allowed("screens/ArtworkScreen.kt", "background(colors.surface2).padding(horizontal = 14.px", "meta chip in the game chip's glass"),
            Allowed("screens/ArtworkScreen.kt", "if (accent) colors.accent else colors.surface2", "badge in the info strip's glass"),
            Allowed("screens/ArtworkScreen.kt", "colors.accent.copy(alpha = .16f)", "count chip in the filter sheet's glass"),
            Allowed("screens/ArtworkScreen.kt", "colors.accent.copy(alpha = .18f)", "icon disc in the filter sheet's glass"),
            Allowed("screens/FilterModal.kt", "if (filters.active) colors.accent.copy(alpha = .16f) else colors.surface", "count chip in the filter panel's glass"),
            Allowed("screens/FilterModal.kt", "colors.accent.copy(alpha = .18f)", "icon disc in the filter panel's glass"),
            Allowed("screens/GameSettingsScreen.kt", "Row(Modifier.fillMaxWidth().clip(shape).background(if (custom)", "source box in the help dock's glass"),
            Allowed("screens/GameSettingsScreen.kt", "Column(Modifier.fillMaxWidth().clip(shape).background(if (custom)", "source box in the help dock's glass"),
            Allowed("keyboard/FullKeyboard.kt", "content.copy(alpha = content.alpha * .55f)", "mark on an opaque key"),
            Allowed("keyboard/FullKeyboard.kt", "palette.onLatched.copy(alpha = .85f)", "bar on an opaque key"),
            Allowed("screens/MetadataUi.kt", "Color.Black.copy(alpha = HDR_SCRIM)", "shade over the badge's opaque gradient"),
            // Under opaque art: the picture covers the fill
            Allowed("screens/HomeScreen.kt", ".background(colors.surface2)", "under the cover's opaque art"),
            Allowed("screens/GameOverview.kt", ".background(colors.surface2),", "under opaque art (the cover, a screenshot, a similar game)"),
            // Performance: the settings search's 30 to 45 keys as glass are as many blurs of the animated background every frame (measured
            // on the emulator: janky frames 0.6 % to 9.8 %, 95th percentile 26 to 89 ms)
            Allowed("screens/SettingsScreen.kt", ".background(if (focused) colors.surface2 else colors.surface)", "settings search keys: one blur per key is too slow"),
        )
    }
}
