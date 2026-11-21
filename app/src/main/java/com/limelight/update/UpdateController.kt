package com.limelight.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Why an update stopped; the offer card maps it to a sentence. */
enum class UpdateFailure { DOWNLOAD, TOO_LARGE, STORAGE, CHECKSUM, SIGNATURE, INSTALL }

/** What the offer card shows (docs/UPDATES.md "Offer card"). */
sealed interface UpdateState {
    object Idle : UpdateState

    /** A newer version: Update or Later. */
    class Offer(val candidate: UpdateCandidate) : UpdateState

    /** Android has not allowed Delos to install apps yet: the card says so and opens that settings page. */
    class NeedsPermission(val candidate: UpdateCandidate) : UpdateState

    /** [total] is -1 while unknown. */
    class Downloading(val candidate: UpdateCandidate, val received: Long, val total: Long) : UpdateState

    /** Downloaded and verified; waits for the shell to be in front before the system installer is started. */
    class Verified(val candidate: UpdateCandidate, val file: File) : UpdateState

    /** The system installer has the file; its own prompt asks the user. */
    class Installing(val candidate: UpdateCandidate) : UpdateState

    class Failed(val candidate: UpdateCandidate, val failure: UpdateFailure) : UpdateState
}

/** What the system installer reported for a session, reduced to what Delos tells the user. */
enum class InstallOutcome {
    SUCCESS, ABORTED, SIGNATURE, FAILED;

    companion object {
        /**
         * Maps the `PackageInstaller.EXTRA_STATUS` and `EXTRA_STATUS_MESSAGE` of a finished session ([status] values of
         * `PackageInstaller.STATUS_*`: 0 success, 3 aborted, 4 failure, 5 blocked, 6 conflict, 7 incompatible; 1 pending user action is not final).
         * An update signed with another key than the installed app fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.
         * Not verified on a device: the exact status and message the system gives for it.
         */
        fun of(status: Int, message: String?): InstallOutcome = when {
            status == 0 -> SUCCESS
            status == 3 -> ABORTED
            message != null && (message.contains("UPDATE_INCOMPATIBLE") || message.contains("signatures do not match", ignoreCase = true)) -> SIGNATURE
            // The conflict status is what the system reports for it; without a message it is the likeliest cause
            status == 6 && message == null -> SIGNATURE
            else -> FAILED
        }
    }
}

/** The part of the system the updater needs; the real one is `PackageApkInstaller`. */
interface ApkInstaller {
    /** Whether the user allowed Delos to install apps ("install unknown apps"). */
    fun canInstall(): Boolean

    /** Opens the system page where the user allows that for Delos. */
    fun openPermissionSettings()

    /** Hands [apk] to the system installer; the result arrives through [UpdateController.onInstallResult]. */
    @Throws(IOException::class)
    fun install(apk: File)
}

/**
 * The updater's state machine (docs/UPDATES.md): check, offer, download, verify, install. All decisions are here so the JVM
 * tests cover them; the network is [UpdateChecker] and [UpdateHttp], the system is [ApkInstaller]. It lives as long as the process,
 * so a download goes on while the activity is recreated.
 */
class UpdateController(
    private val store: UpdateStore,
    private val checker: () -> UpdateChecker,
    private val http: () -> UpdateHttp,
    private val installer: ApkInstaller,
    private val cacheDir: File,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val clock: () -> Long,
    private val log: (String) -> Unit,
) {
    private val mutableState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> get() = mutableState
    private val mutableRequested = MutableStateFlow(false)

    /** True while the current offer came from Check now: the user asked, so the shell shows the card over Settings too. */
    val requested: StateFlow<Boolean> get() = mutableRequested
    private var job: Job? = null

    /** Removes what an earlier run left in the cache (a download that was interrupted, an APK after its install). */
    fun cleanCache() { File(cacheDir, "updates").deleteRecursively() }

    /** The check on app start: silent unless there is something to offer, and hidden for 3 days after "Later". */
    fun checkInBackground(): Job = scope.launch {
        val result = withContext(io) { checker().check(force = false) }
        if (result is CheckResult.Available && !hiddenByLater(result.candidate)) offer(result.candidate, requested = false)
    }

    /** Settings > About > Check now: asks GitHub whatever the last check was; an offer shows even after "Later", at once. */
    suspend fun checkNow(): CheckResult {
        val result = withContext(io) { checker().check(force = true) }
        if (result is CheckResult.Available) offer(result.candidate, requested = true)
        return result
    }

    private fun offer(candidate: UpdateCandidate, requested: Boolean) {
        // A download or install in progress is not interrupted by a check
        if (!mutableState.value.busy()) {
            mutableState.value = UpdateState.Offer(candidate)
            mutableRequested.value = requested
        }
    }

    private fun hiddenByLater(candidate: UpdateCandidate) =
        UpdateSchedule.hidden(clock(), candidate.tag, store.laterTag, store.laterUntil)

    /** "Later" (and B on an offer, a failure or the permission note): hides this version for 3 days; a newer one shows at once. */
    fun later() {
        val current = mutableState.value
        val candidate = current.candidate() ?: return
        if (current.busy()) return
        store.later(candidate.tag, UpdateSchedule.laterUntil(clock()))
        mutableState.value = UpdateState.Idle
        mutableRequested.value = false
    }

    /** "Update": checks the permission, then downloads and verifies; the install follows through [installVerified]. */
    fun accept(): Job? {
        val current = mutableState.value
        val candidate = when (current) {
            is UpdateState.Offer -> current.candidate
            is UpdateState.NeedsPermission -> current.candidate
            is UpdateState.Failed -> current.candidate
            else -> return null
        }
        if (!installer.canInstall()) {
            mutableState.value = UpdateState.NeedsPermission(candidate)
            return null
        }
        return scope.launch { download(candidate) }.also { job = it }
    }

    /** The permission note's action: opens the system page. */
    fun openPermissionSettings() { if (mutableState.value is UpdateState.NeedsPermission) installer.openPermissionSettings() }

    /** The shell is in front again: when the user allowed the install on the settings page, the update goes on. */
    fun onResume(): Job? = if (mutableState.value is UpdateState.NeedsPermission && installer.canInstall()) accept() else null

    /** B while downloading: stops it, deletes the partial file and returns to the offer. */
    fun cancel() { job?.cancel() }

    private suspend fun download(candidate: UpdateCandidate) {
        val name = candidate.apkName
        if (!SAFE_NAME.matches(name)) {
            log("refusing file name '$name'")
            mutableState.value = UpdateState.Failed(candidate, UpdateFailure.DOWNLOAD)
            return
        }
        val dir = File(cacheDir, "updates")
        val file = File(dir, name)
        mutableState.value = UpdateState.Downloading(candidate, 0, candidate.apkSize)
        try {
            withContext(io) {
                dir.deleteRecursively()
                http().download(candidate.apkUrl, file, UpdateHttp.MAX_APK_BYTES) { done, total ->
                    mutableState.value = UpdateState.Downloading(candidate, done, if (total > 0) total else candidate.apkSize)
                }
                val verdict = Checksums.verify(file, name, mapOf(name to candidate.sha256))
                if (verdict != Checksums.Verdict.OK) {
                    file.delete()
                    log("checksum of $name: $verdict, not installing")
                    mutableState.value = UpdateState.Failed(candidate, UpdateFailure.CHECKSUM)
                    return@withContext
                }
                log("downloaded and verified $name")
                mutableState.value = UpdateState.Verified(candidate, file)
            }
        } catch (e: CancellationException) {
            file.delete()
            log("download of $name cancelled")
            mutableState.value = UpdateState.Offer(candidate)
            throw e
        } catch (e: UpdateException) {
            file.delete()
            log("download of $name failed: ${e.kind} ${e.message}")
            mutableState.value = UpdateState.Failed(candidate, if (e.kind == UpdateErrorKind.TOO_LARGE) UpdateFailure.TOO_LARGE else if (e.kind == UpdateErrorKind.STORAGE) UpdateFailure.STORAGE else UpdateFailure.DOWNLOAD)
        } catch (e: IOException) {
            file.delete()
            log("download of $name failed: ${e.javaClass.simpleName}")
            mutableState.value = UpdateState.Failed(candidate, UpdateFailure.DOWNLOAD)
        }
    }

    /** The shell shows the card: hands the verified file to the system installer. Never called during a stream. */
    fun installVerified(): Job? {
        val verified = mutableState.value as? UpdateState.Verified ?: return null
        mutableState.value = UpdateState.Installing(verified.candidate)
        return scope.launch {
            try {
                withContext(io) { installer.install(verified.file) }
                log("installer session committed for ${verified.candidate.tag}")
            } catch (e: IOException) {
                log("installer session failed: ${e.javaClass.simpleName} ${e.message}")
                verified.file.delete()
                mutableState.value = UpdateState.Failed(verified.candidate, UpdateFailure.INSTALL)
            }
        }
    }

    /** The system installer finished the session (from the install result receiver). */
    fun onInstallResult(outcome: InstallOutcome) {
        val installing = mutableState.value as? UpdateState.Installing ?: return
        log("install result: $outcome")
        File(cacheDir, "updates").deleteRecursively()
        mutableState.value = when (outcome) {
            InstallOutcome.SUCCESS -> UpdateState.Idle
            InstallOutcome.ABORTED -> UpdateState.Offer(installing.candidate)
            InstallOutcome.SIGNATURE -> UpdateState.Failed(installing.candidate, UpdateFailure.SIGNATURE)
            InstallOutcome.FAILED -> UpdateState.Failed(installing.candidate, UpdateFailure.INSTALL)
        }
    }

    private fun UpdateState.candidate(): UpdateCandidate? = when (this) {
        UpdateState.Idle -> null
        is UpdateState.Offer -> candidate
        is UpdateState.NeedsPermission -> candidate
        is UpdateState.Downloading -> candidate
        is UpdateState.Verified -> candidate
        is UpdateState.Installing -> candidate
        is UpdateState.Failed -> candidate
    }

    private fun UpdateState.busy() = this is UpdateState.Downloading || this is UpdateState.Verified || this is UpdateState.Installing

    companion object {
        private val SAFE_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}\\.apk")
    }
}
