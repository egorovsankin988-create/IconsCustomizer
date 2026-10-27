package com.ukm.app.iconscustomizer

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import androidx.core.graphics.ColorUtils

/**
 * Background "materials" for the dock and folders. Used by the launcher hooks and by the
 * in-app live preview, so what you see in the app is what the launcher draws.
 */
object Materials {

    val STYLES = listOf(
        "default", "glass", "frosted", "liquid", "solid", "monet", "gradient", "amoled", "outline"
    )

    data class Spec(
        val style: String,
        val color: Int,          // resolved ARGB, 0 = automatic
        val alphaPercent: Int,
        val cornerPx: Float,
        val strokePx: Int,
        val blurRadius: Int = 0,
    )

    fun isDark(ctx: Context) =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun withAlpha(color: Int, percent: Int) =
        ColorUtils.setAlphaComponent(color, (Color.alpha(color) * percent.coerceIn(0, 100) / 100f).toInt())

    private fun systemColor(ctx: Context, id: Int, fallback: Int) =
        try { ctx.getColor(id) } catch (_: Throwable) { fallback }

    private fun accent(ctx: Context, dark: Boolean) = systemColor(
        ctx,
        if (dark) android.R.color.system_accent1_600 else android.R.color.system_accent1_200,
        if (dark) 0xFF3F5F90.toInt() else 0xFFA8C8FF.toInt()
    )

    private fun accent3(ctx: Context, dark: Boolean) = systemColor(
        ctx,
        if (dark) android.R.color.system_accent3_600 else android.R.color.system_accent3_200,
        0xFFB58392.toInt()
    )

    fun build(ctx: Context, spec: Spec): Drawable {
        val dark = isDark(ctx)
        val custom = spec.color
        val a = spec.alphaPercent
        val highlight = if (dark) 0x40FFFFFF else 0x26000000

        fun shape(fill: Int, stroke: Int = 0, strokeColor: Int = 0) = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = spec.cornerPx
            setColor(fill)
            if (stroke > 0) setStroke(stroke, strokeColor)
        }

        return when (spec.style) {
            "glass", "frosted" -> {
                val base = if (custom != 0) custom else if (dark) 0xFF1A1B22.toInt() else Color.WHITE
                val glassAlpha = if (spec.style == "frosted") (a + 15).coerceAtMost(100) else a
                shape(withAlpha(base, glassAlpha), spec.strokePx, highlight)
            }
            "liquid" -> {
                val tint = if (custom != 0) custom else accent(ctx, dark)
                val base = shape(withAlpha(ColorUtils.blendARGB(if (dark) Color.BLACK else Color.WHITE, tint, 0.18f), a))
                val sheen = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(withAlpha(Color.WHITE, if (dark) 22 else 45), Color.TRANSPARENT)
                ).apply { cornerRadius = spec.cornerPx }
                val rim = shape(Color.TRANSPARENT, spec.strokePx.coerceAtLeast(1), withAlpha(Color.WHITE, if (dark) 30 else 70))
                LayerDrawable(arrayOf(base, sheen, rim))
            }
            "solid" -> {
                val base = if (custom != 0) custom else if (dark) 0xFF26272E.toInt() else 0xFFF1F0F7.toInt()
                shape(withAlpha(base, a), spec.strokePx, highlight)
            }
            "monet" -> shape(withAlpha(if (custom != 0) custom else accent(ctx, dark), a), spec.strokePx, highlight)
            "gradient" -> GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    withAlpha(if (custom != 0) custom else accent(ctx, dark), a),
                    withAlpha(accent3(ctx, dark), a)
                )
            ).apply {
                cornerRadius = spec.cornerPx
                if (spec.strokePx > 0) setStroke(spec.strokePx, highlight)
            }
            "amoled" -> shape(withAlpha(Color.BLACK, a.coerceAtLeast(85)), spec.strokePx.coerceAtLeast(1), 0x22FFFFFF)
            "outline" -> shape(
                Color.TRANSPARENT,
                spec.strokePx.coerceAtLeast(2),
                if (custom != 0) custom else if (dark) 0xCCFFFFFF.toInt() else 0x99000000.toInt()
            )
            else -> shape(withAlpha(if (dark) 0xFF000000.toInt() else Color.WHITE, if (dark) 25 * a / 70 else 50 * a / 70))
        }
    }

    /**
     * Applies the material to [view]. "frosted" uses a real cross-window background blur
     * (hidden ViewRootImpl API) when available, otherwise it falls back to translucent glass.
     */
    fun apply(view: View, ctx: Context, spec: Spec) {
        if (spec.style == "frosted" && spec.blurRadius > 0) {
            if (view.isAttachedToWindow) {
                if (applyBlur(view, ctx, spec)) return
            } else {
                view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        v.removeOnAttachStateChangeListener(this)
                        if (!applyBlur(v, ctx, spec)) v.background = build(ctx, spec)
                    }

                    override fun onViewDetachedFromWindow(v: View) {}
                })
            }
        }
        view.background = build(ctx, spec)
        view.clipToOutline = true
    }

    private fun applyBlur(view: View, ctx: Context, spec: Spec): Boolean = try {
        val root = View::class.java.getMethod("getViewRootImpl").invoke(view) ?: throw IllegalStateException()
        val blur = root.javaClass.getMethod("createBackgroundBlurDrawable").invoke(root) as Drawable
        val dark = isDark(ctx)
        val base = if (spec.color != 0) spec.color else if (dark) 0xFF1A1B22.toInt() else Color.WHITE
        blur.javaClass.getMethod("setBlurRadius", Int::class.javaPrimitiveType).invoke(blur, spec.blurRadius)
        blur.javaClass.getMethod("setCornerRadius", Float::class.javaPrimitiveType).invoke(blur, spec.cornerPx)
        blur.javaClass.getMethod("setColor", Int::class.javaPrimitiveType)
            .invoke(blur, withAlpha(base, (spec.alphaPercent * 0.6f).toInt()))
        view.background = blur
        view.clipToOutline = true
        true
    } catch (_: Throwable) {
        false
    }
}
