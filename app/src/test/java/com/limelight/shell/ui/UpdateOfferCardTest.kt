package com.limelight.shell.ui

import androidx.test.core.app.ApplicationProvider
import com.limelight.shell.screens.updateResultText
import com.limelight.update.CheckResult
import com.limelight.update.SemVer
import com.limelight.update.UpdateCandidate
import com.limelight.update.UpdateErrorKind
import com.limelight.update.UpdateFailure
import com.limelight.update.UpdateState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "en")
class UpdateOfferCardTest {
    private val res = ApplicationProvider.getApplicationContext<android.content.Context>().resources
    private val c = UpdateCandidate("v1.1.0", SemVer.parse("1.1.0")!!, "", "delos-1.1.0-arm64-v8a.apk", "https://x/a.apk", 10, "0".repeat(64))

    @Test fun theButtonsFollowTheState() {
        assertEquals(UpdateCardPrimary.UPDATE, updateCardPrimary(UpdateState.Offer(c)))
        assertEquals(UpdateCardPrimary.ALLOW, updateCardPrimary(UpdateState.NeedsPermission(c)))
        assertEquals(UpdateCardPrimary.CANCEL, updateCardPrimary(UpdateState.Downloading(c, 1, 10)))
        assertEquals(UpdateCardPrimary.RETRY, updateCardPrimary(UpdateState.Failed(c, UpdateFailure.DOWNLOAD)))
        assertEquals(UpdateCardPrimary.RETRY, updateCardPrimary(UpdateState.Failed(c, UpdateFailure.CHECKSUM)))
        // Trying again with a file signed by another key can only fail again
        assertEquals(UpdateCardPrimary.NONE, updateCardPrimary(UpdateState.Failed(c, UpdateFailure.SIGNATURE)))
        assertEquals(UpdateCardPrimary.NONE, updateCardPrimary(UpdateState.Installing(c)))
        assertEquals(UpdateCardPrimary.NONE, updateCardPrimary(UpdateState.Verified(c, File("x"))))
    }

    @Test fun theProgressIsAWholePercent() {
        assertNull(downloadPercent(5, -1))
        assertNull(downloadPercent(5, 0))
        assertEquals(0, downloadPercent(0, 10))
        assertEquals(33, downloadPercent(1, 3))
        assertEquals(100, downloadPercent(10, 10))
        assertEquals(100, downloadPercent(11, 10))
    }

    @Test fun everyFailureHasItsOwnSentence() {
        val texts = UpdateFailure.values().map { updateFailureText(res, it) }
        assertEquals(texts.size, texts.toSet().size)
        assertTrue(texts.all { it.isNotBlank() })
        assertTrue(updateFailureText(res, UpdateFailure.SIGNATURE).contains("key"))
    }

    @Test fun theCheckNowRowSaysWhatHappened() {
        assertEquals("Up to date", updateResultText(res, CheckResult.UpToDate("v1.0.0")))
        assertEquals("New version 1.1.0", updateResultText(res, CheckResult.Available(c)))
        assertEquals("Version 1.1.0 has no download for this device", updateResultText(res, CheckResult.NoDownload("v1.1.0")))
        assertEquals("Could not reach GitHub", updateResultText(res, CheckResult.Failed(UpdateErrorKind.NETWORK)))
        assertEquals("GitHub is busy, try later", updateResultText(res, CheckResult.Failed(UpdateErrorKind.RATE_LIMITED)))
        assertEquals("GitHub’s answer was unusable", updateResultText(res, CheckResult.Failed(UpdateErrorKind.BAD_ANSWER)))
        assertEquals("Off", updateResultText(res, CheckResult.Disabled))
        assertEquals("Not in debug builds", updateResultText(res, CheckResult.DebugBuild))
    }
}
