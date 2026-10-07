package com.project.vortex.client.util

import android.app.Activity

/**
 * Update / integrity handshake.
 *
 * Upstream implementation fetched a JSON payload from a remote host, verified a
 * native signature and, whenever anything went wrong (dead server, no internet,
 * re-signed APK), showed a toast and called finishAffinity() - which closed the
 * app immediately after launch.
 *
 * For the Vortex build the check is disabled: the app always starts, offline or
 * online, without opening anything and without terminating itself.
 */
class UpdateCheck {

    fun initiateHandshake(context: Activity, allowOffline: Boolean = false) {
        // No network request, no toast, no finishAffinity().
    }
}
