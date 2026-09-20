package com.skharma.casioclock

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

/**
 * Full-screen "wall clock" host for [NixieClockView]. Keeps the screen on,
 * hides the system bars, and re-hides them whenever the user pokes the edges
 * of the screen to peek at the system UI.
 */
class MainActivity : Activity() {

    private lateinit var watchFace: NixieClockView
    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

    private val tickRunnable = object : Runnable {
        override fun run() {
            watchFace.tick()
            val delay = 1000 - (System.currentTimeMillis() % 1000)
            handler.postDelayed(this, delay)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        prefs = getSharedPreferences("casio_clock", Context.MODE_PRIVATE)

        watchFace = NixieClockView(this)
        watchFace.use24Hour = prefs.getBoolean("use24Hour", true)
        watchFace.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                watchFace.use24Hour = !watchFace.use24Hour
                prefs.edit().putBoolean("use24Hour", watchFace.use24Hour).apply()
            }
            true
        }
        setContentView(watchFace)

        hideSystemBars()
    }

    override fun onResume() {
        super.onResume()
        handler.post(tickRunnable)
        hideSystemBars()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tickRunnable)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(android.view.WindowInsets.Type.systemBars())
                controller.systemBarsBehavior =
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        }
    }
}
