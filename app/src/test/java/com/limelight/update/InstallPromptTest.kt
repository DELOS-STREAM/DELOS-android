package com.limelight.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The system install prompt: when it cannot be shown, the update must end as failed instead of waiting on "Installing". */
@RunWith(RobolectricTestRunner::class)
class InstallPromptTest {
    @Test fun aShownPromptWaitsForTheSessionResult() {
        var started: String? = null
        assertNull(promptOrFailure("prompt") { started = it })
        assertEquals("prompt", started)
    }

    @Test fun noPromptFailsTheInstall() {
        assertEquals(InstallOutcome.FAILED, promptOrFailure<String>(null) { error("must not start") })
    }

    @Test fun aPromptThatCannotStartFailsTheInstall() {
        assertEquals(InstallOutcome.FAILED, promptOrFailure("prompt") { throw SecurityException("background activity start blocked") })
    }
}
