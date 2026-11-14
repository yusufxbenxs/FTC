package com.flyer.launcher

import android.app.Application
import android.content.Context
import com.flyer.launcher.core.LauncherCore

/** Process-wide owner of the [LauncherCore]; the launcher activity and Flyer share it. */
class FlyerApp : Application() {

    lateinit var core: LauncherCore
        private set

    override fun onCreate() {
        super.onCreate()
        core = LauncherCore(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::core.isInitialized) core.icons.trim(level)
    }

    companion object {
        fun core(context: Context): LauncherCore = (context.applicationContext as FlyerApp).core
    }
}
