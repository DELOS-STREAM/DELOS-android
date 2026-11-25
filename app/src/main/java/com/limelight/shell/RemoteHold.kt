package com.limelight.shell

import android.view.KeyEvent

/**
 * Holding the OK key (D-pad Center) of a TV remote: the press of X, for remotes without a Menu key. The activity feeds it every
 * event of that key before anything else sees it ([step]).
 *
 * The hold is the key's first repeat (Android repeats a held key after its long-press time). The first press passes on, so a
 * short press still activates the focused element on its release. From the first repeat on the key is held: X fires once, the
 * further repeats and the release are consumed, so the focused element is not activated as well.
 */
internal class RemoteHold {
    enum class Step { PASS, FIRE, CONSUME }

    private var held = false

    fun step(action: Int, repeatCount: Int): Step = when {
        action == KeyEvent.ACTION_DOWN && repeatCount == 0 -> { held = false; Step.PASS }
        action == KeyEvent.ACTION_DOWN -> if (held) Step.CONSUME else { held = true; Step.FIRE }
        action == KeyEvent.ACTION_UP && held -> { held = false; Step.CONSUME }
        else -> Step.PASS
    }
}
