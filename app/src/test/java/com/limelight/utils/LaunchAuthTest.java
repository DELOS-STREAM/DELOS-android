package com.limelight.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.limelight.DelosStreamActivity;
import com.limelight.ShortcutTrampoline;
import com.limelight.shell.ShellLink;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.file.Files;

/** Launch intents: Delos trusts the ones it signed and asks about every other one (S-03). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class LaunchAuthTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        LaunchAuth.resetForTest();
    }

    private static Intent launch(String uuid, String appId) {
        return new Intent().putExtra(ShortcutTrampoline.UUID_EXTRA, uuid).putExtra(ShortcutTrampoline.NAME_EXTRA, "Workstation")
                .putExtra(DelosStreamActivity.EXTRA_APP_ID, appId).putExtra(DelosStreamActivity.EXTRA_APP_NAME, "Game");
    }

    @Test
    public void anIntentDelosSignedIsTrusted() {
        assertTrue(LaunchAuth.isTrusted(context, LaunchAuth.sign(context, launch("ABC-1", "42"))));
    }

    @Test
    public void anIntentWithoutASignatureOrWithAMadeUpOneIsNot() {
        assertFalse(LaunchAuth.isTrusted(context, launch("ABC-1", "42")));
        assertFalse(LaunchAuth.isTrusted(context, launch("ABC-1", "42").putExtra(LaunchAuth.EXTRA_TOKEN, "")));
        assertFalse(LaunchAuth.isTrusted(context, launch("ABC-1", "42").putExtra(LaunchAuth.EXTRA_TOKEN, "00ff")));
        assertFalse(LaunchAuth.isTrusted(context, null));
    }

    @Test
    public void aSignatureCoversExactlyThePcAndAppItWasMadeFor() {
        String token = LaunchAuth.sign(context, launch("ABC-1", "42")).getStringExtra(LaunchAuth.EXTRA_TOKEN);
        assertFalse(LaunchAuth.isTrusted(context, launch("ABC-2", "42").putExtra(LaunchAuth.EXTRA_TOKEN, token)));
        assertFalse(LaunchAuth.isTrusted(context, launch("ABC-1", "43").putExtra(LaunchAuth.EXTRA_TOKEN, token)));
        assertFalse(LaunchAuth.isTrusted(context, launch("ABC-1", null).putExtra(LaunchAuth.EXTRA_TOKEN, token)));
        assertFalse(LaunchAuth.isTrusted(context, launch("ABC-1", "42").putExtra(DelosStreamActivity.EXTRA_APP_UUID, "X")
                .putExtra(LaunchAuth.EXTRA_TOKEN, token)));
    }

    @Test
    public void fieldsCannotBeShiftedIntoEachOther() {
        byte[] key = new byte[32];
        assertNotEquals(LaunchAuth.token(key, "ab", "c"), LaunchAuth.token(key, "a", "bc"));
        assertNotEquals(LaunchAuth.token(key, null, "a"), LaunchAuth.token(key, "a", null));
        assertNotEquals(LaunchAuth.token(key, "", "a"), LaunchAuth.token(key, null, "a"));
        assertEquals(LaunchAuth.token(key, "a", "b"), LaunchAuth.token(key, "a", "b"));
    }

    @Test
    public void theSecretIsPerInstallationStoredOutsideBackupsAndSurvivesARestart() throws Exception {
        Intent signed = LaunchAuth.sign(context, launch("ABC-1", "42"));
        File file = new File(context.getNoBackupFilesDir(), "launch_auth.key");
        assertEquals(32, file.length());

        LaunchAuth.resetForTest();
        assertTrue(LaunchAuth.isTrusted(context, signed));

        // Another installation (another secret) does not accept it
        assertTrue(file.delete());
        LaunchAuth.resetForTest();
        assertFalse(LaunchAuth.isTrusted(context, signed));
    }

    @Test
    public void aDamagedSecretFileIsReplaced() throws Exception {
        File file = new File(context.getCacheDir(), "damaged.key");
        Files.write(file.toPath(), new byte[] {1, 2, 3});
        byte[] key = LaunchAuth.loadOrCreateKey(file);
        assertEquals(32, key.length);
        assertArrayEquals(key, LaunchAuth.loadOrCreateKey(file));
    }

    // ---- what the exported shell activity makes of an intent ----

    @Test
    public void anUnsignedPcIntentAsksFirstAndASignedOneOpensThePc() {
        Intent outside = new Intent().putExtra(ShortcutTrampoline.UUID_EXTRA, "ABC-1").putExtra(ShortcutTrampoline.NAME_EXTRA, "Workstation");
        ShellLink link = ShellLink.Companion.from(context, outside);
        assertTrue(link instanceof ShellLink.Launch);
        ShellLink.Launch prompt = (ShellLink.Launch) link;
        assertEquals("Workstation", prompt.getHostName());
        // What the prompt starts once the user agreed is signed, so the trampoline does not ask again
        assertTrue(LaunchAuth.isTrusted(context, prompt.getIntent()));
        assertEquals("ABC-1", prompt.getIntent().getStringExtra(ShortcutTrampoline.UUID_EXTRA));

        ShellLink own = ShellLink.Companion.from(context, LaunchAuth.sign(context, outside));
        assertEquals(new ShellLink.OpenPc("ABC-1"), own);
    }

    @Test
    public void aPcIdThatIsNotAnIdIsIgnored() {
        assertEquals(null, ShellLink.Companion.from(context, new Intent().putExtra(ShortcutTrampoline.UUID_EXTRA, "../../databases")));
    }

    @Test
    public void aLaunchLinkAsksFirstAndCarriesASignedIntent() {
        Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse("art://launch?host_uuid=ABC-1&app_id=42&app_name=Game&host_name=Workstation"));
        ShellLink.Launch prompt = (ShellLink.Launch) ShellLink.Companion.from(context, view);
        assertEquals("Game", prompt.getAppName());
        assertEquals("42", prompt.getIntent().getStringExtra(DelosStreamActivity.EXTRA_APP_ID));
        assertTrue(LaunchAuth.isTrusted(context, prompt.getIntent()));
    }
}
