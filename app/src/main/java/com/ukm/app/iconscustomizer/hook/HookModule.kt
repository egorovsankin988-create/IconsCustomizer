package com.ukm.app.iconscustomizer.hook

import android.content.SharedPreferences
import android.util.Log
import com.ukm.app.iconscustomizer.Constants
import com.ukm.app.iconscustomizer.Prefs
import com.ukm.app.iconscustomizer.XposedHelpers
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Executable
import java.lang.reflect.Method

/** Shared plumbing for the per-process hook modules. */
abstract class HookModule(
    protected val module: XposedModule,
    protected val prefs: SharedPreferences?,
    protected val target: String,
) {
    protected fun bool(key: String) = Prefs.bool(prefs, key)
    protected fun int(key: String) = Prefs.int(prefs, key)
    protected fun string(key: String) = Prefs.string(prefs, key)

    protected fun warn(msg: String) = module.log(Log.WARN, Constants.TAG, msg)
    protected fun info(msg: String) = module.log(Log.INFO, Constants.TAG, msg)

    /** Each feature is isolated so a class missing on one ROM never disables the others. */
    protected inline fun safeHook(name: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            warn("Hook '$name' unavailable on $target: $t")
        }
    }

    protected fun findClassOrNull(cl: ClassLoader, vararg names: String): Class<*>? {
        for (name in names) {
            try {
                return XposedHelpers.findClass(name, cl)
            } catch (_: Throwable) {
            }
        }
        return null
    }

    protected fun findClassAny(cl: ClassLoader, vararg names: String): Class<*> =
        findClassOrNull(cl, *names) ?: throw ClassNotFoundException(names.joinToString(" | "))

    /** Exact signature first, then the first declared method with that name and arity. */
    protected fun findMethodLoose(clazz: Class<*>, name: String, vararg params: Class<*>): Method {
        XposedHelpers.findMethodExactIfExists(clazz, name, *params)?.let { return it }
        return findMethodByName(clazz, name, params.size)
    }

    protected fun findMethodByName(clazz: Class<*>, name: String, arity: Int = -1): Method {
        var current: Class<*>? = clazz
        while (current != null) {
            current.declaredMethods.firstOrNull {
                it.name == name && (arity < 0 || it.parameterTypes.size == arity)
            }?.let {
                it.isAccessible = true
                return it
            }
            current = current.superclass
        }
        throw NoSuchMethodException("${clazz.name}#$name/$arity")
    }

    protected fun hook(method: Executable, block: (XposedInterface.Chain) -> Any?) {
        module.hook(method).intercept { chain -> block(chain) }
    }

    /** Runs the original, then [block] with the result; [block]'s return value is used. */
    protected fun after(method: Executable, block: (XposedInterface.Chain, Any?) -> Any?) {
        hook(method) { chain -> block(chain, chain.proceed()) }
    }

    /** Replaces the first argument before the original runs. */
    protected fun replaceFirstArg(method: Executable, map: (Any?) -> Any?) {
        hook(method) { chain ->
            val args = chain.args.toTypedArray()
            if (args.isNotEmpty()) args[0] = map(args[0])
            chain.proceed(args)
        }
    }

    /**
     * Makes a no-arg method return [value] in every class that exists. Values are coerced to
     * the method's real return type so a mismatched guess never crashes the target process.
     */
    protected fun returnConstant(cl: ClassLoader, classes: List<String>, method: String, value: Any): Boolean {
        var hooked = false
        for (cls in classes) {
            val clazz = findClassOrNull(cl, cls) ?: continue
            val m = XposedHelpers.findMethodExactIfExists(clazz, method) ?: continue
            val coerced = coerce(value, m.returnType) ?: continue
            safeHook("$cls#$method") {
                hook(m) { coerced }
                hooked = true
            }
        }
        return hooked
    }

    protected fun coerce(value: Any, type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE, java.lang.Boolean::class.java -> value as? Boolean
        java.lang.Integer.TYPE, java.lang.Integer::class.java -> (value as? Number)?.toInt()
        java.lang.Float.TYPE, java.lang.Float::class.java -> (value as? Number)?.toFloat()
        java.lang.Long.TYPE, java.lang.Long::class.java -> (value as? Number)?.toLong()
        java.lang.Double.TYPE, java.lang.Double::class.java -> (value as? Number)?.toDouble()
        else -> null
    }

    protected fun setFieldNumber(obj: Any, field: String, value: Number) {
        val f = XposedHelpers.findField(obj.javaClass, field)
        when (f.type) {
            java.lang.Float.TYPE -> f.setFloat(obj, value.toFloat())
            java.lang.Integer.TYPE -> f.setInt(obj, value.toInt())
            java.lang.Double.TYPE -> f.setDouble(obj, value.toDouble())
            else -> f.set(obj, coerce(value, f.type) ?: value)
        }
    }
}
