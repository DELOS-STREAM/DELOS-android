package com.limelight.utils;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import com.limelight.display.DualScreen;

/**
 * Brings the handheld control activity to the foreground for a stream on another display.
 */
public final class ExternalDisplayFocus {
    private ExternalDisplayFocus() {
    }

    public static void requestFocusToExternalDisplayControl(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent intentTouchpad = new Intent(context, ExternalDisplayControlActivity.class);
            intentTouchpad.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            Bundle optionsDefault = ActivityOptions.makeBasic().setLaunchDisplayId(DualScreen.mainDisplayId(context)).toBundle();
            context.startActivity(intentTouchpad, optionsDefault);
        }
    }
}
