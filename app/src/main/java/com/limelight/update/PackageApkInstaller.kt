package com.limelight.update

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.limelight.diag.DiagLog
import java.io.File
import java.io.IOException

/**
 * The system side of the update: `PackageInstaller` with one session for the verified APK. The result arrives at
 * [UpdateInstallReceiver]. On Android 12 and later the session asks for no user action when the system allows it for an
 * update of the app that the same installer put there; otherwise Android shows its own prompt. Not verified on a device.
 */
class PackageApkInstaller(context: Context) : ApkInstaller {
    private val app = context.applicationContext

    override fun canInstall(): Boolean = app.packageManager.canRequestPackageInstalls()

    override fun openPermissionSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            app.startActivity(intent)
        } catch (e: RuntimeException) {
            DiagLog.w("Update", "cannot open the install permission page: ${e.javaClass.simpleName}")
        }
    }

    @SuppressLint("UnspecifiedImmutableFlag") // the system adds the result to the intent, so it must be mutable (API 31+)
    override fun install(apk: File) {
        val installer = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(app.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = try {
            installer.createSession(params)
        } catch (e: RuntimeException) {
            throw IOException("cannot create an install session", e)
        }
        val session = try {
            installer.openSession(id)
        } catch (e: RuntimeException) {
            throw IOException("cannot open the install session", e)
        }
        try {
            apk.inputStream().use { input ->
                session.openWrite("delos.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val result = Intent(app, UpdateInstallReceiver::class.java).setPackage(app.packageName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            session.commit(PendingIntent.getBroadcast(app, id, result, flags).intentSender)
        } catch (e: IOException) {
            session.abandon()
            throw e
        } catch (e: RuntimeException) {
            session.abandon()
            throw IOException("the install session failed", e)
        } finally {
            session.close()
        }
    }
}
