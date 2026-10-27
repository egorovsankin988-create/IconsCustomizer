package com.ukm.app.iconscustomizer

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    companion object {
        const val PREF_NAME = "com.ukm.app.iconscustomizer_preferences"
    }

    private lateinit var collapsingToolbar: CollapsingToolbarLayout
    private lateinit var bottomNavView: BottomNavigationView
    private lateinit var cardBottomNav: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        collapsingToolbar = findViewById(R.id.collapsingToolbar)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.materialToolbar))
        bottomNavView = findViewById(R.id.bottom_nav_view)
        cardBottomNav = findViewById(R.id.card_bottom_nav)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.fragment_container)) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(left = bars.left, right = bars.right)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(cardBottomNav) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val lp = v.layoutParams as? androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
            if (lp != null) {
                lp.bottomMargin = (16 * resources.displayMetrics.density).toInt() + bars.bottom
                v.layoutParams = lp
            }
            insets
        }

        bottomNavView.setOnItemSelectedListener { item ->
            val settingsFragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? SettingsFragment
            settingsFragment?.selectTab(item.itemId)
            when (item.itemId) {
                R.id.nav_home -> setScreenTitle(getString(R.string.nav_desktop))
                R.id.nav_settings -> setScreenTitle(getString(R.string.nav_settings))
            }
            true
        }

        supportFragmentManager.addOnBackStackChangedListener {
            val isRoot = supportFragmentManager.backStackEntryCount == 0
            cardBottomNav.isVisible = isRoot
            if (isRoot) {
                val currentTabId = bottomNavView.selectedItemId
                when (currentTabId) {
                    R.id.nav_home -> setScreenTitle(getString(R.string.nav_desktop))
                    R.id.nav_settings -> setScreenTitle(getString(R.string.nav_settings))
                    else -> setScreenTitle(getString(R.string.app_name))
                }
            }
        }

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.fragment_container, SettingsFragment())
                .commit()
        }
    }

    fun setScreenTitle(title: CharSequence) {
        collapsingToolbar.title = title
    }
}
