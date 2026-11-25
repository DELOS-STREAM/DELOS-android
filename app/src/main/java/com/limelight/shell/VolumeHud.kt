package com.limelight.shell

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.animation.PathInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.limelight.binding.audio.VolumeController
import com.limelight.shell.settings.Appearance
import com.limelight.shell.settings.AppearanceStore
import com.limelight.shell.data.ProfileStore
import com.limelight.shell.ui.*
import java.util.function.BooleanSupplier
import java.util.function.Supplier
import java.util.WeakHashMap
import kotlin.math.min
import kotlin.math.roundToInt

/** One shared display-only HUD implementation for shell and stream; its small real window can blur SurfaceView video. */
class VolumeHud(
    private val activity: ComponentActivity,
    private val appearance: Supplier<Appearance>,
    private val keepOnDevice: BooleanSupplier,
) : DefaultLifecycleObserver {
    private val volume = VolumeController.get(activity)
    private val main = Handler(Looper.getMainLooper())
    private var dialog: ComponentDialog? = null
    private var fade: ObjectAnimator? = null
    private var stageScale = 1f
    private var compose: ComposeView? = null
    private val hideLater = Runnable { hide() }
    private val easeOut = PathInterpolator(.16f, 1f, .3f, 1f)
    private val easeIn = PathInterpolator(.5f, 0f, .75f, 0f)

    init {
        volume.keepSoundOnDevice(keepOnDevice.asBoolean)
        hosts[activity] = this
        activity.lifecycle.addObserver(this)
    }

    override fun onResume(owner: LifecycleOwner) { volume.keepSoundOnDevice(keepOnDevice.asBoolean) }
    override fun onStop(owner: LifecycleOwner) { dismiss() }
    override fun onPause(owner: LifecycleOwner) { dismiss() }
    override fun onDestroy(owner: LifecycleOwner) {
        dismiss()
        hosts.remove(activity)
        activity.lifecycle.removeObserver(this)
    }

    fun handle(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP && event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN &&
            event.keyCode != KeyEvent.KEYCODE_VOLUME_MUTE) return false
        volume.keepSoundOnDevice(keepOnDevice.asBoolean)
        val press = volume.state.value.press
        if (!volume.key(event)) return false
        if (volume.state.value.press != press) show()
        return true
    }

    private fun show() {
        main.removeCallbacks(hideLater)
        val look = appearance.get()
        val decor = activity.window.decorView
        val density = activity.resources.displayMetrics.density
        val width = decor.width.takeIf { it > 0 } ?: activity.resources.displayMetrics.widthPixels
        val height = decor.height.takeIf { it > 0 } ?: activity.resources.displayMetrics.heightPixels
        val plan = StagePlanner.plan(width / density, height / density)
        val metrics = StageMetrics(plan.dpPerPx, look.uiScale / 100f, plan.stageWidth, plan.stageHeight, plan.shape)
        val scale = metrics.dpPerPx * density
        val side = (248 * scale).roundToInt()
        stageScale = scale
        if (dialog == null) {
            val d = ComponentDialog(activity, android.R.style.Theme_Translucent_NoTitleBar)
            val view = ComposeView(activity).apply {
                isFocusable = false
                isFocusableInTouchMode = false
                isSoundEffectsEnabled = false
                importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
            val initial = volume.state.value.previousLevel
            view.setContent {
                val state by volume.state.collectAsState()
                CompositionLocalProvider(LocalStage provides metrics,
                    LocalReducedMotion provides look.reducedMotion) {
                    VolumeDial(state.level, initial, state.route)
                }
            }
            d.setContentView(view)
            val window = requireNotNull(d.window)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.setBackgroundDrawable(GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xB81E2A3F.toInt(), 0xB80A111D.toInt())).apply { shape = GradientDrawable.OVAL })
            window.attributes = window.attributes.apply {
                gravity = Gravity.TOP or Gravity.LEFT
                windowAnimations = 0
                dimAmount = 0f
                if (Build.VERSION.SDK_INT >= 30) fitInsetsTypes = 0
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            dialog = d
            compose = view
            d.show()
            window.setLayout(side, side)
            if (Build.VERSION.SDK_INT >= 31) window.setBackgroundBlurRadius((8 * scale).roundToInt())
            window.decorView.elevation = 50 * scale
            window.decorView.setOutlineAmbientShadowColor(0x55000000)
            window.decorView.setOutlineSpotShadowColor(0x55000000)
            window.decorView.alpha = if (look.reducedMotion) 1f else 0f
            window.decorView.translationY = if (look.reducedMotion) 0f else 16 * scale
        }
        val window = requireNotNull(dialog?.window)
        val safe = ViewCompat.getRootWindowInsets(decor)?.getInsets(WindowInsetsCompat.Type.displayCutout())
        val centreY = min(height * .8f, height - (safe?.bottom ?: 0) - (124 + 92) * scale)
        window.attributes = window.attributes.apply {
            x = (width - side) / 2
            y = (centreY - side / 2f).roundToInt()
        }
        val root = window.decorView
        root.animate().setListener(null).cancel()
        fade?.cancel()
        if (look.reducedMotion) {
            root.alpha = 1f
            root.translationY = 0f
        } else {
            root.animate().translationY(0f).setDuration(Motion.ENTER.toLong()).setInterpolator(easeOut).start()
            fade = ObjectAnimator.ofFloat(root, "alpha", root.alpha, 1f).apply {
                duration = Motion.FADE.toLong()
                interpolator = easeOut
                start()
            }
        }
        main.postDelayed(hideLater, 1500)
    }

    private fun hide() {
        val root = dialog?.window?.decorView ?: return
        if (appearance.get().reducedMotion) { dismiss(); return }
        fade?.cancel()
        root.animate().alpha(0f).translationY(16 * stageScale)
            .setDuration(Motion.EXIT.toLong()).setInterpolator(easeIn)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { dismiss() }
            }).start()
    }

    private fun dismiss() {
        main.removeCallbacks(hideLater)
        fade?.cancel()
        dialog?.window?.decorView?.animate()?.setListener(null)?.cancel()
        dialog?.dismiss()
        compose?.disposeComposition()
        dialog = null
        compose = null
    }
    companion object {
        private val hosts = WeakHashMap<Activity, VolumeHud>()

        @JvmStatic
        fun attach(activity: ComponentActivity, keepOnDevice: BooleanSupplier): VolumeHud {
            val store = AppearanceStore { ProfileStore.currentSettings(activity) }
            return VolumeHud(activity, Supplier { store.appearance.value }, keepOnDevice)
        }

        /** Dialogs that own input forward only their hardware volume keys to the same host. */
        @JvmStatic
        fun dispatch(activity: Activity, event: KeyEvent): Boolean = hosts[activity]?.handle(event) ?: false
    }
}
