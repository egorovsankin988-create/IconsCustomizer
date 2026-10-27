package com.ukm.app.iconscustomizer.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.ukm.app.iconscustomizer.App
import com.ukm.app.iconscustomizer.MainActivity.Companion.PREF_NAME
import com.ukm.app.iconscustomizer.Prefs
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Reads from LSPosed remote preferences when the module service is connected, and always
 * mirrors writes into local preferences so the app stays usable (and settings are kept)
 * while the module is not active yet. The mirror is pushed to LSPosed once it connects.
 */
object PrefStore {

    private fun local(): SharedPreferences =
        App.instance.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private fun remote(): SharedPreferences? = try {
        App.mService?.getRemotePreferences(PREF_NAME)
    } catch (_: Throwable) {
        null
    }

    private fun source(): SharedPreferences = remote() ?: local()

    fun get(key: String): Any? {
        val def = Prefs.DEFAULTS[key]
        return when (def) {
            is Boolean -> Prefs.bool(source(), key)
            is Int -> Prefs.int(source(), key)
            is String -> Prefs.string(source(), key)
            else -> source().all[key]
        }
    }

    fun bool(key: String) = get(key) as? Boolean ?: false
    fun int(key: String) = get(key) as? Int ?: 0
    fun string(key: String) = get(key) as? String ?: ""

    fun put(key: String, value: Any?) {
        fun SharedPreferences.Editor.write() {
            when (value) {
                null -> remove(key)
                is Boolean -> putBoolean(key, value)
                is Int -> putInt(key, value)
                is String -> putString(key, value.trim())
            }
        }
        local().edit(commit = true) { write() }
        try {
            remote()?.edit(commit = true) { write() }
        } catch (_: Throwable) {
        }
    }

    fun isDefault(key: String) = get(key) == Prefs.DEFAULTS[key]

    /** Every stored key, including per-app icon overrides, for backups. */
    fun all(): Map<String, Any?> {
        val merged = local().all.toMutableMap()
        try {
            remote()?.all?.let { merged.putAll(it) }
        } catch (_: Throwable) {
        }
        return merged
    }

    /** Called when LSPosed connects: copy settings made while the module was inactive. */
    fun syncLocalToRemote() {
        val r = remote() ?: return
        val localValues = local().all
        if (localValues.isEmpty()) return
        try {
            r.edit(commit = true) {
                for ((k, v) in localValues) {
                    if (r.contains(k)) continue
                    when (v) {
                        is Boolean -> putBoolean(k, v)
                        is Int -> putInt(k, v)
                        is String -> putString(k, v)
                    }
                }
            }
        } catch (_: Throwable) {
        }
    }
}

/** Tracks which processes need a restart for pending changes to take effect. */
object PendingChanges {
    fun interface Listener {
        fun onPendingChanged(scopes: Set<Scope>)
    }

    private val scopes = mutableSetOf<Scope>()
    private val listeners = CopyOnWriteArraySet<Listener>()

    val current: Set<Scope> get() = synchronized(scopes) { scopes.toSet() }

    fun add(scope: Scope) {
        if (scope != Scope.LAUNCHER && scope != Scope.SYSTEMUI) return
        synchronized(scopes) { if (!scopes.add(scope)) return }
        notifyListeners()
    }

    fun remove(scope: Scope) {
        synchronized(scopes) { if (!scopes.remove(scope)) return }
        notifyListeners()
    }

    fun clear() {
        synchronized(scopes) { scopes.clear() }
        notifyListeners()
    }

    fun addListener(l: Listener) {
        listeners.add(l)
        l.onPendingChanged(current)
    }

    fun removeListener(l: Listener) = listeners.remove(l)

    private fun notifyListeners() {
        val snapshot = current
        listeners.forEach { it.onPendingChanged(snapshot) }
    }
}
