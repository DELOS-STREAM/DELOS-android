package com.limelight.shell.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.limelight.shell.settings.Appearance
import com.limelight.shell.settings.Palette
import com.limelight.shell.ui.delosColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The stat block sits on the brightest part of the game background: its texts must read there, in every palette and both themes. */
class PlaytimeScrimTest {
    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance(); val lb = b.luminance()
        return (maxOf(la, lb) + .05f) / (minOf(la, lb) + .05f)
    }

    /** Bright and busy game backgrounds as the hero art makes them behind the block: pale yellow, white, light cyan, orange. */
    private val brightHeroes = listOf(Color(0xFFF5E6A8), Color.White, Color(0xFFBFF0F5), Color(0xFFF2A65A))

    @Test fun stepsMakeTheScrimsOpacity() {
        // STEPS rounded rectangles of stepAlpha one over the other give COLOR_ALPHA behind the block's middle
        var behind = 0.0
        repeat(PlaytimeScrim.STEPS) { behind = behind + (1.0 - behind) * PlaytimeScrim.stepAlpha }
        assertEquals(PlaytimeScrim.COLOR_ALPHA.toDouble(), behind, .001)
    }

    @Test fun everyTextOfTheBlockReadsOverABrightGameBackground() {
        for (palette in Palette.entries) for (dark in listOf(true, false)) {
            val c = delosColors(Appearance(dark = dark, palette = palette), null)
            for (hero in brightHeroes) {
                val behind = PlaytimeScrim.over(c.bg, hero)
                // Small text reads at 4.5, or at least as well as on the plain background where the theme's own color is weaker (the light theme's
                // dim text and accents): the scrim never makes it noticeably worse (three tenths of a ratio point: an orange game background keeps 10 % of itself behind the light theme's scrim)
                for ((name, text) in listOf("last eyebrow" to c.text3, "unit" to c.text2, "value" to c.text, "accent eyebrow" to c.accent)) {
                    val plain = contrast(text.compositeOver(c.bg), c.bg)
                    val ratio = contrast(text.compositeOver(behind), behind)
                    assertTrue("$palette dark=$dark $name over $hero: $ratio against $plain", ratio >= minOf(plain, 4.5f) - .3f)
                }
            }
        }
    }

    @Test fun theDimEyebrowReadsAs45OnTheScrimOfADarkHero() {
        for (palette in Palette.entries) {
            val c = delosColors(Appearance(dark = true, palette = palette), null)
            val behind = PlaytimeScrim.over(c.bg, Color(0xFF22304A))
            assertTrue("$palette", contrast(c.text3.compositeOver(behind), behind) >= 4.5f)
        }
    }
}
