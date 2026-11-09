/*
 * Podroid - Rootless Podman for Android
 * Copyright (C) 2024-2026 Podroid contributors
 *
 * A tiny always-on-top dot shown while the VM runs (docker-only fork keep-alive).
 */
package com.excp.podroid.service

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView

/**
 * Keep-alive, not decoration: several OEM ROMs (MIUI/HyperOS among them) apply a
 * background CPU quota to any app that is not foreground-visible — measured at
 * ~1/20 of normal CPU once the activity is backgrounded, which starves the QEMU
 * process and makes docker/SSH/port forwards unreachable. A
 * TYPE_APPLICATION_OVERLAY window keeps the process in a visible/perceptible
 * state, which exempts it from that quota tier without touching any ROM setting.
 * The dot is the only touch target (tap opens the app); touches elsewhere pass
 * straight through.
 */
class FloatingBall(private val context: Context) {

    private var view: View? = null

    fun canShow(): Boolean = Settings.canDrawOverlays(context)

    /** Idempotent; call from a thread with a main looper (WindowManager requires one). */
    fun show() {
        if (view != null || !canShow()) return
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dot = ImageView(context).apply {
            setImageDrawable(GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xDD2196F3.toInt())
                setStroke(dp(1.5f), Color.WHITE)
            })
            contentDescription = "Podroid keep-alive"
        }
        val params = WindowManager.LayoutParams(
            dp(20f), dp(20f),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        dot.setOnClickListener {
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launch ->
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                runCatching { context.startActivity(launch) }
            }
        }
        runCatching { wm.addView(dot, params) }
            .onSuccess { view = dot }
            .onFailure { Log.w(TAG, "addView failed", it) }
    }

    /** Idempotent and safe to call from any teardown path (never throws). */
    fun hide() {
        val v = view ?: return
        view = null
        runCatching {
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v)
        }
    }

    private fun dp(v: Float): Int =
        (v * context.resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private const val TAG = "FloatingBall"
    }
}
