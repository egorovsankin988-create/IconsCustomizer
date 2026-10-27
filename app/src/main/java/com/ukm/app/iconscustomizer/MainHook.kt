package com.ukm.app.iconscustomizer

import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import com.ukm.app.iconscustomizer.hook.LauncherHooks
import com.ukm.app.iconscustomizer.hook.SystemUiHooks
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/** Entry point (META-INF/xposed/java_init.list). Dispatches to per-process hook modules. */
class MainHook : XposedModule() {

    companion object {
        const val TAG = Constants.TAG
        const val ACTION_RELOAD = Constants.ACTION_RELOAD
        val LAUNCHER_PACKAGES = Constants.LAUNCHER_PACKAGES
    }

    private var prefs: SharedPreferences? = null

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        super.onModuleLoaded(param)
        prefs = getRemotePreferences(MainActivity.PREF_NAME)
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        super.onPackageLoaded(param)
        if (!param.isFirstPackage) return
        val pkg = param.packageName
        try {
            when (pkg) {
                in LAUNCHER_PACKAGES -> {
                    log(Log.INFO, TAG, "HyperDesk: hooking $pkg (${Build.MODEL}, SDK ${Build.VERSION.SDK_INT})")
                    LauncherHooks(this, prefs, pkg).install(param.defaultClassLoader)
                }
                SystemUiHooks.SYSTEMUI -> SystemUiHooks(this, prefs).install(param.defaultClassLoader)
            }
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "HyperDesk: failed to hook $pkg", t)
        }
    }
}
