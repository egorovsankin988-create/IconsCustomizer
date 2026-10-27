package com.ukm.app.iconscustomizer

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
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

    // Tab Containers
    private lateinit var containerDesktop: View
    private lateinit var containerSettings: View
    private var currentTabId = R.id.nav_home

    // Switches
    private lateinit var switchHideIconLabels: MaterialSwitch
    private lateinit var switchHideIconShadows: MaterialSwitch
    private lateinit var switchEnableDock: MaterialSwitch
    private lateinit var switchDockBlur: MaterialSwitch
    private lateinit var switchHideDockLabels: MaterialSwitch
    private lateinit var switchEnableDockFolder: MaterialSwitch
    private lateinit var switchUnlockAnimations: MaterialSwitch
    private lateinit var switchUnlockAnimRate: MaterialSwitch

    // Sliders
    private lateinit var sliderIconSize: Slider
    private lateinit var sliderDockWidth: Slider
    private lateinit var sliderDockHeight: Slider
    private lateinit var sliderDockCornerRadius: Slider
    private lateinit var sliderDockOpacity: Slider
    private lateinit var sliderFolderCornerRadius: Slider
    private lateinit var sliderFolderOpacity: Slider

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        installedIconPacks = getInstalledIconPacks(requireContext())
        setupInteractions(view)
        loadSystemInfo(view)
        selectTab(currentTabId)
    }

    fun selectTab(tabId: Int) {
        currentTabId = tabId
        if (!::containerDesktop.isInitialized) return
        containerDesktop.isVisible = tabId == R.id.nav_home
        containerSettings.isVisible = tabId == R.id.nav_settings
        view?.findViewById<NestedScrollView>(R.id.settings_scroll)?.smoothScrollTo(0, 0)
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
        Log.i(Constants.TAG, "Service State Changed")
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

    private fun bindSlider(view: View, slider: Slider, valueViewId: Int, prefKey: String, unit: String = "") {
        val valueView = view.findViewById<TextView>(valueViewId)
        slider.setLabelFormatter { "${it.toInt()}$unit" }
        slider.addOnChangeListener { _, value, fromUser ->
            valueView.text = "${value.toInt()}$unit"
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

    private fun openAllApps(packPackage: String) {
        val fragment = AllAppsFragment().apply {
            arguments = Bundle().apply { putString("EXTRA_ICON_PACK", packPackage) }
        }
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun showNoPacksDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setIcon(R.drawable.ic_info)
            .setTitle(R.string.dialog_no_packs_title)
            .setMessage(R.string.dialog_no_packs_msg)
            .setPositiveButton(R.string.btn_search_google_play) { _, _ ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=icon+pack"))
                    startActivity(intent)
                } catch (_: Throwable) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=icon+pack&c=apps"))
                    startActivity(intent)
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showIconPackPicker(view: View, openAppsAfterSelection: Boolean = false) {
        if (installedIconPacks.isEmpty()) {
            showNoPacksDialog()
            return
        }
        val displayNames = arrayOf("None") + installedIconPacks.map { it.name }.toTypedArray()
        val packageNames = arrayOf("none") + installedIconPacks.map { it.packageName }.toTypedArray()

        val currentSelection = mService?.getRemotePreferences(PREF_NAME)?.getString("icon_pack", "none")
        val currentIndex = packageNames.indexOf(currentSelection).takeIf { it >= 0 } ?: 0

        MaterialAlertDialogBuilder(requireContext())
            .setIcon(R.drawable.ic_apps)
            .setTitle(R.string.pref_choose_pack)
            .setSingleChoiceItems(displayNames, currentIndex) { dialog, which ->
                val chosenPack = packageNames[which]
                UIHelpers.pushRemotePref("icon_pack", chosenPack)
                UIHelpers.pushRemotePref("enable_themed_icons", chosenPack != "none")
                applyServiceStateToUI(view)
                UIHelpers.restartLauncher(requireContext())
                dialog.dismiss()
                if (openAppsAfterSelection && chosenPack != "none") {
                    openAllApps(chosenPack)
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun setupInteractions(view: View) {
        // Tab Containers
        containerDesktop = view.findViewById(R.id.container_desktop)
        containerSettings = view.findViewById(R.id.container_settings)

        // Switches
        switchHideIconLabels = view.findViewById(R.id.switch_hide_icon_labels)
        switchHideIconShadows = view.findViewById(R.id.switch_hide_icon_shadows)
        switchEnableDock = view.findViewById(R.id.switch_enable_dock)
        switchDockBlur = view.findViewById(R.id.switch_dock_blur)
        switchHideDockLabels = view.findViewById(R.id.switch_hide_dock_labels)
        switchEnableDockFolder = view.findViewById(R.id.switch_monet_dock_folder)
        switchUnlockAnimations = view.findViewById(R.id.switch_unlock_animations)
        switchUnlockAnimRate = view.findViewById(R.id.switch_unlock_anim_rate)

        // Sliders
        sliderIconSize = view.findViewById(R.id.slider_icon_size)
        sliderDockWidth = view.findViewById(R.id.slider_dock_width)
        sliderDockHeight = view.findViewById(R.id.slider_dock_height)
        sliderDockCornerRadius = view.findViewById(R.id.slider_dock_corners)
        sliderDockOpacity = view.findViewById(R.id.slider_dock_opacity)
        sliderFolderCornerRadius = view.findViewById(R.id.slider_folder_corners)
        sliderFolderOpacity = view.findViewById(R.id.slider_folder_opacity)

        // Bind Switches
        bindSwitchRow(view, R.id.row_hide_icon_labels, switchHideIconLabels, "hide_icon_labels", false)
        bindSwitchRow(view, R.id.row_hide_icon_shadows, switchHideIconShadows, "hide_icon_shadows", false)
        bindSwitchRow(view, R.id.row_enable_dock, switchEnableDock, "enable_dock", true)
        bindSwitchRow(view, R.id.row_dock_blur, switchDockBlur, "dock_blur", false)
        bindSwitchRow(view, R.id.row_hide_dock_labels, switchHideDockLabels, "hide_dock_labels", false)
        bindSwitchRow(view, R.id.row_monet_dock_folder, switchEnableDockFolder, "theme_dock_folder", true)
        bindSwitchRow(view, R.id.row_unlock_animations, switchUnlockAnimations, "unlock_animations", false)
        bindSwitchRow(view, R.id.row_unlock_anim_rate, switchUnlockAnimRate, "unlock_anim_rate", false)

        // Bind Sliders
        bindSlider(view, sliderIconSize, R.id.tv_icon_size_value, "icon_size")
        bindSlider(view, sliderDockWidth, R.id.tv_dock_width_value, "dock_width", "%")
        bindSlider(view, sliderDockHeight, R.id.tv_dock_height_value, "dock_height", " dp")
        bindSlider(view, sliderDockCornerRadius, R.id.tv_dock_corners_value, "dock_corner_radius")
        bindSlider(view, sliderDockOpacity, R.id.tv_dock_opacity_value, "dock_folder_opacity")
        bindSlider(view, sliderFolderCornerRadius, R.id.tv_folder_corners_value, "folder_corner_radius")
        bindSlider(view, sliderFolderOpacity, R.id.tv_folder_opacity_value, "folder_opacity")

        // Icon pack row
        view.findViewById<View>(R.id.row_icon_pack).setOnClickListener { showIconPackPicker(view) }

        // Per-app icon customization row
        view.findViewById<View>(R.id.row_apply_custom).setOnClickListener {
            val selectedPack = mService?.getRemotePreferences(PREF_NAME)?.getString("icon_pack", "none")
            if (installedIconPacks.isEmpty()) {
                showNoPacksDialog()
            } else if (selectedPack == null || selectedPack == "none") {
                showIconPackPicker(view, openAppsAfterSelection = true)
            } else {
                openAllApps(selectedPack)
            }
        }

        // Action Buttons
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
        val isThemeDockFolder = prefs.getBoolean("theme_dock_folder", false)
        val isDockEnabled = prefs.getBoolean("enable_dock", false)
        val selectedPackId = prefs.getString("icon_pack", "none") ?: "none"

        isUpdatingUI = true
        switchEnableDock.isChecked = isDockEnabled
        switchDockBlur.isChecked = prefs.getBoolean("dock_blur", true)
        switchEnableDockFolder.isChecked = isThemeDockFolder
        switchHideIconLabels.isChecked = prefs.getBoolean("hide_icon_labels", false)
        switchHideIconShadows.isChecked = prefs.getBoolean("hide_icon_shadows", false)
        switchHideDockLabels.isChecked = prefs.getBoolean("hide_dock_labels", false)
        switchUnlockAnimations.isChecked = prefs.getBoolean("unlock_animations", false)
        switchUnlockAnimRate.isChecked = prefs.getBoolean("unlock_anim_rate", false)

        sliderDockWidth.value = prefs.getInt("dock_width", 90).toFloat()
            .coerceIn(sliderDockWidth.valueFrom, sliderDockWidth.valueTo)
        sliderDockHeight.value = prefs.getInt("dock_height", 14).toFloat()
            .coerceIn(sliderDockHeight.valueFrom, sliderDockHeight.valueTo)
        sliderIconSize.value = prefs.getInt("icon_size", 180).toFloat()
            .coerceIn(sliderIconSize.valueFrom, sliderIconSize.valueTo)
        sliderDockOpacity.value = prefs.getInt("dock_folder_opacity", 200).toFloat()
            .coerceIn(sliderDockOpacity.valueFrom, sliderDockOpacity.valueTo)
        sliderDockCornerRadius.value = prefs.getInt("dock_corner_radius", 60).toFloat()
            .coerceIn(sliderDockCornerRadius.valueFrom, sliderDockCornerRadius.valueTo)
        sliderFolderCornerRadius.value = prefs.getInt("folder_corner_radius", 66).toFloat()
            .coerceIn(sliderFolderCornerRadius.valueFrom, sliderFolderCornerRadius.valueTo)
        sliderFolderOpacity.value = prefs.getInt("folder_opacity", 200).toFloat()
            .coerceIn(sliderFolderOpacity.valueFrom, sliderFolderOpacity.valueTo)
        isUpdatingUI = false

        view.findViewById<TextView>(R.id.tv_selected_icon_pack).text =
            if (selectedPackId == "none") "None"
            else installedIconPacks.find { it.packageName == selectedPackId }?.name ?: selectedPackId

        fun show(visible: Boolean, vararg ids: Int) = ids.forEach { view.findViewById<View>(it).isVisible = visible }
        show(
            isDockEnabled,
            R.id.row_dock_blur, R.id.row_dock_width, R.id.row_dock_height,
            R.id.row_dock_corners, R.id.row_dock_opacity, R.id.row_hide_dock_labels
        )
        show(
            isThemeDockFolder,
            R.id.row_folder_corners, R.id.row_folder_opacity
        )

        selectTab(currentTabId)
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
