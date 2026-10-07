package com.project.vortex.client.overlay.manager

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.compositionContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.project.vortex.client.application.AppContext
import com.project.vortex.client.constructors.GameManager
import com.project.vortex.client.overlay.mods.DummyOverlay
import com.project.vortex.client.service.Services
import com.project.vortex.client.ui.theme.VortexClientTheme

import kotlinx.coroutines.launch

@SuppressLint("StaticFieldLeak")
@Suppress("MemberVisibilityCanBePrivate")
object OverlayManager {

    private val overlayWindows = ArrayList<OverlayWindow>()

    var currentContext: Context? = null
        private set

    var isShowing = false
        private set

    init {
        ensureWindows()
    }

    /**
     * Keeps the window list in sync with the current mode. Upstream only built this
     * list once in `init`, so a list captured before the game/modules were ready
     * could end up without the floating button - the overlay then did nothing.
     */
    private fun ensureWindows() {
        if (Services.RemisOnline) {
            overlayWindows.removeAll { it is OverlayButton }
            if (overlayWindows.none { it is DummyOverlay }) {
                overlayWindows.add(DummyOverlay())
            }
            return
        }

        overlayWindows.removeAll { it is DummyOverlay }

        if (overlayWindows.none { it is OverlayButton }) {
            overlayWindows.add(0, OverlayButton())
        }

        GameManager
            .elements
            .filter { it.isShortcutDisplayed }
            .map { it.overlayShortcutButton }
            .forEach { shortcut ->
                if (overlayWindows.none { it === shortcut }) {
                    overlayWindows.add(shortcut)
                }
            }
    }

    fun showOverlayWindow(overlayWindow: OverlayWindow) {
        overlayWindows.add(overlayWindow)

        val context = currentContext
        if (isShowing && context != null) {
            showOverlayWindow(context, overlayWindow)
        }
    }

    fun dismissOverlayWindow(overlayWindow: OverlayWindow) {
        overlayWindows.remove(overlayWindow)

        val context = currentContext
        if (isShowing && context != null) {
            dismissOverlayWindow(context, overlayWindow)
        }
    }

    fun show(context: Context) {
        currentContext = context

        if (!canDrawOverlays(context)) {
            requestOverlayPermission(context)
            return
        }

        ensureWindows()

        overlayWindows.forEach {
            showOverlayWindow(context, it)
        }

        isShowing = true
    }

    private fun canDrawOverlays(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    /**
     * Instead of silently doing nothing when "Display over other apps" is missing,
     * send the user to the exact settings page and say why.
     */
    private fun requestOverlayPermission(context: Context) {
        Log.w("OverlayManager", "Overlay permission missing - asking the user")
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (t: Throwable) {
            Log.e("OverlayManager", "Could not open overlay permission settings", t)
        }
        Toast.makeText(
            context,
            "Allow \"Display over other apps\" for Vortex Client so the menu can appear.",
            Toast.LENGTH_LONG
        ).show()
    }

    fun dismiss() {
        val context = currentContext
        if (context != null) {
            overlayWindows.forEach {
                dismissOverlayWindow(context, it)
            }
            isShowing = false
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    private fun showOverlayWindow(context: Context, overlayWindow: OverlayWindow) {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val layoutParams = overlayWindow.layoutParams
        val composeView = overlayWindow.composeView
        
        
        composeView.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.windowInsetsController?.hide(android.view.WindowInsets.Type.systemBars())
                view.windowInsetsController?.systemBarsBehavior = 
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                @Suppress("DEPRECATION")
                view.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            }
            insets
        }
        
        composeView.setContent {
            VortexClientTheme {
                CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                    overlayWindow.Content()
                }
            }
        }
        
        val lifecycleOwner = overlayWindow.lifecycleOwner
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        composeView.setViewTreeLifecycleOwner(lifecycleOwner)
        composeView.setViewTreeViewModelStoreOwner(object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore
                get() = overlayWindow.viewModelStore
        })
        composeView.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        composeView.compositionContext = overlayWindow.recomposer
        if (overlayWindow.firstRun) {
            overlayWindow.composeScope.launch {
                overlayWindow.recomposer.runRecomposeAndApplyChanges()
            }
            overlayWindow.firstRun = false
        }

        addViewWithRetry(windowManager, composeView, layoutParams, context)
    }

    /**
     * WindowManager can refuse the very first addView right after the permission is
     * granted (common on MIUI/ColorOS), so the call is retried a couple of times
     * instead of failing silently.
     */
    private fun addViewWithRetry(
        windowManager: WindowManager,
        composeView: View,
        layoutParams: WindowManager.LayoutParams,
        context: Context,
        attempt: Int = 0
    ) {
        if (composeView.parent != null) return

        try {
            windowManager.addView(composeView, layoutParams)
        } catch (e: Exception) {
            Log.e("OverlayManager", "Failed to add overlay window (attempt $attempt)", e)

            if (attempt < 2) {
                Handler(Looper.getMainLooper()).postDelayed({
                    addViewWithRetry(windowManager, composeView, layoutParams, context, attempt + 1)
                }, 500L * (attempt + 1))
            } else {
                val reason = when {
                    !canDrawOverlays(context) -> "\"Display over other apps\" permission is disabled"
                    else -> e.message ?: e.javaClass.simpleName
                }
                Toast.makeText(
                    context,
                    "Vortex overlay could not be shown: $reason",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun dismissOverlayWindow(context: Context, overlayWindow: OverlayWindow) {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val composeView = overlayWindow.composeView

        try {
            if (composeView.parent != null) {
                windowManager.removeView(composeView)
            }
        } catch (e: Exception) {
            Log.w("OverlayManager", "Failed to remove overlay window", e)
        }
    }

    fun showCustomOverlay(view: View) {
        val wm = AppContext.instance.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        wm.addView(view, params)
    }

    fun dismissCustomOverlay(view: View) {
        val wm = AppContext.instance.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.removeView(view)
    }


}