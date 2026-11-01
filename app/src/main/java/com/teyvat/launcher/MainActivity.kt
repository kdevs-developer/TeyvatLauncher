package com.teyvat.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    /** Bumped when apps are installed / removed / opened -> the list reloads. */
    private val refresh = mutableIntStateOf(0)

    /** Bumped every time the launcher comes back to the front -> entrance animations replay. */
    private val enterTick = mutableIntStateOf(0)

    /** Bumped when Home is pressed while the launcher is already showing -> jump back to the start. */
    private val homeTick = mutableIntStateOf(0)

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refresh.intValue++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Back is handled inside the UI (HomeScreen): it returns to the start of the row.
        // A launcher must never "go back" out of itself.
        setContent { HomeRoot(refresh.intValue, enterTick.intValue, homeTick.intValue) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homeTick.intValue++
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(packageReceiver)
    }

    override fun onResume() {
        super.onResume()
        refresh.intValue++
        enterTick.intValue++
    }
}
