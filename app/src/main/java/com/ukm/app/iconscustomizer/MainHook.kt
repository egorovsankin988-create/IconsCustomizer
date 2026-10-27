package com.ukm.app.iconscustomizer

import android.app.Application
import android.app.Instrumentation
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Process
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import org.w3c.dom.Element
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import android.R as Resources


class MainHook : XposedModule() {

    companion object {
        const val TAG = "UKMTAG"
        const val ACTION_RELOAD = "com.ukm.app.RELOAD_ICONS"

        /** HyperOS system launcher (CN + most global ROMs) and the POCO launcher. */
        val LAUNCHER_PACKAGES = listOf("com.miui.home", "com.mi.android.globallauncher")
    }

    private var launcherPackage = ""
    private var prefManager: SharedPreferences? = null
    private var launcherContext: Context? = null
    private val resolvedCache = ConcurrentHashMap<String, String>()
    private var iconPackPackageName: String = "none"
    private var themeHomeScreenOnly: Boolean = false
    private var isThemedIconEnabled: Boolean = false
    private var isThemedClockEnabled: Boolean = false
    private var isThemeDockFolderEnabled: Boolean = false
    private var dockFolderBgColor: Int = 0
    private var clockWidgetColor: Int = 0
    private var isDockEnabled: Boolean = false
    private var dockFolderOpacity = 200
    private var dockCornerRadius = 60
    private var iconSize: Int = 180

    // The first onWallpaperColorChanged after process start is the launcher's own init
    // call; only later ones are real palette changes.
    @Volatile private var launcherColorCallbackSeen = false
    @Volatile private var dockColorCallbackSeen = false

    private val iconPackActive get() = isThemedIconEnabled && iconPackPackageName != "none"

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        super.onModuleLoaded(param)
        prefManager = getRemotePreferences(MainActivity.PREF_NAME)
    }

    override fun onPackageLoaded(packageParam: XposedModuleInterface.PackageLoadedParam) {
        super.onPackageLoaded(packageParam)
        if (packageParam.packageName !in LAUNCHER_PACKAGES || !packageParam.isFirstPackage) return
        launcherPackage = packageParam.packageName

        val prefs = prefManager
        isThemedIconEnabled = prefs?.getBoolean("enable_themed_icons", false) ?: false
        isDockEnabled = prefs?.getBoolean("enable_dock", false) ?: false
        themeHomeScreenOnly = prefs?.getBoolean("themed_icons_homescreen_only", false) ?: false
        isThemeDockFolderEnabled = prefs?.getBoolean("theme_dock_folder", false) ?: false
        isThemedClockEnabled = prefs?.getBoolean("enable_themed_clock", false) ?: false
        iconPackPackageName = prefs?.getString("icon_pack", "none") ?: "none"
        dockCornerRadius = prefs?.getInt("dock_corner_radius", 60) ?: 60
        if (isThemeDockFolderEnabled) {
            dockFolderBgColor = prefs?.getInt("monet_folder_dock_bg_color", 0) ?: 0
            dockFolderOpacity = prefs?.getInt("dock_folder_opacity", 200) ?: 200
        }
        if (isThemedClockEnabled) {
            clockWidgetColor = prefs?.getInt("monet_clock_color", 0) ?: 0
        }
        iconSize = prefs?.getInt("icon_size", 180) ?: 180

        log(Log.INFO, TAG, "Hooking $launcherPackage (${android.os.Build.MODEL}, SDK ${android.os.Build.VERSION.SDK_INT})")
        hookLauncher(packageParam.defaultClassLoader)
    }

    /**
     * Every hook is isolated: launcher builds differ between HyperOS versions, CN/global ROMs
     * and the POCO launcher, so a class missing on one device must not disable the others.
     */
    private fun hookLauncher(classLoader: ClassLoader) {
        safeHook("launcherContext") { setLauncherContext() }
        safeHook("iconSize") { iconSizeHook(classLoader) }
        if (isThemedClockEnabled) safeHook("clock") { launcherClockHook(classLoader) }
        if (isThemeDockFolderEnabled) safeHook("folder") { folderHook(classLoader) }
        if (isDockEnabled) dockHook(classLoader)
        if (iconPackActive) {
            safeHook("iconPack") { iconPackHook(classLoader) }
            safeHook("defaultIconFlag") { defaultIconFlagHook(classLoader) }
        }
        safeHook("wallpaperColor") { wallpaperColorChangedHook(classLoader) }
    }

    private inline fun safeHook(name: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            log(Log.WARN, TAG, "Hook '$name' unavailable on $launcherPackage: $t")
        }
    }

    private fun findClassAny(classLoader: ClassLoader, vararg names: String): Class<*> {
        for (name in names) {
            try {
                return XposedHelpers.findClass(name, classLoader)
            } catch (_: Throwable) {
            }
        }
        throw ClassNotFoundException(names.joinToString(" | "))
    }

    /** Exact match first, then the single declared method with that name and arity. */
    private fun findMethodLoose(clazz: Class<*>, name: String, vararg params: Class<*>): Method {
        XposedHelpers.findMethodExactIfExists(clazz, name, *params)?.let { return it }
        var current: Class<*>? = clazz
        while (current != null) {
            current.declaredMethods.firstOrNull {
                it.name == name && it.parameterTypes.size == params.size
            }?.let {
                it.isAccessible = true
                return it
            }
            current = current.superclass
        }
        throw NoSuchMethodException("${clazz.name}#$name/${params.size}")
    }

    private fun defaultIconFlagHook(classLoader: ClassLoader) {
        val deviceConfigsClass = findClassAny(
            classLoader,
            "com.miui.home.common.device.DeviceConfigs",
            "com.miui.home.launcher.DeviceConfig"
        )
        val method = findMethodLoose(deviceConfigsClass, "updateIsDefaultIcon", Context::class.java)
        hook(method).intercept { chain ->
            val result = chain.proceed()
            if (iconPackActive) {
                XposedHelpers.setStaticBooleanField(deviceConfigsClass, "sIsDefaultIcon", false)
            }
            result
        }
    }

    private fun wallpaperColorChangedHook(classLoader: ClassLoader) {
        val baseLauncherClass = findClassAny(
            classLoader,
            "com.miui.home.launcher.BaseLauncher",
            "com.miui.home.launcher.Launcher"
        )
        val method = XposedHelpers.findMethodExact(baseLauncherClass, "onWallpaperColorChanged")
        hook(method).intercept { chain ->
            val result = chain.proceed()
            if (!launcherColorCallbackSeen) {
                launcherColorCallbackSeen = true
                return@intercept result
            }
            val baseLauncher = chain.thisObject ?: return@intercept result
            if (iconPackActive) {
                try {
                    XposedHelpers.callMethod(baseLauncher, "refreshAllAppsIcon")
                } catch (t: Throwable) {
                    log(Log.WARN, TAG, "refreshAllAppsIcon failed: $t")
                }
            }
            if (isThemedClockEnabled) {
                refreshMaMlWidgets(baseLauncher)
                refreshGadgets(baseLauncher)
            }
            result
        }
    }

    private fun launcherClockHook(classLoader: ClassLoader) {
        val factoryClass =
            XposedHelpers.findClass("com.miui.maml.elements.ScreenElementFactory", classLoader)
        val rootClass = XposedHelpers.findClass("com.miui.maml.ScreenElementRoot", classLoader)
        val createInstanceMethod = XposedHelpers.findMethodExact(
            factoryClass,
            "createInstance",
            Element::class.java,
            rootClass
        )
        hook(createInstanceMethod).intercept { param ->
            val domElement = param.args[0] as? Element ?: return@intercept param.proceed()
            val tagName = domElement.tagName
            if (tagName == "DateTime" || tagName == "Time" || tagName == "Text") {
                try {
                    val ctx = launcherContext ?: return@intercept param.proceed()
                    val color = ctx.getColor(
                        if (clockWidgetColor != 0) clockWidgetColor else Resources.color.system_accent1_200
                    )
                    domElement.removeAttribute("colorExp")
                    domElement.removeAttribute("colorLight")
                    domElement.removeAttribute("colorDark")
                    domElement.setAttribute("color", String.format("#%08X", color))
                } catch (e: Exception) {
                    Log.e(TAG, "launcherClockHook: ", e)
                }
            }
            param.proceed()
        }
    }

    private fun folderHook(classLoader: ClassLoader) {
        val clazz = findClassAny(
            classLoader,
            "com.miui.home.model.core.IconCache",
            "com.miui.home.launcher.IconCache"
        )
        val getDrawableMethod = XposedHelpers.findMethodExact(clazz, "getDrawable", Int::class.java)

        hook(getDrawableMethod).intercept { chain ->
            val id = chain.args[0] as Int
            val ctx = launcherContext
            if (ctx != null && (id == 4097 || id == 4098 || id == 4099 || id == 4100 || id == 4104)) {
                return@intercept GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(
                        ContextCompat.getColor(
                            ctx,
                            if (dockFolderBgColor != 0) dockFolderBgColor else Resources.color.system_accent1_600
                        )
                    )
                    alpha = dockFolderOpacity
                    cornerRadius = 66f
                }
            }
            chain.proceed()
        }
    }

    private fun dockHook(classLoader: ClassLoader) {
        safeHook("dockMeasure") {
            val hotSeatsClass = findClassAny(
                classLoader,
                "com.miui.home.launcher.hotseats.HotSeats",
                "com.miui.home.launcher.HotSeats"
            )
            val onMeasure = XposedHelpers.findMethodExact(
                hotSeatsClass, "onMeasure", Int::class.java, Int::class.java
            )
            hook(onMeasure).intercept { chain ->
                val result = chain.proceed()
                val hotSeats = chain.thisObject as View
                (hotSeats.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                    val dynamicSide = maxOf(10, 160 - (iconSize / 2))
                    if (it.leftMargin != dynamicSide || it.rightMargin != dynamicSide) {
                        it.leftMargin = dynamicSide
                        it.rightMargin = dynamicSide
                        hotSeats.layoutParams = it
                    }
                }
                val dynamicVerticalPadding = maxOf(0, 120 - (iconSize / 2))
                if (hotSeats.paddingTop != dynamicVerticalPadding || hotSeats.paddingBottom != dynamicVerticalPadding) {
                    hotSeats.setPadding(
                        hotSeats.paddingLeft,
                        dynamicVerticalPadding,
                        hotSeats.paddingRight,
                        dynamicVerticalPadding
                    )
                }
                result
            }
        }

        val screenClass = try {
            findClassAny(
                classLoader,
                "com.miui.home.launcher.hotseats.HotSeatsScreenViewContent",
                "com.miui.home.launcher.HotSeatsScreenViewContent"
            )
        } catch (t: Throwable) {
            log(Log.WARN, TAG, "Dock background unavailable on $launcherPackage: $t")
            return
        }

        safeHook("dockInflate") {
            hook(XposedHelpers.findMethodExact(screenClass, "onFinishInflate")).intercept { chain ->
                val result = chain.proceed()
                applyPillStyle(chain.thisObject as? View)
                result
            }
        }
        safeHook("dockWallpaperColor") {
            hook(XposedHelpers.findMethodExact(screenClass, "onWallpaperColorChanged")).intercept { chain ->
                val result = chain.proceed()
                if (dockColorCallbackSeen) {
                    applyPillStyle(chain.thisObject as? View)
                }
                dockColorCallbackSeen = true
                result
            }
        }
    }

    private fun applyPillStyle(view: View?) {
        if (view == null) return
        val ctx = launcherContext ?: view.context
        val pill = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dockCornerRadius.toFloat()
        }
        changeDockBackground(ctx, pill)
        view.background = pill
        view.clipToOutline = true
    }

    private fun refreshGadgets(baseLauncher: Any) {
        try {
            val gadgets = XposedHelpers.getObjectField(baseLauncher, "mGadgets") as? ArrayList<*>
            gadgets?.forEach { gadget ->
                gadget ?: return@forEach
                val gadgetInfo = XposedHelpers.callMethod(gadget, "getTag") ?: return@forEach
                val id = XposedHelpers.getObjectField(gadgetInfo, "mGadgetId") as? Int
                    ?: return@forEach
                try {
                    XposedHelpers.callMethod(
                        baseLauncher,
                        "reloadGadget",
                        arrayOf(Int::class.java),
                        id
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "refreshGadgets: ", e)
                }
            }
        } catch (e: Throwable) {
            log(Log.ERROR, TAG, "Error refreshing gadgets: ${e.message}")
        }
    }

    private fun refreshMaMlWidgets(baseLauncher: Any) {
        try {
            try {
                val mamlUtilsClass = XposedHelpers.findClass(
                    "com.miui.launcher.utils.MamlUtils",
                    baseLauncher.javaClass.classLoader
                )
                XposedHelpers.callStaticMethod(mamlUtilsClass, "clearMamlCache")
            } catch (e: Throwable) {
                Log.e(TAG, "Could not clear MAML cache", e)
            }
            val mMaMlViews =
                XposedHelpers.getObjectField(baseLauncher, "mMaMlViews") as? ArrayList<*>
            mMaMlViews?.forEach { widget ->
                if (widget != null) {
                    try {
                        XposedHelpers.setBooleanField(widget, "mThemeApplied", false)
                        XposedHelpers.callMethod(
                            widget,
                            "onUpgrade",
                            arrayOf(Int::class.java, Int::class.java),
                            0, 0
                        )
                    } catch (innerE: Exception) {
                        Log.e(TAG, "Failed to force rebuild MAML widget", innerE)
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error fetching mMaMlViews: ${e.message}")
        }
    }

    private fun changeDockBackground(ctx: Context, drawable: GradientDrawable) {
        val isDarkModeEnabled =
            (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        if (!isThemeDockFolderEnabled) {
            drawable.setColor((if (isDarkModeEnabled) "#40000000" else "#80FFFFFF").toColorInt())
        } else {
            drawable.setColor(
                ContextCompat.getColor(
                    ctx,
                    if (dockFolderBgColor != 0) dockFolderBgColor else Resources.color.system_accent1_600
                )
            )
            drawable.alpha = dockFolderOpacity
        }
    }

    private fun iconSizeHook(classLoader: ClassLoader) {
        val iconConfigClass = findClassAny(
            classLoader,
            $$"com.miui.home.common.gridconfig.GridConfig$IconConfig",
            $$"com.miui.home.launcher.gridconfig.GridConfig$IconConfig"
        )
        val getIconSizeMethod = XposedHelpers.findMethodExact(iconConfigClass, "getIconSize")
        hook(getIconSizeMethod).intercept { _ -> iconSize }
    }

    /**
     * Hooks the framework entry point instead of the launcher's own Application subclass, whose
     * name differs between the HyperOS launcher and the POCO launcher.
     */
    private fun setLauncherContext() {
        val method = Instrumentation::class.java.getDeclaredMethod(
            "callApplicationOnCreate",
            Application::class.java
        )
        hook(method).intercept { chain ->
            val result = chain.proceed()
            val app = chain.args[0] as? Application
            if (launcherContext == null && app != null && app.packageName == launcherPackage) {
                try {
                    launcherContext = app
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(ctx: Context, intent: Intent) {
                            if (intent.action == ACTION_RELOAD) {
                                Log.i(TAG, "Reload broadcast received, restarting $launcherPackage")
                                Process.killProcess(Process.myPid())
                            }
                        }
                    }
                    app.registerReceiver(
                        receiver,
                        IntentFilter(ACTION_RELOAD),
                        Context.RECEIVER_EXPORTED
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error:", e)
                }
            }
            result
        }
    }

    private fun iconPackHook(classLoader: ClassLoader) {
        val drawableInfoClass =
            XposedHelpers.findClass("com.miui.home.model.core.DrawableInfo", classLoader)
        val itemInfoClass = XposedHelpers.findClassIfExists(
            "com.miui.home.model.api.ItemInfoWithIconAndMessage",
            classLoader
        )
        val applyTo = if (itemInfoClass != null) {
            findMethodLoose(drawableInfoClass, "applyTo", itemInfoClass)
        } else {
            findMethodLoose(drawableInfoClass, "applyTo", Any::class.java)
        }

        hook(applyTo).intercept { chain ->
            try {
                val itemInfo = chain.args[0] ?: return@intercept chain.proceed()
                val iconType = XposedHelpers.callMethod(itemInfo, "getIconType") as? Int
                val isHomeScreenItem = itemInfo.javaClass.name == "com.miui.home.launcher.ShortcutInfo"
                val shouldThemeIcon = !themeHomeScreenOnly || isHomeScreenItem
                if (!shouldThemeIcon || iconType == 8) return@intercept chain.proceed()

                val originalIcon =
                    XposedHelpers.getObjectField(chain.thisObject, "icon") as? Drawable
                        ?: return@intercept chain.proceed()
                val componentName =
                    XposedHelpers.callMethod(itemInfo, "getComponentInfo") as? ComponentName
                        ?: return@intercept chain.proceed()
                val customIcon = getCustomIcon(componentName, originalIcon)
                val iconMask = XposedHelpers.getObjectField(chain.thisObject, "enableIconMask")
                try {
                    val setIconDrawable = XposedHelpers.findMethodExact(
                        itemInfo.javaClass, "setIconDrawable", Drawable::class.java
                    )
                    val setEnableIconMask = findMethodLoose(
                        itemInfo.javaClass, "setEnableIconMask", Int::class.java
                    )
                    setIconDrawable.invoke(itemInfo, customIcon)
                    setEnableIconMask.invoke(itemInfo, iconMask)
                } catch (_: Exception) {
                    return@intercept chain.proceed()
                }
                null
            } catch (e: Exception) {
                Log.e(TAG, "iconPackHook: ", e)
                chain.proceed()
            }
        }
    }

    private fun getCustomIcon(
        componentName: ComponentName,
        originalIcon: Drawable
    ): Drawable {
        val ctx = launcherContext ?: return originalIcon
        val exactComponentString =
            "ComponentInfo{${componentName.packageName}/${componentName.className}}"
        val manualOverrideKey = "custom_icon_${iconPackPackageName}_$exactComponentString"
        val manualDrawableName = prefManager?.getString(manualOverrideKey, null)

        if (!manualDrawableName.isNullOrEmpty()) {
            val manualCustomDrawable =
                IconPackHelper.loadIcon(ctx, iconPackPackageName, manualDrawableName)
            if (manualCustomDrawable != null) {
                manualCustomDrawable.bounds = originalIcon.bounds
                return getCustomColoredDrawableIcon(manualCustomDrawable)
            }
        }

        val appFilterMap = IconPackHelper.getAppFilterMap(ctx, iconPackPackageName)
        if (appFilterMap.isEmpty()) return originalIcon

        val drawableName = getBestMatchDrawable(componentName, appFilterMap) ?: return originalIcon
        val customDrawable = IconPackHelper.loadIcon(ctx, iconPackPackageName, drawableName)
            ?: return originalIcon
        customDrawable.bounds = originalIcon.bounds
        return getCustomColoredDrawableIcon(customDrawable)
    }

    private fun getCustomColoredDrawableIcon(icon: Drawable): Drawable {
        val isMonetEnabled = prefManager?.getBoolean("enable_monet_colors", false) ?: false
        val ctx = launcherContext
        if (!isMonetEnabled || ctx == null) return icon
        return try {
            val savedBgResId = prefManager?.getInt("monet_bg_color", 0) ?: 0
            val savedFgResId = prefManager?.getInt("monet_fg_color", 0) ?: 0
            val monetBackground = if (savedBgResId != 0) ContextCompat.getColor(ctx, savedBgResId) else null
            val monetForeground = if (savedFgResId != 0) ContextCompat.getColor(ctx, savedFgResId) else null
            IconPackHelper.putColorIntoDrawable(ctx, icon, monetBackground, monetForeground) ?: icon
        } catch (e: Exception) {
            Log.e(TAG, "Failed to theme custom icon", e)
            icon
        }
    }

    private fun getBestMatchDrawable(
        component: ComponentName,
        appFilterMap: Map<String, String>
    ): String? {
        val exactString = "ComponentInfo{${component.packageName}/${component.className}}"

        resolvedCache[exactString]?.let { return it.ifEmpty { null } }

        var match = appFilterMap[exactString]
        if (match == null) {
            val packagePrefix = "ComponentInfo{${component.packageName}/"
            match = appFilterMap.entries.firstOrNull { it.key.startsWith(packagePrefix) }?.value
        }
        resolvedCache[exactString] = match ?: ""
        return match
    }
}
