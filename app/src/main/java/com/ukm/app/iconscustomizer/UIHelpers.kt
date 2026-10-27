package com.ukm.app.iconscustomizer

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.edit
import com.ukm.app.iconscustomizer.MainActivity.Companion.PREF_NAME
import java.util.concurrent.TimeUnit

object UIHelpers {

    data class LauncherInfo(val packageName: String, val label: String) {
        val isSupported get() = packageName in Constants.LAUNCHER_PACKAGES
    }

    /** Asks the hooked launcher (HyperOS or POCO) to restart itself; no root needed. */
    fun restartLauncher(context: Context): Boolean {
        return try {
            for (pkg in Constants.LAUNCHER_PACKAGES) {
                context.sendBroadcast(Intent(Constants.ACTION_RELOAD).setPackage(pkg))
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** Blocking; call off the main thread. Falls back to the module broadcast without root. */
    fun forceRestartLauncher(context: Context): Boolean {
        val pkg = getDefaultLauncher(context)?.packageName?.takeIf { it in Constants.LAUNCHER_PACKAGES }
        val targets = pkg?.let { listOf(it) } ?: Constants.LAUNCHER_PACKAGES
        if (runAsRoot(targets.joinToString(" ; ") { "am force-stop $it" })) return true
        return restartLauncher(context)
    }

    /** Blocking; call off the main thread. SystemUI is restarted by the system after it dies. */
    fun restartSystemUi(): Boolean =
        runAsRoot("killall com.android.systemui || kill -9 \$(pidof com.android.systemui)")

    fun runAsRoot(command: String): Boolean {
        return try {
            val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
            process.inputStream.use { it.readBytes() }
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroy()
                return false
            }
            process.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }

    fun getSystemProperty(key: String): String? {
        return try {
            val process = ProcessBuilder("getprop", key).start()
            val value = process.inputStream.bufferedReader().use { it.readText().trim() }
            process.waitFor(2, TimeUnit.SECONDS)
            value.ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }

    fun getDeviceName(): String {
        val marketName = getSystemProperty("ro.product.marketname")
            ?: getSystemProperty("ro.product.vendor.marketname")
            ?: "${Build.BRAND} ${Build.MODEL}"
        return "$marketName (${Build.DEVICE})"
    }

    /** Returns e.g. "OS3.0.4.0.WNPMIXM", or null on non-HyperOS systems. */
    fun getHyperOsVersion(): String? =
        getSystemProperty("ro.mi.os.version.incremental")
            ?: getSystemProperty("ro.mi.os.version.name")

    fun getHyperOsMajorVersion(): Int? {
        getSystemProperty("ro.mi.os.version.code")?.toIntOrNull()?.let { return it }
        val name = getSystemProperty("ro.mi.os.version.name") ?: return null
        return Regex("""OS(\d+)""").find(name)?.groupValues?.get(1)?.toIntOrNull()
    }

    fun getDefaultLauncher(context: Context): LauncherInfo? {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val info = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
            ?: return null
        // "android" means the resolver/chooser is the default, i.e. no default home is set.
        if (info.packageName == "android") return null
        return LauncherInfo(info.packageName, info.applicationInfo.loadLabel(pm).toString())
    }

    /** Writes a setting (LSPosed remote prefs + local mirror). */
    fun pushRemotePref(changedKey: String, newValue: Any): Boolean {
        com.ukm.app.iconscustomizer.ui.PrefStore.put(changedKey, newValue)
        return App.mService != null
    }

    /** Applies Settings.Global animation scales (percent values) through root. */
    fun setAnimationScales(values: List<Pair<String, Int>>): Boolean {
        val cmd = values.joinToString(" && ") { (name, percent) ->
            "settings put global $name ${String.format(java.util.Locale.US, "%.2f", percent / 100f)}"
        }
        return runAsRoot(cmd)
    }

    fun pushLocalPref(context: Context, changedKey: String, newValue: Any) {
        val localPrefs = context.getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        localPrefs.edit(commit = true) {
            when (newValue) {
                is String -> putString(changedKey, newValue.trim())
                is Boolean -> putBoolean(changedKey, newValue)
                is Int -> putInt(changedKey, newValue)
            }
        }
    }
}
