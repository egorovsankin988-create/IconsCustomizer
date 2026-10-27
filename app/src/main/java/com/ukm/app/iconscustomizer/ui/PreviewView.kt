package com.ukm.app.iconscustomizer.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import androidx.core.graphics.ColorUtils
import com.ukm.app.iconscustomizer.Constants
import com.ukm.app.iconscustomizer.Materials
import com.ukm.app.iconscustomizer.Prefs
import com.ukm.app.iconscustomizer.R

object IconPacks {
    data class Pack(val name: String, val packageName: String)

    fun installed(context: Context): List<Pack> {
        val pm = context.packageManager
        val packs = linkedMapOf<String, Pack>()
        for (action in arrayOf("com.novalauncher.THEME", "org.adw.launcher.THEMES")) {
            for (info in pm.queryIntentActivities(Intent(action), PackageManager.GET_META_DATA)) {
                val pkg = info.activityInfo.packageName
                packs.getOrPut(pkg) { Pack(info.loadLabel(pm).toString(), pkg) }
            }
        }
        return packs.values.sortedBy { it.name.lowercase() }
    }

    fun label(context: Context, pkg: String): String {
        if (pkg == "none" || pkg.isEmpty()) return context.getString(R.string.icon_pack_none)
        return try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) {
            pkg
        }
    }
}

/**
 * Live mock-up of the launcher / system surface a category changes. It uses the same
 * [Materials] code as the hooks, so dock and folder styles look like they will on the phone.
 */
@SuppressLint("ViewConstructor")
class PreviewView(
    context: Context,
    private val kind: PreviewKind,
    private val accent: Accent,
    private val valueOf: (String) -> Any?,
) : View(context) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density
    private fun int(key: String) = valueOf(key) as? Int ?: (Prefs.DEFAULTS[key] as? Int ?: 0)
    private fun bool(key: String) = valueOf(key) == true
    private fun str(key: String) = valueOf(key) as? String ?: (Prefs.DEFAULTS[key] as? String ?: "")

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val clip = Path()

    private val iconColors: IntArray by lazy {
        val ids = intArrayOf(
            android.R.color.system_accent1_400, android.R.color.system_accent2_400,
            android.R.color.system_accent3_400, android.R.color.system_accent1_600,
            android.R.color.system_accent3_200, android.R.color.system_accent2_600,
        )
        ids.map { id -> try { context.getColor(id) } catch (_: Throwable) { accent.accent } }.toIntArray()
    }

    init {
        contentDescription = context.getString(R.string.preview_label)
    }

    override fun onDraw(canvas: Canvas) {
        val pad = dp(12f)
        rect.set(pad, pad, width - pad, height - pad)
        clip.reset()
        clip.addRoundRect(rect, dp(20f), dp(20f), Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        drawWallpaper(canvas, rect)
        when (kind) {
            PreviewKind.DOCK -> drawDock(canvas, rect)
            PreviewKind.FOLDER -> drawFolder(canvas, rect)
            PreviewKind.ICONS -> drawIcons(canvas, rect)
            PreviewKind.CONTROL_CENTER -> drawControlCenter(canvas, rect)
            PreviewKind.VOLUME -> drawVolume(canvas, rect)
        }
        canvas.restore()
    }

    private fun drawWallpaper(canvas: Canvas, r: RectF) {
        paint.shader = LinearGradient(
            r.left, r.top, r.right, r.bottom,
            ColorUtils.blendARGB(iconColors[0], Color.BLACK, 0.15f),
            ColorUtils.blendARGB(iconColors[2], Color.WHITE, 0.1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(r, paint)
        paint.shader = null
        paint.maskFilter = BlurMaskFilter(dp(36f), BlurMaskFilter.Blur.NORMAL)
        paint.color = ColorUtils.setAlphaComponent(iconColors[4], 170)
        canvas.drawCircle(r.left + r.width() * 0.25f, r.top + r.height() * 0.35f, dp(46f), paint)
        paint.color = ColorUtils.setAlphaComponent(iconColors[1], 150)
        canvas.drawCircle(r.left + r.width() * 0.8f, r.top + r.height() * 0.7f, dp(56f), paint)
        paint.maskFilter = null
    }

    private fun drawAppIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int, label: Boolean) {
        if (!bool(Prefs.HIDE_SHADOWS)) {
            paint.color = 0x33000000
            paint.maskFilter = BlurMaskFilter(dp(4f), BlurMaskFilter.Blur.NORMAL)
            canvas.drawRoundRect(cx - size / 2, cy - size / 2 + dp(2f), cx + size / 2, cy + size / 2 + dp(2f), size * 0.3f, size * 0.3f, paint)
            paint.maskFilter = null
        }
        paint.color = color
        canvas.drawRoundRect(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2, size * 0.3f, size * 0.3f, paint)
        paint.color = ColorUtils.setAlphaComponent(Color.WHITE, 200)
        canvas.drawCircle(cx, cy, size * 0.18f, paint)
        if (label) {
            val labelScale = int(Prefs.LABEL_SIZE).takeIf { it > 0 }?.let { it / 12f } ?: 1f
            val w = size * 0.8f * labelScale.coerceIn(0.6f, 1.6f)
            val h = dp(3.5f) * labelScale.coerceIn(0.6f, 1.6f)
            paint.color = ColorUtils.setAlphaComponent(Color.WHITE, 210)
            val top = cy + size / 2 + dp(6f)
            canvas.drawRoundRect(cx - w / 2, top, cx + w / 2, top + h, h, h, paint)
        }
    }

    private fun iconSize() = dp(44f) * int(Prefs.ICON_SCALE) / 100f

    private fun drawIcons(canvas: Canvas, r: RectF) {
        val size = iconSize()
        val step = r.width() / 4
        for (row in 0..1) {
            for (i in 0..3) {
                val cx = r.left + step * (i + 0.5f)
                val cy = r.top + r.height() * (if (row == 0) 0.3f else 0.68f)
                drawAppIcon(canvas, cx, cy, size, iconColors[(i + row * 2) % iconColors.size], !bool(Prefs.HIDE_LABELS))
            }
        }
    }

    private fun drawDock(canvas: Canvas, r: RectF) {
        val size = dp(40f) * int(Prefs.ICON_SCALE) / 100f
        // Home screen icons above the dock
        val step = r.width() / 4
        for (i in 0..3) {
            drawAppIcon(canvas, r.left + step * (i + 0.5f), r.top + dp(40f), size, iconColors[(i + 2) % iconColors.size], !bool(Prefs.HIDE_LABELS))
        }
        val enabled = bool(Prefs.DOCK)
        val widthPct = if (enabled) int(Prefs.DOCK_WIDTH) else 92
        val padding = dp((if (enabled) int(Prefs.DOCK_PADDING) else 12).toFloat())
        val dockW = r.width() * widthPct / 100f
        val dockH = size + padding * 2
        val left = r.centerX() - dockW / 2
        val bottom = r.bottom - dp(12f)
        val top = bottom - dockH
        val spec = Materials.Spec(
            style = if (enabled) str(Prefs.DOCK_STYLE) else "default",
            color = Constants.resolveColor(context, int(Prefs.DOCK_COLOR)),
            alphaPercent = if (enabled) int(Prefs.DOCK_ALPHA) else 70,
            cornerPx = dp((if (enabled) int(Prefs.DOCK_RADIUS) else 28).toFloat()),
            strokePx = dp((if (enabled) int(Prefs.DOCK_STROKE) else 0).toFloat()).toInt(),
        )
        if (spec.style == "frosted") drawFrost(canvas, RectF(left, top, left + dockW, bottom), spec.cornerPx)
        Materials.build(context, spec).apply {
            setBounds(left.toInt(), top.toInt(), (left + dockW).toInt(), bottom.toInt())
            draw(canvas)
        }
        val inner = dockW / 4
        for (i in 0..3) {
            drawAppIcon(canvas, left + inner * (i + 0.5f), top + dockH / 2, size, iconColors[i], false)
        }
    }

    /** Approximates a background blur by drawing blurred wallpaper blobs inside the shape. */
    private fun drawFrost(canvas: Canvas, r: RectF, radius: Float) {
        canvas.save()
        val p = Path().apply { addRoundRect(r, radius, radius, Path.Direction.CW) }
        canvas.clipPath(p)
        paint.color = ColorUtils.setAlphaComponent(Color.WHITE, 60)
        canvas.drawRect(r, paint)
        paint.maskFilter = BlurMaskFilter(dp(int(Prefs.DOCK_BLUR).coerceAtLeast(1) / 3f + 4f), BlurMaskFilter.Blur.NORMAL)
        paint.color = ColorUtils.setAlphaComponent(iconColors[4], 120)
        canvas.drawCircle(r.left + r.width() * 0.3f, r.centerY(), r.height() * 0.6f, paint)
        paint.maskFilter = null
        canvas.restore()
    }

    private fun drawFolder(canvas: Canvas, r: RectF) {
        val enabled = bool(Prefs.FOLDER)
        val size = dp(96f)
        val left = r.centerX() - size / 2
        val top = r.centerY() - size / 2 - dp(8f)
        val spec = Materials.Spec(
            style = if (enabled) str(Prefs.FOLDER_STYLE) else "default",
            color = Constants.resolveColor(context, int(Prefs.FOLDER_COLOR)),
            alphaPercent = if (enabled) int(Prefs.FOLDER_ALPHA) else 70,
            cornerPx = dp((if (enabled) int(Prefs.FOLDER_RADIUS) else 22).toFloat()),
            strokePx = dp((if (enabled) int(Prefs.FOLDER_STROKE) else 0).toFloat()).toInt(),
        )
        Materials.build(context, spec).apply {
            setBounds(left.toInt(), top.toInt(), (left + size).toInt(), (top + size).toInt())
            draw(canvas)
        }
        val cols = int(Prefs.FOLDER_COLUMNS).takeIf { it > 0 }?.coerceAtMost(4) ?: 3
        val cell = (size - dp(16f)) / cols
        for (i in 0 until cols * cols) {
            val cx = left + dp(8f) + cell * (i % cols + 0.5f)
            val cy = top + dp(8f) + cell * (i / cols + 0.5f)
            paint.color = iconColors[i % iconColors.size]
            canvas.drawRoundRect(cx - cell * 0.36f, cy - cell * 0.36f, cx + cell * 0.36f, cy + cell * 0.36f, cell * 0.12f, cell * 0.12f, paint)
        }
        if (!bool(Prefs.HIDE_LABELS)) {
            paint.color = ColorUtils.setAlphaComponent(Color.WHITE, 210)
            val y = top + size + dp(10f)
            canvas.drawRoundRect(r.centerX() - dp(26f), y, r.centerX() + dp(26f), y + dp(4f), dp(2f), dp(2f), paint)
        }
    }

    private fun radiusOr(key: String, default: Float) = int(key).takeIf { it > 0 }?.let { dp(it.toFloat()) } ?: dp(default)

    private fun drawControlCenter(canvas: Canvas, r: RectF) {
        paint.color = 0x66000000
        canvas.drawRect(r, paint)
        val gap = dp(10f)
        val tile = (r.height() - gap * 3) / 2
        val tileR = radiusOr(Prefs.CC_TILE_RADIUS, 18f)
        val cardR = radiusOr(Prefs.CC_CARD_RADIUS, 22f)
        val sliderR = radiusOr(Prefs.CC_SLIDER_RADIUS, 22f)
        // Two large cards
        val cardW = (r.width() * 0.56f - gap * 2) / 2
        for (i in 0..1) {
            val l = r.left + gap + i * (cardW + gap)
            paint.color = if (i == 0) accent.accent else ColorUtils.setAlphaComponent(Color.WHITE, 70)
            canvas.drawRoundRect(l, r.top + gap, l + cardW, r.top + gap + tile, cardR, cardR, paint)
        }
        // Four round toggle tiles
        val small = (cardW - gap) / 2
        for (i in 0..3) {
            val l = r.left + gap + (i % 2) * (small + gap) + (i / 2) * (cardW + gap)
            val t = r.top + gap * 2 + tile
            paint.color = if (i % 3 == 0) accent.accent else ColorUtils.setAlphaComponent(Color.WHITE, 70)
            canvas.drawRoundRect(l, t, l + small, t + small, tileR.coerceAtMost(small / 2), tileR.coerceAtMost(small / 2), paint)
        }
        // Two vertical sliders (brightness / volume)
        val sw = (r.width() * 0.44f - gap * 3) / 2
        for (i in 0..1) {
            val l = r.left + r.width() * 0.56f + gap + i * (sw + gap)
            val t = r.top + gap
            val b = r.bottom - gap
            paint.color = ColorUtils.setAlphaComponent(Color.WHITE, 70)
            canvas.drawRoundRect(l, t, l + sw, b, sliderR, sliderR, paint)
            paint.color = Color.WHITE
            val fillTop = t + (b - t) * (if (i == 0) 0.35f else 0.55f)
            canvas.save()
            canvas.clipPath(Path().apply { addRoundRect(RectF(l, t, l + sw, b), sliderR, sliderR, Path.Direction.CW) })
            canvas.drawRect(l, fillTop, l + sw, b, paint)
            canvas.restore()
        }
    }

    private fun drawVolume(canvas: Canvas, r: RectF) {
        val panelR = radiusOr(Prefs.VOL_PANEL_RADIUS, 24f)
        val sliderR = radiusOr(Prefs.VOL_SLIDER_RADIUS, 20f)
        val w = dp(64f)
        val l = r.right - w - dp(20f)
        val t = r.top + dp(14f)
        val b = r.bottom - dp(14f)
        paint.color = ColorUtils.setAlphaComponent(Color.BLACK, if (bool(Prefs.VOL_BLUR)) 60 else 110)
        canvas.drawRoundRect(l - dp(8f), t - dp(8f), l + w + dp(8f), b + dp(8f), panelR, panelR, paint)
        val slider = RectF(l, t, l + w, b - dp(44f))
        paint.color = ColorUtils.setAlphaComponent(Color.WHITE, 60)
        canvas.drawRoundRect(slider, sliderR, sliderR, paint)
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(slider, sliderR, sliderR, Path.Direction.CW) })
        paint.color = Color.WHITE
        canvas.drawRect(slider.left, slider.top + slider.height() * 0.4f, slider.right, slider.bottom, paint)
        canvas.restore()
        paint.color = ColorUtils.setAlphaComponent(Color.WHITE, 60)
        canvas.drawCircle(l + w / 2, b - dp(18f), dp(16f), paint)
    }
}
