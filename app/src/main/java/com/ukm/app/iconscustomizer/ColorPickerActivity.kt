package com.ukm.app.iconscustomizer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.ukm.app.iconscustomizer.MainActivity.Companion.PREF_NAME

class ColorPickerActivity : AppCompatActivity() {

    private lateinit var targetPreferenceKey: String
    private lateinit var colorTypeKey: String

    private var initialColorId: Int = 0
    private var currentColor: Int = Color.TRANSPARENT
    private var selectedColor: Int = Color.RED
    private var selectedMonetResId: Int = 0
    private var selectedMonetName: String = ""

    private var currentAlpha = 255
    private var currentHue = 0f
    private var currentSat = 1f
    private var currentVal = 1f

    private var isUpdatingFromCode = false

    private lateinit var viewCurrentColor: View
    private lateinit var viewNewColor: View
    private lateinit var etHex: TextInputEditText
    private lateinit var sliderHue: Slider
    private lateinit var sliderSaturation: Slider
    private lateinit var sliderValue: Slider
    private lateinit var sliderAlpha: Slider
    private lateinit var sliderRed: Slider
    private lateinit var sliderGreen: Slider
    private lateinit var sliderBlue: Slider

    private lateinit var tvHueValue: TextView
    private lateinit var tvSatValue: TextView
    private lateinit var tvValValue: TextView
    private lateinit var tvAlphaValue: TextView
    private lateinit var tvRedValue: TextView
    private lateinit var tvGreenValue: TextView
    private lateinit var tvBlueValue: TextView
    private lateinit var tvRgbReadout: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_color_picker)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.materialToolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val dialogTitle = intent.getStringExtra("EXTRA_TITLE") ?: getString(R.string.color_picker_tab_palette)
        targetPreferenceKey = intent.getStringExtra("EXTRA_TARGET_KEY") ?: return finish()
        colorTypeKey = intent.getStringExtra("EXTRA_TYPE_KEY") ?: return finish()
        supportActionBar?.title = dialogTitle

        val prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        initialColorId = prefs.getInt(targetPreferenceKey, 0)
        selectedMonetName = prefs.getString(colorTypeKey, "default") ?: "default"

        currentColor = Constants.resolveColor(this, initialColorId)
        selectedColor = if (currentColor != 0 && currentColor != Color.TRANSPARENT) currentColor else "#3F5F90".toColorInt()

        currentAlpha = Color.alpha(selectedColor)
        val hsv = FloatArray(3)
        Color.colorToHSV(selectedColor, hsv)
        currentHue = hsv[0]
        currentSat = hsv[1]
        currentVal = hsv[2]

        initViews()
        setupModeToggle()
        setupHexInput()
        setupSliders()
        setupPresetSwatches()
        setupMonetGrid()
        setupBottomActions()

        updateUiFromColor(selectedColor, updateHexField = true)
    }

    private fun initViews() {
        viewCurrentColor = findViewById(R.id.view_current_color)
        viewNewColor = findViewById(R.id.view_new_color)
        etHex = findViewById(R.id.et_hex)
        sliderHue = findViewById(R.id.slider_hue)
        sliderSaturation = findViewById(R.id.slider_saturation)
        sliderValue = findViewById(R.id.slider_value)
        sliderAlpha = findViewById(R.id.slider_alpha)
        sliderRed = findViewById(R.id.slider_red)
        sliderGreen = findViewById(R.id.slider_green)
        sliderBlue = findViewById(R.id.slider_blue)

        tvHueValue = findViewById(R.id.tv_hue_value)
        tvSatValue = findViewById(R.id.tv_saturation_value)
        tvValValue = findViewById(R.id.tv_value_value)
        tvAlphaValue = findViewById(R.id.tv_alpha_value)
        tvRedValue = findViewById(R.id.tv_red_value)
        tvGreenValue = findViewById(R.id.tv_green_value)
        tvBlueValue = findViewById(R.id.tv_blue_value)
        tvRgbReadout = findViewById(R.id.tv_rgb_readout)

        updateSwatch(viewCurrentColor, currentColor)

        findViewById<View>(R.id.btn_copy_hex).setOnClickListener {
            val hex = etHex.text.toString().trim()
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("HEX Color", "#$hex"))
            Toast.makeText(this, "HEX #$hex скопирован", Toast.LENGTH_SHORT).show()
        }

        findViewById<View>(R.id.btn_paste_hex).setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                var text = clip.getItemAt(0).text?.toString()?.trim() ?: ""
                text = text.replace("#", "")
                if (text.length == 6 || text.length == 8) {
                    etHex.setText(text)
                } else {
                    Toast.makeText(this, "В буфере нет валидного HEX кода (#RRGGBB)", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateSwatch(view: View, color: Int) {
        val density = resources.displayMetrics.density
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 16f * density
            if (color == 0 || color == Color.TRANSPARENT) {
                setColor(Color.TRANSPARENT)
                setStroke((1.5f * density).toInt(), Color.GRAY)
            } else {
                setColor(color)
                setStroke((1.5f * density).toInt(), Color.argb(40, 0, 0, 0))
            }
        }
    }

    private fun setupModeToggle() {
        val toggle = findViewById<MaterialButtonToggleGroup>(R.id.toggle_group_mode)
        val containerPalette = findViewById<View>(R.id.container_palette)
        val containerCustom = findViewById<View>(R.id.container_custom)
        val containerRgb = findViewById<View>(R.id.container_rgb)

        toggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                containerPalette.isVisible = checkedId == R.id.btn_mode_palette
                containerCustom.isVisible = checkedId == R.id.btn_mode_custom
                containerRgb.isVisible = checkedId == R.id.btn_mode_rgb
            }
        }
    }

    private fun setupHexInput() {
        etHex.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isUpdatingFromCode) return
                var text = s?.toString()?.trim() ?: return
                text = text.replace("#", "").trim()
                if (text.length == 6 || text.length == 8) {
                    try {
                        val parsed = Color.parseColor("#$text")
                        selectedMonetResId = 0
                        selectedMonetName = "custom_#$text"
                        currentAlpha = Color.alpha(parsed)
                        val hsv = FloatArray(3)
                        Color.colorToHSV(parsed, hsv)
                        currentHue = hsv[0]
                        currentSat = hsv[1]
                        currentVal = hsv[2]
                        updateUiFromColor(parsed, updateHexField = false)
                    } catch (_: Exception) {}
                }
            }
        })
    }

    private fun setupSliders() {
        // HSV Sliders
        sliderHue.addOnChangeListener { _, value, fromUser ->
            if (isUpdatingFromCode) return@addOnChangeListener
            currentHue = value
            tvHueValue.text = "${value.toInt()}°"
            if (fromUser) recalculateColorFromHsv()
        }

        sliderSaturation.addOnChangeListener { _, value, fromUser ->
            if (isUpdatingFromCode) return@addOnChangeListener
            currentSat = value / 100f
            tvSatValue.text = "${value.toInt()}%"
            if (fromUser) recalculateColorFromHsv()
        }

        sliderValue.addOnChangeListener { _, value, fromUser ->
            if (isUpdatingFromCode) return@addOnChangeListener
            currentVal = value / 100f
            tvValValue.text = "${value.toInt()}%"
            if (fromUser) recalculateColorFromHsv()
        }

        sliderAlpha.addOnChangeListener { _, value, fromUser ->
            if (isUpdatingFromCode) return@addOnChangeListener
            currentAlpha = (value * 2.55f).toInt().coerceIn(0, 255)
            tvAlphaValue.text = "${value.toInt()}%"
            if (fromUser) recalculateColorFromHsv()
        }

        // RGB Sliders
        sliderRed.addOnChangeListener { _, value, fromUser ->
            if (isUpdatingFromCode) return@addOnChangeListener
            tvRedValue.text = "${value.toInt()}"
            if (fromUser) recalculateColorFromRgb()
        }

        sliderGreen.addOnChangeListener { _, value, fromUser ->
            if (isUpdatingFromCode) return@addOnChangeListener
            tvGreenValue.text = "${value.toInt()}"
            if (fromUser) recalculateColorFromRgb()
        }

        sliderBlue.addOnChangeListener { _, value, fromUser ->
            if (isUpdatingFromCode) return@addOnChangeListener
            tvBlueValue.text = "${value.toInt()}"
            if (fromUser) recalculateColorFromRgb()
        }
    }

    private fun recalculateColorFromHsv() {
        selectedMonetResId = 0
        selectedMonetName = "custom"
        val baseColor = Color.HSVToColor(floatArrayOf(currentHue, currentSat, currentVal))
        selectedColor = Color.argb(currentAlpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
        updateUiFromColor(selectedColor, updateHexField = true)
    }

    private fun recalculateColorFromRgb() {
        selectedMonetResId = 0
        selectedMonetName = "custom"
        val r = sliderRed.value.toInt()
        val g = sliderGreen.value.toInt()
        val b = sliderBlue.value.toInt()
        selectedColor = Color.argb(currentAlpha, r, g, b)

        val hsv = FloatArray(3)
        Color.colorToHSV(selectedColor, hsv)
        currentHue = hsv[0]
        currentSat = hsv[1]
        currentVal = hsv[2]

        updateUiFromColor(selectedColor, updateHexField = true)
    }

    private fun updateUiFromColor(color: Int, updateHexField: Boolean) {
        selectedColor = color
        updateSwatch(viewNewColor, color)

        if (updateHexField) {
            isUpdatingFromCode = true
            val hex = if (Color.alpha(color) == 255) {
                String.format("%06X", 0xFFFFFF and color)
            } else {
                String.format("%08X", color)
            }
            etHex.setText(hex)
            etHex.setSelection(hex.length)
            isUpdatingFromCode = false
        }

        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val a = Color.alpha(color)
        val alphaPercent = (a / 255f * 100f).toInt()
        tvRgbReadout.text = "R: $r   G: $g   B: $b   A: $alphaPercent%"

        isUpdatingFromCode = true
        sliderHue.value = currentHue.coerceIn(sliderHue.valueFrom, sliderHue.valueTo)
        tvHueValue.text = "${currentHue.toInt()}°"

        sliderSaturation.value = (currentSat * 100f).coerceIn(sliderSaturation.valueFrom, sliderSaturation.valueTo)
        tvSatValue.text = "${(currentSat * 100f).toInt()}%"

        sliderValue.value = (currentVal * 100f).coerceIn(sliderValue.valueFrom, sliderValue.valueTo)
        tvValValue.text = "${(currentVal * 100f).toInt()}%"

        sliderAlpha.value = alphaPercent.toFloat().coerceIn(sliderAlpha.valueFrom, sliderAlpha.valueTo)
        tvAlphaValue.text = "$alphaPercent%"

        sliderRed.value = r.toFloat().coerceIn(sliderRed.valueFrom, sliderRed.valueTo)
        tvRedValue.text = "$r"

        sliderGreen.value = g.toFloat().coerceIn(sliderGreen.valueFrom, sliderGreen.valueTo)
        tvGreenValue.text = "$g"

        sliderBlue.value = b.toFloat().coerceIn(sliderBlue.valueFrom, sliderBlue.valueTo)
        tvBlueValue.text = "$b"
        isUpdatingFromCode = false
    }

    private fun setupPresetSwatches() {
        val container = findViewById<LinearLayout>(R.id.layout_preset_swatches)
        val density = resources.displayMetrics.density
        fun dpToPx(dp: Float): Int = (dp * density).toInt()

        val colorRows = listOf(
            // Primary & Vibrant
            listOf(0xFFF44336.toInt(), 0xFFE91E63.toInt(), 0xFF9C27B0.toInt(), 0xFF673AB7.toInt(), 0xFF3F51B5.toInt(), 0xFF2196F3.toInt()),
            // Cyan, Teal, Green, Lime
            listOf(0xFF03A9F4.toInt(), 0xFF00BCD4.toInt(), 0xFF009688.toInt(), 0xFF4CAF50.toInt(), 0xFF8BC34A.toInt(), 0xFFCDDC39.toInt()),
            // Yellow, Amber, Orange, Deep Orange, Brown, Blue Grey
            listOf(0xFFFFEB3B.toInt(), 0xFFFFC107.toInt(), 0xFFFF9800.toInt(), 0xFFFF5722.toInt(), 0xFF795548.toInt(), 0xFF607D8B.toInt()),
            // HyperOS & POCO signature accents
            listOf(0xFFFF6900.toInt(), 0xFFFFCC00.toInt(), 0xFF0A84FF.toInt(), 0xFF30D158.toInt(), 0xFFBF5AF2.toInt(), 0xFFFF375F.toInt()),
            // Deep, AMOLED & Light
            listOf(0xFF000000.toInt(), 0xFF1E1E1E.toInt(), 0xFF2C2C2E.toInt(), 0xFF3A3A3C.toInt(), 0xFFE5E5EA.toInt(), 0xFFFFFFFF.toInt())
        )

        for (row in colorRows) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(4f), 0, dpToPx(4f))
            }
            for (color in row) {
                val swatch = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(dpToPx(44f), dpToPx(44f)).apply {
                        setMargins(dpToPx(6f), dpToPx(4f), dpToPx(6f), dpToPx(4f))
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(color)
                        if (color == 0xFFFFFFFF.toInt() || color == 0xFF000000.toInt()) {
                            setStroke(dpToPx(1.5f), Color.LTGRAY)
                        }
                    }
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        selectedMonetResId = 0
                        selectedMonetName = "preset_" + String.format("%06X", 0xFFFFFF and color)
                        currentAlpha = Color.alpha(color)
                        val hsv = FloatArray(3)
                        Color.colorToHSV(color, hsv)
                        currentHue = hsv[0]
                        currentSat = hsv[1]
                        currentVal = hsv[2]
                        updateUiFromColor(color, true)
                    }
                }
                rowLayout.addView(swatch)
            }
            container.addView(rowLayout)
        }
    }

    private fun setupMonetGrid() {
        val gridContainer = findViewById<FrameLayout>(R.id.gridContainer)
        val density = resources.displayMetrics.density
        fun dpToPx(dp: Int): Int = (dp * density).toInt()

        val gridLayout = GridLayout(this).apply {
            columnCount = 4
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(8))
        }

        val categories = listOf("accent1", "accent2", "accent3", "neutral1")
        val displayNames = listOf("Accent 1", "Accent 2", "Accent 3", "Neutral 1")
        val shades = listOf(100, 200, 300, 400, 500, 600, 700, 800, 900)

        val cellWidth = dpToPx(64)
        val cellHeight = dpToPx(46)
        val cellMargin = dpToPx(6)

        val outValue = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)

        for (name in displayNames) {
            val headerText = TextView(this).apply {
                text = name
                textSize = 13f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(8), 0, dpToPx(8))
            }
            gridLayout.addView(headerText)
        }

        for (shade in shades) {
            for (category in categories) {
                val resourceName = "system_${category}_$shade"
                val colorResId = resources.getIdentifier(resourceName, "color", "android")
                val colorType = "${category}_$shade"

                val cellContainer = FrameLayout(this).apply {
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = cellWidth
                        height = cellHeight
                        setMargins(cellMargin, cellMargin, cellMargin, cellMargin)
                    }
                    isClickable = true
                    isFocusable = true
                    foreground = ContextCompat.getDrawable(context, outValue.resourceId)
                }

                val resolvedColor = if (colorResId != 0) {
                    try { ContextCompat.getColor(this, colorResId) } catch (_: Exception) { Color.TRANSPARENT }
                } else Color.TRANSPARENT

                val backgroundShape = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(12).toFloat()
                    if (resolvedColor != Color.TRANSPARENT) {
                        setColor(resolvedColor)
                    } else {
                        setColor(Color.LTGRAY)
                    }
                }
                cellContainer.background = backgroundShape

                val textView = TextView(this).apply {
                    gravity = Gravity.CENTER
                    textSize = 11f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    if (colorResId != 0) {
                        text = shade.toString()
                        setTextColor(if (shade <= 400) "#DE000000".toColorInt() else Color.WHITE)
                    } else {
                        text = "-"
                        setTextColor(Color.DKGRAY)
                    }
                }
                cellContainer.addView(textView)

                if (colorResId != 0 && resolvedColor != Color.TRANSPARENT) {
                    cellContainer.setOnClickListener {
                        selectedMonetResId = colorResId
                        selectedMonetName = colorType
                        currentAlpha = 255
                        val hsv = FloatArray(3)
                        Color.colorToHSV(resolvedColor, hsv)
                        currentHue = hsv[0]
                        currentSat = hsv[1]
                        currentVal = hsv[2]
                        updateUiFromColor(resolvedColor, true)
                    }
                }
                gridLayout.addView(cellContainer)
            }
        }
        gridContainer.addView(gridLayout)
    }

    private fun setupBottomActions() {
        val btnClear = findViewById<MaterialButton>(R.id.btnClearColor)
        btnClear.setOnClickListener {
            UIHelpers.pushRemotePref(targetPreferenceKey, 0)
            UIHelpers.pushLocalPref(this, targetPreferenceKey, 0)
            UIHelpers.pushRemotePref(colorTypeKey, "default")
            UIHelpers.pushLocalPref(this, colorTypeKey, "default")
            UIHelpers.restartLauncher(this)
            Toast.makeText(this, "Цвет сброшен по умолчанию", Toast.LENGTH_SHORT).show()
            finishWithResult(0)
        }

        val btnApply = findViewById<MaterialButton>(R.id.btnApplyColor)
        btnApply.setOnClickListener {
            val toSaveValue = if (selectedMonetResId != 0) selectedMonetResId else selectedColor
            val toSaveName = if (selectedMonetResId != 0) selectedMonetName else String.format("#%08X", selectedColor)

            UIHelpers.pushLocalPref(this, targetPreferenceKey, toSaveValue)
            UIHelpers.pushLocalPref(this, colorTypeKey, toSaveName)
            UIHelpers.pushRemotePref(targetPreferenceKey, toSaveValue)
            UIHelpers.pushRemotePref(colorTypeKey, toSaveName)

            UIHelpers.restartLauncher(this)
            Toast.makeText(this, getString(R.string.toast_launcher_restarted), Toast.LENGTH_SHORT).show()
            finishWithResult(toSaveValue)
        }
    }

    private fun finishWithResult(savedColorId: Int) {
        val resultIntent = Intent().apply {
            putExtra("RETURNED_COLOR_ID", savedColorId)
            putExtra("RETURNED_TARGET_KEY", targetPreferenceKey)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }
}