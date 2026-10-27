package com.ukm.app.iconscustomizer.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/** Where a setting takes effect, which decides how it is applied after a change. */
enum class Scope { LAUNCHER, SYSTEMUI, SYSTEM_SETTINGS, APP }

/** Shows an item only while [key]'s current value satisfies [test]. */
class Dependency(val key: String, val test: (Any?) -> Boolean) {
    companion object {
        fun on(key: String) = Dependency(key) { it == true }
        fun oneOf(key: String, vararg values: String) = Dependency(key) { it in values }
    }
}

sealed class Item {
    open val key: String? get() = null
    open val dependsOn: List<Dependency> get() = emptyList()
}

data class SwitchItem(
    override val key: String,
    @StringRes val title: Int,
    @StringRes val summary: Int,
    @DrawableRes val icon: Int,
    val scope: Scope,
    override val dependsOn: List<Dependency> = emptyList(),
    val beta: Boolean = false,
) : Item()

data class SliderItem(
    override val key: String,
    @StringRes val title: Int,
    @DrawableRes val icon: Int,
    val min: Int,
    val max: Int,
    val scope: Scope,
    val unit: String = "",
    val step: Int = 1,
    @StringRes val summary: Int = 0,
    /** Label shown instead of the number when the value is [zeroValue], e.g. "System". */
    @StringRes val zeroLabel: Int = 0,
    val zeroValue: Int = 0,
    /** Displayed value is value / divisor, e.g. 50 with divisor 100 shows "0.5". */
    val divisor: Int = 1,
    override val dependsOn: List<Dependency> = emptyList(),
    val beta: Boolean = false,
) : Item()

data class Choice(val value: String, @StringRes val label: Int)

data class ChoiceItem(
    override val key: String,
    @StringRes val title: Int,
    @DrawableRes val icon: Int,
    val choices: List<Choice>,
    val scope: Scope,
    override val dependsOn: List<Dependency> = emptyList(),
) : Item()

data class ColorItem(
    override val key: String,
    /** Key the colour picker stores the palette name under. */
    val typeKey: String,
    @StringRes val title: Int,
    @DrawableRes val icon: Int,
    val scope: Scope,
    override val dependsOn: List<Dependency> = emptyList(),
) : Item()

data class ActionItem(
    val id: String,
    @StringRes val title: Int,
    @StringRes val summary: Int,
    @DrawableRes val icon: Int,
    override val dependsOn: List<Dependency> = emptyList(),
    val danger: Boolean = false,
) : Item()

enum class PreviewKind { DOCK, FOLDER, CONTROL_CENTER, VOLUME, ICONS }

data class PreviewItem(val kind: PreviewKind) : Item()

data class InfoItem(@StringRes val text: Int, @DrawableRes val icon: Int) : Item()

data class Section(@StringRes val title: Int, val items: List<Item>)

enum class Group { LAUNCHER, SYSTEM }

data class Category(
    val id: String,
    @StringRes val title: Int,
    @StringRes val summary: Int,
    @DrawableRes val icon: Int,
    /** Seed hue; harmonized with the dynamic color scheme at runtime. */
    val hue: Int,
    val group: Group,
    val sections: List<Section>,
) {
    val items get() = sections.flatMap { it.items }
}
