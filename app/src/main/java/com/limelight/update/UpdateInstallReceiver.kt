package com.limelight.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.limelight.diag.DiagLog

/** Receives the result of the install session ([PackageApkInstaller]); not exported, only the PendingIntent of the session reaches it. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // Android's own prompt: the system hands over the activity that asks the user
            val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
            if (confirm == null) {
                DiagLog.w("Update", "the installer asked for the user but gave no prompt")
                return
            }
            try {
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: RuntimeException) {
                DiagLog.w("Update", "cannot show the install prompt: ${e.javaClass.simpleName}")
            }
            return
        }
        DiagLog.i("Update", "install status $status ${message?.take(200).orEmpty()}")
        Updates.peek()?.onInstallResult(InstallOutcome.of(status, message))
    }
}
