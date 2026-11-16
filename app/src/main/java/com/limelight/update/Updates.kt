package com.limelight.update

import android.content.Context
import androidx.preference.PreferenceManager
import com.limelight.BuildConfig
import com.limelight.diag.DiagLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

/** The one [UpdateController] of the process, built from the real network, preferences and installer. */
object Updates {
    @Volatile private var instance: UpdateController? = null
    private val client by lazy { OkHttpClient() }

    /** The controller; the first call creates it and removes what an earlier run left in the cache. */
    fun controller(context: Context): UpdateController = instance ?: synchronized(this) {
        instance ?: create(context.applicationContext).also { instance = it }
    }

    /** The controller if it exists: the install result receiver needs it only while the app that started the install is alive. */
    fun peek(): UpdateController? = instance

    fun store(context: Context): UpdateStore = UpdateStore(PreferenceManager.getDefaultSharedPreferences(context.applicationContext))

    private fun create(app: Context): UpdateController {
        val store = store(app)
        fun policy() = UpdatePolicy(BuildConfig.DEBUG, if (BuildConfig.DEBUG) store.debugServer else null)
        fun http() = UpdateHttp(client, "Delos/${BuildConfig.VERSION_NAME}", policy().allowCleartext)
        val log = { message: String -> DiagLog.i("Update", message) }
        return UpdateController(
            store,
            checker = { UpdateChecker(http(), store, policy(), BuildConfig.VERSION_NAME, android.os.Build.SUPPORTED_ABIS.toList(), System::currentTimeMillis, log) },
            http = ::http,
            installer = PackageApkInstaller(app),
            cacheDir = app.cacheDir,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            io = Dispatchers.IO,
            clock = System::currentTimeMillis,
            log = log,
        ).also { it.cleanCache() }
    }
}
