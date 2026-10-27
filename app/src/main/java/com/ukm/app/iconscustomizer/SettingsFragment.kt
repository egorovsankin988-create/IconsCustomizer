package com.ukm.app.iconscustomizer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.ukm.app.iconscustomizer.MainActivity.Companion.PREF_NAME
import io.github.libxposed.service.XposedService
import com.google.android.material.R as MaterialR

class SettingsFragment : Fragment(), App.ServiceStateListener {

    private var mService: XposedService? = null
    private var isUpdatingUI = false

    data class IconPackInfo(val name: String, val packageName: String)

    private var installedIconPacks = listOf<IconPackInfo>()
    private lateinit var switchEnableTheming: MaterialSwitch
    private lateinit var switchHomescreenOnly: MaterialSwitch
    private lateinit var switchMonet: MaterialSwitch
    private lateinit var switchEnableDock: MaterialSwitch
    private lateinit var switchEnableMonetDockFolder: MaterialSwitch
    private lateinit var switchMonetClock: MaterialSwitch
    private lateinit var sliderIconSize: Slider
    private lateinit var sliderDockCornerRadius: Slider
    private lateinit var sliderDockOpacity: Slider

    private val colorPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val colorId = result.data?.getIntExtra("RETURNED_COLOR_ID", 0) ?: 0
            val targetKey = result.data?.getStringExtra("RETURNED_TARGET_KEY")
            view?.let { v ->
                when (targetKey) {
                    "monet_fg_color" -> updateColorPreview(v, R.id.img_preview_fg, colorId)
                    "monet_bg_color" -> updateColorPreview(v, R.id.img_preview_bg, colorId)
                    "monet_folder_dock_bg_color" ->
                        updateColorPreview(v, R.id.img_preview_dock_bg, colorId)
                    "monet_clock_color" -> updateColorPreview(v, R.id.img_preview_clock, colorId)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? MainActivity)?.setScreenTitle(getString(R.string.app_name))
        installedIconPacks = getInstalledIconPacks(requireContext())
        setupInteractions(view)
        loadSystemInfo(view)
    }

    override fun onStart() {
        super.onStart()
        App.addServiceStateListener(this)
    }

    override fun onStop() {
        App.removeServiceStateListener(this)
        super.onStop()
    }

    override fun onServiceStateChanged(service: XposedService?) {
        Log.i(MainHook.TAG, "Service State Changed")
        this.mService = service
        activity?.runOnUiThread { applyServiceStateToUI(view) }
    }

    /** Whole row toggles the switch; the switch itself is not clickable. */
    private fun bindSwitchRow(
        view: View,
        rowId: Int,
        switch: MaterialSwitch,
        prefKey: String,
        refreshUi: Boolean
    ) {
        view.findViewById<View>(rowId).setOnClickListener { switch.toggle() }
        switch.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingUI || mService == null) return@setOnCheckedChangeListener
            UIHelpers.pushRemotePref(prefKey, isChecked)
            if (refreshUi) applyServiceStateToUI(view)
            UIHelpers.restartLauncher(requireContext())
        }
    }

    private fun bindSlider(view: View, slider: Slider, valueViewId: Int, prefKey: String) {
        val valueView = view.findViewById<TextView>(valueViewId)
        slider.setLabelFormatter { it.toInt().toString() }
        slider.addOnChangeListener { _, value, fromUser ->
            valueView.text = value.toInt().toString()
            if (fromUser && mService != null) {
                UIHelpers.pushRemotePref(prefKey, value.toInt())
            }
        }
        slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                if (mService != null) UIHelpers.restartLauncher(requireContext())
            }
        })
    }

    private fun bindColorRow(view: View, rowId: Int, title: String, targetKey: String, typeKey: String) {
        view.findViewById<View>(rowId).setOnClickListener {
            val intent = Intent(requireContext(), ColorPickerActivity::class.java).apply {
                putExtra("EXTRA_TITLE", title)
                putExtra("EXTRA_TARGET_KEY", targetKey)
                putExtra("EXTRA_TYPE_KEY", typeKey)
            }
            colorPickerLauncher.launch(intent)
        }
    }

    private fun setupInteractions(view: View) {
        switchEnableTheming = view.findViewById(R.id.switch_enable_theming)
        switchHomescreenOnly = view.findViewById(R.id.switch_homescreen_only)
        switchMonet = view.findViewById(R.id.switch_enable_monet)
        switchEnableDock = view.findViewById(R.id.switch_enable_dock)
        switchEnableMonetDockFolder = view.findViewById(R.id.switch_monet_dock_folder)
        switchMonetClock = view.findViewById(R.id.switch_monet_clock)
        sliderIconSize = view.findViewById(R.id.slider_icon_size)
        sliderDockOpacity = view.findViewById(R.id.slider_dock_opacity)
        sliderDockCornerRadius = view.findViewById(R.id.slider_dock_corners)

        bindSwitchRow(view, R.id.row_enable_theming, switchEnableTheming, "enable_themed_icons", true)
        bindSwitchRow(view, R.id.row_homescreen_only, switchHomescreenOnly, "themed_icons_homescreen_only", false)
        bindSwitchRow(view, R.id.row_enable_monet, switchMonet, "enable_monet_colors", true)
        bindSwitchRow(view, R.id.row_enable_dock, switchEnableDock, "enable_dock", true)
        bindSwitchRow(view, R.id.row_monet_dock_folder, switchEnableMonetDockFolder, "theme_dock_folder", true)
        bindSwitchRow(view, R.id.row_monet_clock, switchMonetClock, "enable_themed_clock", true)

        bindSlider(view, sliderIconSize, R.id.tv_icon_size_value, "icon_size")
        bindSlider(view, sliderDockOpacity, R.id.tv_dock_opacity_value, "dock_folder_opacity")
        bindSlider(view, sliderDockCornerRadius, R.id.tv_dock_corners_value, "dock_corner_radius")

        bindColorRow(view, R.id.row_monet_fg, "Pick Foreground Color", "monet_fg_color", "selected_fg_color_name")
        bindColorRow(view, R.id.row_monet_bg, "Pick Background Color", "monet_bg_color", "selected_bg_color_name")
        bindColorRow(view, R.id.row_dock_bg, "Pick Dock Background", "monet_folder_dock_bg_color", "selected_monet_folder_dock_bg_color")
        bindColorRow(view, R.id.row_clock_color, "Pick Clock Color", "monet_clock_color", "selected_monet_clock_color")

        view.findViewById<View>(R.id.row_icon_pack).setOnClickListener { showIconPackPicker(view) }

        view.findViewById<View>(R.id.row_apply_custom).setOnClickListener {
            val selectedPack = mService?.getRemotePreferences(PREF_NAME)?.getString("icon_pack", "none")
            if (selectedPack != null && selectedPack != "none") {
                val fragment = AllAppsFragment().apply {
                    arguments = Bundle().apply { putString("EXTRA_ICON_PACK", selectedPack) }
                }
                requireActivity().supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit()
            }
        }

        view.findViewById<View>(R.id.btn_restart_launcher).setOnClickListener { button ->
            button.isEnabled = false
            val appContext = requireContext().applicationContext
            Thread {
                val success = UIHelpers.forceRestartLauncher(appContext)
                activity?.runOnUiThread {
                    button.isEnabled = true
                    toast(if (success) R.string.toast_launcher_restarted else R.string.toast_root_denied)
                }
            }.start()
        }

        view.findViewById<View>(R.id.btn_restart_systemui).setOnClickListener { button ->
            MaterialAlertDialogBuilder(requireContext())
                .setIcon(R.drawable.ic_refresh)
                .setTitle(R.string.action_restart_systemui)
                .setMessage(R.string.action_restart_systemui_summary)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_restart) { _, _ ->
                    button.isEnabled = false
                    Thread {
                        val success = UIHelpers.restartSystemUi()
                        activity?.runOnUiThread {
                            button.isEnabled = true
                            toast(if (success) R.string.toast_systemui_restarted else R.string.toast_root_denied)
                        }
                    }.start()
                }
                .show()
        }
    }

    private fun toast(resId: Int) {
        context?.let { Toast.makeText(it, resId, Toast.LENGTH_SHORT).show() }
    }

    /** getprop / package queries run off the main thread. */
    private fun loadSystemInfo(view: View) {
        val appContext = requireContext().applicationContext
        Thread {
            val device = UIHelpers.getDeviceName()
            val osVersion = UIHelpers.getHyperOsVersion()
            val osMajor = UIHelpers.getHyperOsMajorVersion()
            val launcher = UIHelpers.getDefaultLauncher(appContext)
            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                view.findViewById<TextView>(R.id.tv_status_device).text =
                    getString(R.string.status_device, device)
                view.findViewById<TextView>(R.id.tv_status_os).text =
                    getString(R.string.status_os, osVersion ?: "Unknown", Build.VERSION.RELEASE)
                view.findViewById<TextView>(R.id.tv_status_launcher).text =
                    getString(
                        R.string.status_launcher,
                        launcher?.let { "${it.label} (${it.packageName})" } ?: "Not set"
                    )
                val warning = when {
                    osMajor == null || osMajor < 3 -> getString(R.string.status_os_unsupported)
                    launcher != null && !launcher.isSupported -> getString(R.string.status_launcher_unsupported)
                    else -> null
                }
                view.findViewById<TextView>(R.id.tv_status_warning).apply {
                    text = warning
                    isVisible = warning != null
                }
            }
        }.start()
    }

    private fun applyStatusCard(view: View, active: Boolean) {
        val card = view.findViewById<MaterialCardView>(R.id.card_status)
        val bg = MaterialColors.getColor(
            card,
            if (active) MaterialR.attr.colorPrimaryContainer else MaterialR.attr.colorErrorContainer
        )
        val fg = MaterialColors.getColor(
            card,
            if (active) MaterialR.attr.colorOnPrimaryContainer else MaterialR.attr.colorOnErrorContainer
        )
        card.setCardBackgroundColor(bg)
        view.findViewById<ImageView>(R.id.img_status).apply {
            setImageResource(if (active) R.drawable.ic_check_circle else R.drawable.ic_error)
            imageTintList = ColorStateList.valueOf(fg)
        }
        view.findViewById<TextView>(R.id.tv_status_title)
            .setText(if (active) R.string.status_active else R.string.status_inactive)
        view.findViewById<TextView>(R.id.tv_status_summary).isVisible = !active
        listOf(
            R.id.tv_status_title, R.id.tv_status_summary, R.id.tv_status_device,
            R.id.tv_status_os, R.id.tv_status_launcher, R.id.tv_status_warning
        ).forEach { view.findViewById<TextView>(it).setTextColor(fg) }
    }

    private fun applyServiceStateToUI(view: View?) {
        if (view == null) return

        val service = mService
        applyStatusCard(view, service != null)
        setUiEnabled(view, service != null)
        if (service == null) return

        val prefs = service.getRemotePreferences(PREF_NAME)
        val isThemingEnabled = prefs.getBoolean("enable_themed_icons", false)
        val isMonetCustomization = prefs.getBoolean("enable_monet_colors", false)
        val isThemeDockFolder = prefs.getBoolean("theme_dock_folder", false)
        val isDockEnabled = prefs.getBoolean("enable_dock", false)
        val isThemeClockWidget = prefs.getBoolean("enable_themed_clock", false)
        val selectedPackId = prefs.getString("icon_pack", "none") ?: "none"

        isUpdatingUI = true
        switchEnableTheming.isChecked = isThemingEnabled
        switchHomescreenOnly.isChecked = prefs.getBoolean("themed_icons_homescreen_only", false)
        switchMonet.isChecked = isMonetCustomization
        switchEnableDock.isChecked = isDockEnabled
        switchEnableMonetDockFolder.isChecked = isThemeDockFolder
        switchMonetClock.isChecked = isThemeClockWidget
        sliderIconSize.value = prefs.getInt("icon_size", 180).toFloat()
            .coerceIn(sliderIconSize.valueFrom, sliderIconSize.valueTo)
        sliderDockOpacity.value = prefs.getInt("dock_folder_opacity", 200).toFloat()
            .coerceIn(sliderDockOpacity.valueFrom, sliderDockOpacity.valueTo)
        sliderDockCornerRadius.value = prefs.getInt("dock_corner_radius", 60).toFloat()
            .coerceIn(sliderDockCornerRadius.valueFrom, sliderDockCornerRadius.valueTo)
        isUpdatingUI = false

        view.findViewById<TextView>(R.id.tv_selected_icon_pack).text =
            if (selectedPackId == "none") "None"
            else installedIconPacks.find { it.packageName == selectedPackId }?.name ?: "Unknown"

        updateColorPreview(view, R.id.img_preview_fg, prefs.getInt("monet_fg_color", 0))
        updateColorPreview(view, R.id.img_preview_bg, prefs.getInt("monet_bg_color", 0))
        updateColorPreview(view, R.id.img_preview_dock_bg, prefs.getInt("monet_folder_dock_bg_color", 0))
        updateColorPreview(view, R.id.img_preview_clock, prefs.getInt("monet_clock_color", 0))

        fun show(visible: Boolean, vararg ids: Int) = ids.forEach { view.findViewById<View>(it).isVisible = visible }
        show(
            isThemingEnabled,
            R.id.row_homescreen_only, R.id.row_icon_pack, R.id.title_monet_colors, R.id.card_monet_colors
        )
        show(isThemingEnabled && selectedPackId != "none", R.id.row_apply_custom)
        show(isMonetCustomization, R.id.row_monet_fg, R.id.row_monet_bg)
        show(isDockEnabled, R.id.row_dock_corners)
        show(isThemeDockFolder, R.id.row_dock_opacity, R.id.row_dock_bg)
        show(isThemeClockWidget, R.id.row_clock_color)
    }

    private fun showIconPackPicker(view: View) {
        val displayNames = arrayOf("None") + installedIconPacks.map { it.name }.toTypedArray()
        val packageNames = arrayOf("none") + installedIconPacks.map { it.packageName }.toTypedArray()

        val currentSelection = mService?.getRemotePreferences(PREF_NAME)?.getString("icon_pack", "none")
        val currentIndex = packageNames.indexOf(currentSelection).takeIf { it >= 0 } ?: 0

        MaterialAlertDialogBuilder(requireContext())
            .setIcon(R.drawable.ic_apps)
            .setTitle(R.string.pref_choose_pack)
            .setSingleChoiceItems(displayNames, currentIndex) { dialog, which ->
                UIHelpers.pushRemotePref("icon_pack", packageNames[which])
                applyServiceStateToUI(view)
                UIHelpers.restartLauncher(requireContext())
                dialog.dismiss()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun updateColorPreview(view: View, imageViewId: Int, savedColor: Int) {
        val imageView = view.findViewById<ImageView>(imageViewId) ?: return
        val resolvedColor = try {
            if (savedColor != 0) ContextCompat.getColor(requireContext(), savedColor) else Color.TRANSPARENT
        } catch (_: Exception) {
            Color.TRANSPARENT
        }
        val outline = MaterialColors.getColor(imageView, MaterialR.attr.colorOutline)
        imageView.setImageDrawable(GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(resolvedColor)
            setStroke(dpToPx(1.5f).toInt(), outline)
        })
    }

    private fun setUiEnabled(view: View, isEnabled: Boolean) {
        val content = view.findViewById<ViewGroup>(R.id.settings_content)
        content.alpha = if (isEnabled) 1f else 0.38f
        setEnabledRecursive(content, isEnabled)
    }

    private fun setEnabledRecursive(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        if (view is ViewGroup) view.children.forEach { setEnabledRecursive(it, enabled) }
    }

    private fun dpToPx(dp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)

    private fun getInstalledIconPacks(context: Context): List<IconPackInfo> {
        val pm = context.packageManager
        val iconPacks = mutableListOf<IconPackInfo>()
        val actions = arrayOf("com.novalauncher.THEME", "org.adw.launcher.THEMES")
        for (action in actions) {
            val resolveInfos = pm.queryIntentActivities(Intent(action), PackageManager.GET_META_DATA)
            for (info in resolveInfos) {
                val packageName = info.activityInfo.packageName
                if (iconPacks.none { it.packageName == packageName }) {
                    iconPacks.add(IconPackInfo(info.loadLabel(pm).toString(), packageName))
                }
            }
        }
        return iconPacks.sortedBy { it.name }
    }
}
