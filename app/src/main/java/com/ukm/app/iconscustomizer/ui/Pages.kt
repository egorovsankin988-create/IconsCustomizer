package com.ukm.app.iconscustomizer.ui

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import com.ukm.app.iconscustomizer.App
import com.ukm.app.iconscustomizer.Prefs
import com.ukm.app.iconscustomizer.R
import com.ukm.app.iconscustomizer.UIHelpers
import io.github.libxposed.service.XposedService
import com.google.android.material.R as MR

/** Number of settings in [category] that differ from their default. */
fun activeCount(category: Category): Int =
    category.items.mapNotNull { it.key }.distinct()
        .count { it != Prefs.ANIM_PRESET && it in Prefs.DEFAULTS && !PrefStore.isDefault(it) }

/** Builds the two-column category tile grid used on Home, Launcher and System pages. */
fun buildTileGrid(fragment: SettingsPageFragment, root: LinearLayout, categories: List<Category>) {
    val ctx = fragment.requireContext()
    val inflater = LayoutInflater.from(ctx)
    val gap = (10 * ctx.resources.displayMetrics.density).toInt()
    categories.chunked(2).forEachIndexed { index, pair ->
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            if (index > 0) topMargin = gap
        })
        pair.forEachIndexed { i, cat ->
            val tile = inflater.inflate(R.layout.item_category_tile, row, false) as MaterialCardView
            val accent = Accent.of(ctx, cat.hue)
            tile.findViewById<View>(R.id.tile_icon_bg).background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(accent.container)
            }
            tile.findViewById<ImageView>(R.id.tile_icon).apply {
                setImageResource(cat.icon)
                imageTintList = ColorStateList.valueOf(accent.onContainer)
            }
            tile.findViewById<TextView>(R.id.tile_title).setText(cat.title)
            tile.findViewById<TextView>(R.id.tile_summary).setText(cat.summary)
            val count = activeCount(cat)
            tile.findViewById<TextView>(R.id.tile_badge).apply {
                isVisible = count > 0
                text = ctx.resources.getQuantityString(R.plurals.active_count, count, count)
            }
            tile.setOnClickListener { (fragment.activity as? com.ukm.app.iconscustomizer.MainActivity)?.openCategory(cat.id) }
            (tile.layoutParams as LinearLayout.LayoutParams).apply {
                if (i == 1) marginStart = gap / 2 else marginEnd = gap / 2
            }
            row.addView(tile)
        }
        if (pair.size == 1) {
            row.addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f).apply { marginStart = gap / 2 })
        }
    }
}

private fun sectionHeader(fragment: SettingsPageFragment, root: LinearLayout, text: Int) {
    val header = LayoutInflater.from(fragment.requireContext()).inflate(R.layout.item_section_header, root, false) as TextView
    header.setText(text)
    root.addView(header)
}

/** Dashboard: module status, quick actions and the categories you have customised. */
class HomeFragment : SettingsPageFragment(), App.ServiceStateListener {

    private var root: View? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        pageView(inflater, container).also { root = it }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        build()
    }

    override fun onStart() {
        super.onStart()
        App.addServiceStateListener(this)
    }

    override fun onStop() {
        App.removeServiceStateListener(this)
        super.onStop()
    }

    override fun onServiceStateChanged(service: XposedService?) = refreshPage()

    override fun refreshPage() {
        if (view != null) build()
    }

    private fun build() {
        val content = root?.content() ?: return
        content.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        val hero = inflater.inflate(R.layout.view_hero, content, false)
        content.addView(hero)
        bindHero(hero)

        val active = Catalog.categories.filter { activeCount(it) > 0 }
        sectionHeader(this, content, R.string.home_your_tweaks)
        if (active.isEmpty()) {
            val empty = inflater.inflate(R.layout.item_pref_info, content, false)
            empty.findViewById<ImageView>(R.id.icon).setImageResource(R.drawable.ic_sparkle)
            empty.findViewById<TextView>(R.id.text).setText(R.string.home_no_tweaks)
            empty.background = GradientDrawable().apply {
                cornerRadius = resources.getDimension(R.dimen.segment_corner_outer)
                setColor(MaterialColors.getColor(empty, MR.attr.colorSurfaceContainer))
            }
            content.addView(empty)
        } else {
            buildTileGrid(this, content, active)
        }

        sectionHeader(this, content, R.string.home_explore)
        buildTileGrid(this, content, Catalog.categories.filter { activeCount(it) == 0 }.take(4))
    }

    private fun bindHero(hero: View) {
        val active = App.mService != null
        val card = hero as MaterialCardView
        val c1 = MaterialColors.getColor(card, if (active) MR.attr.colorPrimaryContainer else MR.attr.colorErrorContainer)
        val c2 = MaterialColors.getColor(card, if (active) MR.attr.colorTertiaryContainer else MR.attr.colorSurfaceContainerHighest)
        val fg = MaterialColors.getColor(card, if (active) MR.attr.colorOnPrimaryContainer else MR.attr.colorOnErrorContainer)
        hero.findViewById<View>(R.id.hero_content).background =
            GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c1, c2))
        hero.findViewById<ImageView>(R.id.hero_icon).apply {
            setImageResource(if (active) R.drawable.ic_sparkle else R.drawable.ic_error)
            imageTintList = ColorStateList.valueOf(fg)
        }
        hero.findViewById<TextView>(R.id.hero_status).apply {
            setText(if (active) R.string.status_active else R.string.status_inactive)
            setTextColor(fg)
        }
        hero.findViewById<TextView>(R.id.hero_summary).apply {
            setText(if (active) R.string.status_active_summary else R.string.status_inactive_summary)
            setTextColor(fg)
        }
        hero.findViewById<View>(R.id.hero_restart_launcher).setOnClickListener { restartLauncher() }
        hero.findViewById<View>(R.id.hero_restart_systemui).setOnClickListener { confirmRestartSystemUi() }

        val chips = hero.findViewById<ChipGroup>(R.id.hero_chips)
        val warning = hero.findViewById<TextView>(R.id.hero_warning).apply { setTextColor(fg) }
        val app = requireContext().applicationContext
        Thread {
            val device = UIHelpers.getDeviceName()
            val os = UIHelpers.getHyperOsVersion()
            val major = UIHelpers.getHyperOsMajorVersion()
            val launcher = UIHelpers.getDefaultLauncher(app)
            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                chips.removeAllViews()
                listOf(
                    R.drawable.ic_smartphone to device,
                    R.drawable.ic_sparkle to "${os ?: "HyperOS ?"} · Android ${Build.VERSION.RELEASE}",
                    R.drawable.ic_home to (launcher?.label ?: getString(R.string.status_launcher_unknown)),
                ).forEach { (icon, text) ->
                    chips.addView(infoPill(icon, text, fg))
                }
                val text = when {
                    major != null && major < 3 -> getString(R.string.status_os_unsupported)
                    launcher != null && !launcher.isSupported -> getString(R.string.status_launcher_unsupported)
                    else -> null
                }
                warning.text = text
                warning.isVisible = text != null
            }
        }.start()
    }
}

private fun HomeFragment.infoPill(icon: Int, text: String, fg: Int): TextView {
    val d = resources.displayMetrics.density
    return TextView(requireContext()).apply {
        this.text = text
        setTextAppearance(MR.style.TextAppearance_Material3_LabelLarge)
        setTextColor(fg)
        gravity = android.view.Gravity.CENTER_VERTICAL
        compoundDrawablePadding = (6 * d).toInt()
        setPadding((10 * d).toInt(), (6 * d).toInt(), (12 * d).toInt(), (6 * d).toInt())
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, icon)?.mutate()?.apply {
            setTint(fg)
            setBounds(0, 0, (16 * d).toInt(), (16 * d).toInt())
        }
        setCompoundDrawablesRelative(drawable, null, null, null)
        background = GradientDrawable().apply {
            cornerRadius = 16 * d
            setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(fg, 28))
        }
    }
}

/** Launcher or System tab: a grid of categories. */
class CategoryGridFragment : SettingsPageFragment() {
    private val group get() = Group.valueOf(requireArguments().getString("group")!!)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        pageView(inflater, container)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = refreshPage()

    override fun refreshPage() {
        val content = view?.content() ?: return
        content.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        val intro = inflater.inflate(R.layout.item_pref_info, content, false)
        intro.findViewById<ImageView>(R.id.icon).setImageResource(if (group == Group.LAUNCHER) R.drawable.ic_home else R.drawable.ic_tune)
        intro.findViewById<TextView>(R.id.text).setText(if (group == Group.LAUNCHER) R.string.intro_launcher else R.string.intro_system)
        content.addView(intro)
        buildTileGrid(this, content, Catalog.categories.filter { it.group == group })
    }

    companion object {
        fun newInstance(group: Group) = CategoryGridFragment().apply { arguments = bundleOf("group" to group.name) }
    }
}

/** A single category rendered from [Catalog]. */
class CategoryFragment : SettingsPageFragment() {
    val category get() = Catalog.byId(requireArguments().getString("id")!!)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        pageView(inflater, container)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (category.id == "animations") syncSystemScales()
        renderer = SettingsRenderer(requireContext(), Accent.of(requireContext(), category.hue), this).also {
            it.render(view.content(), category.sections)
        }
    }

    /** Show the real system values; they can be changed outside the app too. */
    private fun syncSystemScales() {
        val cr = requireContext().contentResolver
        listOf(
            Prefs.WINDOW_SCALE to Settings.Global.WINDOW_ANIMATION_SCALE,
            Prefs.TRANSITION_SCALE to Settings.Global.TRANSITION_ANIMATION_SCALE,
            Prefs.ANIMATOR_SCALE to Settings.Global.ANIMATOR_DURATION_SCALE,
        ).forEach { (key, setting) ->
            val value = (Settings.Global.getFloat(cr, setting, 1f) * 100).toInt()
            if (value != PrefStore.int(key)) PrefStore.put(key, value)
        }
    }

    companion object {
        fun newInstance(id: String) = CategoryFragment().apply { arguments = bundleOf("id" to id) }
    }
}

class ToolsFragment : SettingsPageFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        pageView(inflater, container)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        renderer = SettingsRenderer(requireContext(), Accent.of(requireContext(), null), this).also {
            it.render(view.content(), Catalog.tools)
        }
    }
}
