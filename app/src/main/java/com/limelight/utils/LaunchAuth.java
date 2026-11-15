package com.limelight.utils;

import android.content.Context;
import android.content.Intent;

import com.limelight.DelosStreamActivity;
import com.limelight.LimeLog;
import com.limelight.ShortcutTrampoline;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Tells a launch intent that Delos made itself (a TV channel entry, a link the user confirmed) from
 * one another app or a file supplied. The exported activities cannot see who sent an intent, so
 * Delos signs its own: an HMAC-SHA256 over the PC and app the intent names, keyed with a secret
 * that never leaves this installation (it lives in the no-backup directory). An intent without a
 * valid signature is outside input and has to pass the security prompt first.
 */
public final class LaunchAuth {
    public static final String EXTRA_TOKEN = "DelosLaunchToken";

    private static final String KEY_FILE = "launch_auth.key";
    private static final int KEY_BYTES = 32;
    private static final String ALGORITHM = "HmacSHA256";

    private static byte[] cachedKey;

    private LaunchAuth() {}

    /** Adds the signature of what {@code intent} launches; call it after the last launch extra is set. */
    public static Intent sign(Context context, Intent intent) {
        return intent.putExtra(EXTRA_TOKEN, token(key(context), intent));
    }

    /** True when {@code intent} carries the signature Delos gave to exactly this PC and app. */
    public static boolean isTrusted(Context context, Intent intent) {
        String token = intent != null ? intent.getStringExtra(EXTRA_TOKEN) : null;
        if (token == null) {
            return false;
        }
        return MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                token(key(context), intent).getBytes(StandardCharsets.UTF_8));
    }

    static String token(byte[] key, Intent intent) {
        return token(key,
                intent.getStringExtra(ShortcutTrampoline.UUID_EXTRA),
                intent.getStringExtra(ShortcutTrampoline.NAME_EXTRA),
                intent.getStringExtra(DelosStreamActivity.EXTRA_APP_UUID),
                intent.getStringExtra(DelosStreamActivity.EXTRA_APP_ID),
                intent.getStringExtra(DelosStreamActivity.EXTRA_APP_NAME));
    }

    /** Each field is written with its length, so no two different field lists share one message. */
    static String token(byte[] key, String... fields) {
        StringBuilder message = new StringBuilder();
        for (String field : fields) {
            if (field == null) {
                message.append("-;");
            } else {
                message.append(field.length()).append(':').append(field).append(';');
            }
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            byte[] digest = mac.doFinal(message.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static synchronized byte[] key(Context context) {
        if (cachedKey == null) {
            cachedKey = loadOrCreateKey(new File(context.getNoBackupFilesDir(), KEY_FILE));
        }
        return cachedKey;
    }

    /**
     * The stored secret, or a new one when there is none or it is unreadable (entries signed with a
     * lost secret then simply ask first). A secret that cannot be stored still works for this process.
     */
    static byte[] loadOrCreateKey(File file) {
        try {
            if (file.length() == KEY_BYTES) {
                return Files.readAllBytes(file.toPath());
            }
        } catch (IOException e) {
            LimeLog.warning("Launch secret unreadable: " + e);
        }
        byte[] key = new byte[KEY_BYTES];
        new SecureRandom().nextBytes(key);
        File temp = new File(file.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) {
            out.write(key);
            out.getFD().sync();
        } catch (IOException e) {
            LimeLog.warning("Launch secret not stored: " + e);
            return key;
        }
        if (!temp.renameTo(file)) {
            LimeLog.warning("Launch secret not stored");
        }
        return key;
    }

    /** Tests only: forget the secret read from disk. */
    static synchronized void resetForTest() {
        cachedKey = null;
    }
}
