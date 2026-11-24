package com.limelight.shell.settings;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TutorialStartTest {
    @Test public void existingUserGetsTheNewTutorialOnAPlainStart() {
        assertTrue(TutorialStart.shouldShow(false, true, false));
    }

    @Test public void completionOrSkipSuppressesFutureStarts() {
        assertFalse(TutorialStart.shouldShow(true, true, false));
    }

    @Test public void freshInstallKeepsItsExistingFirstRunFlow() {
        assertFalse(TutorialStart.shouldShow(false, false, false));
    }

    @Test public void streamEntryDefersWithoutConsumingTheOffer() {
        assertFalse(TutorialStart.shouldShow(false, true, true));
        assertTrue(TutorialStart.shouldShow(false, true, false));
    }

    @Test public void changingProfilesDoesNotReviveACompletedTutorial() {
        assertFalse(TutorialStart.shouldShow(true, false, false));
        assertFalse(TutorialStart.shouldShow(true, true, false));
    }
}
