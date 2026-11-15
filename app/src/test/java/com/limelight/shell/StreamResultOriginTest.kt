package com.limelight.shell

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The exported shell activity shows a stream result only when Delos itself wrote it (S-04). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StreamResultOriginTest {
    private val result = StreamResult(StreamResultKind.CONNECTION_LOST, "Title", "Sentence", "Details", "ABC-1", "Workstation", 42, "Game")

    @Test
    fun extrasFromAnotherAppAreNotAResult() {
        val forged = Intent()
            .putExtra("DelosResultKind", "CONNECTION_LOST").putExtra("DelosResultTitle", "Update required")
            .putExtra("DelosResultSentence", "Install this").putExtra("DelosResultPcUuid", "ABC-1")
        assertNull(StreamResult.take(forged))
        assertNull(StreamResult.take(forged.putExtra("DelosResultOrigin", "guess")))
    }

    @Test
    fun aCopiedResultWithoutThisProcessMarkIsNotShown() {
        val own = result.putInto(Intent())
        val copy = Intent().putExtras(own.extras!!)
        copy.removeExtra("DelosResultOrigin")
        assertNull(StreamResult.take(copy))
        assertEquals("Title", StreamResult.take(own)?.title)
    }
}
