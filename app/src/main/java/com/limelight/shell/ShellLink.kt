package com.limelight.shell

import android.content.Context
import android.content.Intent
import com.limelight.ShortcutTrampoline
import com.limelight.DelosStreamActivity
import com.limelight.utils.HostIds
import com.limelight.utils.LaunchAuth

/**
 * What a link, another app or a shortcut asks the Delos UI to do: art:// links (as the classic
 * AddComputerManually parsed them) and PC shortcuts (ShortcutTrampoline).
 */
sealed interface ShellLink {
    /** `art://host[:port]?pin=…&passphrase=…[&name=…]`: add the PC and pair it (with the one-time code, if given). */
    data class Pair(val address: String, val name: String?, val otp: Otp?) : ShellLink

    /**
     * `art://launch?host_uuid=…&app_id=…`, or a PC another app asks to open: start a game (or open the PC, which may
     * wake it) once the user agreed. [intent] is for ShortcutTrampoline and signed ([LaunchAuth]), so it starts without asking again.
     */
    data class Launch(val intent: Intent, val appName: String?, val hostName: String?) : ShellLink

    /** A PC shortcut Delos made itself: open that PC's games. */
    data class OpenPc(val uuid: String) : ShellLink

    companion object {
        fun from(context: Context, intent: Intent?): ShellLink? {
            intent ?: return null
            val data = intent.data
            if (intent.action == Intent.ACTION_VIEW && data != null && data.scheme == "art") {
                if (data.port == -1 && data.host == "launch") {
                    val launch = trampoline(context)
                        .putExtra(ShortcutTrampoline.UUID_EXTRA, data.getQueryParameter("host_uuid"))
                        .putExtra(ShortcutTrampoline.NAME_EXTRA, data.getQueryParameter("host_name"))
                        .putExtra(DelosStreamActivity.EXTRA_APP_UUID, data.getQueryParameter("app_uuid"))
                        .putExtra(DelosStreamActivity.EXTRA_APP_NAME, data.getQueryParameter("app_name"))
                        .putExtra(DelosStreamActivity.EXTRA_APP_ID, data.getQueryParameter("app_id"))
                    return Launch(LaunchAuth.sign(context, launch), data.getQueryParameter("app_name"), data.getQueryParameter("host_name"))
                }
                val address = data.authority?.takeIf { it.isNotEmpty() } ?: return null
                val pin = data.getQueryParameter("pin")
                val passphrase = data.getQueryParameter("passphrase")
                return Pair(address, data.getQueryParameter("name"), if (pin != null && passphrase != null) Otp(pin, passphrase) else null)
            }
            val uuid = intent.getStringExtra(ShortcutTrampoline.UUID_EXTRA)?.takeIf { HostIds.isValidUuid(it) } ?: return null
            if (LaunchAuth.isTrusted(context, intent)) return OpenPc(uuid)
            // The activity is exported and opening a PC can wake it: an intent Delos did not sign asks first
            val name = intent.getStringExtra(ShortcutTrampoline.NAME_EXTRA)
            val open = trampoline(context).putExtra(ShortcutTrampoline.UUID_EXTRA, uuid).putExtra(ShortcutTrampoline.NAME_EXTRA, name)
            return Launch(LaunchAuth.sign(context, open), null, name)
        }

        private fun trampoline(context: Context) = Intent(context, ShortcutTrampoline::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
