package com.limelight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.limelight.shell.ShellActivity;
import com.limelight.shell.ShellLink;
import com.limelight.utils.LaunchAuth;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * The exported shortcut activity and outside input (S-03): an intent Delos did not sign, or an opened
 * {@code .art} file, starts no stream and wakes no PC. It becomes the {@code art://launch} link that the
 * Delos UI asks about first.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, shadows = {com.limelight.shadows.ShadowMoonBridge.class, com.limelight.shadows.ShadowGameManager.class})
public class ShortcutTrampolineOutsideInputTest {
    private static final String PC = "7B0A3A5E-1C2D-4E5F-8A9B-0C1D2E3F4A5B";

    private Application app;
    private ShadowApplication shadowApp;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        shadowApp = shadowOf(app);
    }

    private Intent run(Intent intent) {
        ShortcutTrampoline activity = Robolectric.buildActivity(ShortcutTrampoline.class, intent).create().get();
        assertTrue("the trampoline ends itself", activity.isFinishing());
        // Nothing may reach the PC: the computer manager service (polling, Wake-on-LAN) is not even bound
        assertTrue(shadowApp.getBoundServiceConnections().isEmpty());
        assertNull(shadowApp.getNextStartedService());
        return shadowOf(activity).getNextStartedActivity();
    }

    private static Intent unsigned() {
        return new Intent(ApplicationProvider.getApplicationContext(), ShortcutTrampoline.class)
                .putExtra(ShortcutTrampoline.UUID_EXTRA, PC).putExtra(ShortcutTrampoline.NAME_EXTRA, "Workstation")
                .putExtra(DelosStreamActivity.EXTRA_APP_ID, "42").putExtra(DelosStreamActivity.EXTRA_APP_NAME, "Half-Life 2");
    }

    private void assertAsksInDelos(Intent started) {
        assertNotNull("outside input goes to the Delos UI", started);
        assertEquals(ShellActivity.class.getName(), started.getComponent().getClassName());
        assertEquals(Intent.ACTION_VIEW, started.getAction());
        Uri link = started.getData();
        assertEquals("art", link.getScheme());
        assertEquals("launch", link.getHost());
        assertEquals(PC, link.getQueryParameter("host_uuid"));
        assertEquals("42", link.getQueryParameter("app_id"));
        assertEquals("Half-Life 2", link.getQueryParameter("app_name"));
        // ...where it is the link that asks first
        ShellLink shellLink = ShellLink.Companion.from(app, started);
        assertTrue(shellLink instanceof ShellLink.Launch);
        assertEquals("Half-Life 2", ((ShellLink.Launch) shellLink).getAppName());
    }

    @Test
    public void anIntentFromAnotherAppAsksInDelosInsteadOfStarting() {
        assertAsksInDelos(run(unsigned()));
    }

    @Test
    public void aForgedSignatureIsOutsideInput() {
        assertAsksInDelos(run(unsigned().putExtra(LaunchAuth.EXTRA_TOKEN, "0123456789abcdef")));
    }

    @Test
    public void aSignatureForAnotherGameDoesNotCoverThisOne() {
        String token = LaunchAuth.sign(app, unsigned().putExtra(DelosStreamActivity.EXTRA_APP_ID, "7")).getStringExtra(LaunchAuth.EXTRA_TOKEN);
        assertAsksInDelos(run(unsigned().putExtra(LaunchAuth.EXTRA_TOKEN, token)));
    }

    @Test
    public void anOpenedArtFileAsksInDelosEvenWithASignedIntentAroundIt() {
        Uri file = Uri.parse("content://downloads/shortcut.art");
        shadowOf(app.getContentResolver()).registerInputStream(file, new ByteArrayInputStream(
                ("# Delos shortcut\n[host_uuid] " + PC + "\n[app_id] 42\n[app_name] Half-Life 2\n").getBytes(StandardCharsets.UTF_8)));
        Intent view = LaunchAuth.sign(app, unsigned()).setAction(Intent.ACTION_VIEW).setData(file);
        assertAsksInDelos(run(view));
    }

    @Test
    public void aBrokenArtFileStartsNothing() {
        Uri file = Uri.parse("content://downloads/broken.art");
        shadowOf(app.getContentResolver()).registerInputStream(file, new ByteArrayInputStream(
                ("[host_uuid] " + PC + "\nthis line is not a key\n").getBytes(StandardCharsets.UTF_8)));
        assertNull(run(new Intent(Intent.ACTION_VIEW, file, app, ShortcutTrampoline.class)));
    }

    @Test
    public void anArtFileThatCannotBeOpenedStartsNothing() {
        assertNull(run(new Intent(Intent.ACTION_VIEW, Uri.parse("content://downloads/missing.art"), app, ShortcutTrampoline.class)));
    }

    @Test
    public void aPcIdThatIsNotAnIdIsRejected() {
        assertNull(run(unsigned().putExtra(ShortcutTrampoline.UUID_EXTRA, "../../databases/x")));
    }
}
