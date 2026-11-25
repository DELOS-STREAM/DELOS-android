package com.limelight.binding.input.driver

import android.app.Activity
import android.os.Bundle

/**
 * Started by Android when a wired DualSense is plugged in (manifest filter `usb_dualsense`). Choosing DELOS with "Always" in
 * Android's prompt keeps the USB permission for that controller, so a stream no longer asks for it every time (#125). Nothing is
 * shown: UsbDriverService claims the controller when a stream wants it.
 */
class UsbAttachActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
