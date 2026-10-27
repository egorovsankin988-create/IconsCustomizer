package com.ukm.app.iconscustomizer

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.transition.MaterialFadeThrough
import com.google.android.material.transition.MaterialSharedAxis
import com.ukm.app.iconscustomizer.ui.CategoryFragment
import com.ukm.app.iconscustomizer.ui.CategoryGridFragment
import com.ukm.app.iconscustomizer.ui.Catalog
import com.ukm.app.iconscustomizer.ui.Group
import com.ukm.app.iconscustomizer.ui.HomeFragment
import com.ukm.app.iconscustomizer.ui.PendingChanges
import com.ukm.app.iconscustomizer.ui.PrefStore
import com.ukm.app.iconscustomizer.ui.Refreshable
import com.ukm.app.iconscustomizer.ui.Scope
import com.ukm.app.iconscustomizer.ui.ToolsFragment
import io.github.libxposed.service.XposedService

class MainActivity : AppCompatActivity(), App.ServiceStateListener, PendingChanges.Listener {

    companion object {
        const val PREF_NAME = "com.ukm.app.iconscustomizer_preferences"
    }

    private lateinit var appBar: AppBarLayout
    private lateinit var collapsingToolbar: CollapsingToolbarLayout
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var bottomCard: View
    private lateinit var fabApply: ExtendedFloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        appBar = findViewById(R.id.appBar)
        collapsingToolbar = findViewById(R.id.collapsingToolbar)
        bottomNav = findViewById(R.id.bottom_nav_view)
        bottomCard = findViewById(R.id.card_bottom_nav)
        fabApply = findViewById(R.id.fab_apply)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.materialToolbar))

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.fragment_container)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.updatePadding(left = bars.left, right = bars.right)
            insets
        }
        // The bar floats above the gesture area; the nav view itself must not add inset padding,
        // otherwise its icons sit off-centre inside the pill.
        val baseBottom = resources.displayMetrics.density * 14
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.bottom_area)) { v, insets ->
            v.updatePadding(bottom = (baseBottom + insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom).toInt())
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { _, insets -> insets }

        bottomNav.setOnItemSelectedListener { item -> showTab(item); true }
        fabApply.setOnClickListener { applyPending() }

        supportFragmentManager.addOnBackStackChangedListener { updateChrome() }
        if (savedInstanceState == null) {
            showTab(bottomNav.menu.findItem(R.id.nav_home))
        }
        updateChrome()
    }

    override fun onStart() {
        super.onStart()
        App.addServiceStateListener(this)
        PendingChanges.addListener(this)
    }

    override fun onStop() {
        PendingChanges.removeListener(this)
        App.removeServiceStateListener(this)
        super.onStop()
    }

    override fun onServiceStateChanged(service: XposedService?) {
        if (service != null) PrefStore.syncLocalToRemote()
        refreshAll()
    }

    override fun onPendingChanged(scopes: Set<Scope>) {
        runOnUiThread {
            fabApply.text = getString(
                when {
                    Scope.LAUNCHER in scopes && Scope.SYSTEMUI in scopes -> R.string.action_apply_both
                    Scope.SYSTEMUI in scopes -> R.string.action_apply_systemui
                    else -> R.string.action_apply
                }
            )
            if (scopes.isEmpty()) fabApply.hide() else fabApply.show()
        }
    }

    private fun applyPending() {
        val scopes = PendingChanges.current
        fabApply.isEnabled = false
        Thread {
            var ok = true
            if (Scope.LAUNCHER in scopes) ok = UIHelpers.forceRestartLauncher(applicationContext) && ok
            if (Scope.SYSTEMUI in scopes) ok = UIHelpers.restartSystemUi() && ok
            runOnUiThread {
                fabApply.isEnabled = true
                if (ok) PendingChanges.clear()
                android.widget.Toast.makeText(
                    this,
                    if (ok) R.string.toast_applied else R.string.toast_root_denied,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }.start()
    }

    private fun showTab(item: MenuItem) {
        val fragment: Fragment = when (item.itemId) {
            R.id.nav_launcher -> CategoryGridFragment.newInstance(Group.LAUNCHER)
            R.id.nav_system -> CategoryGridFragment.newInstance(Group.SYSTEM)
            R.id.nav_tools -> ToolsFragment()
            else -> HomeFragment()
        }
        fragment.enterTransition = MaterialFadeThrough()
        fragment.exitTransition = MaterialFadeThrough()
        supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.fragment_container, fragment)
            .commit()
        setScreenTitle(tabTitle(item.itemId))
        appBar.setExpanded(true, true)
    }

    private fun tabTitle(itemId: Int): CharSequence =
        if (itemId == R.id.nav_home) getString(R.string.app_name)
        else bottomNav.menu.findItem(itemId)?.title ?: getString(R.string.app_name)

    fun openCategory(id: String) {
        val category = Catalog.byId(id)
        openPage(CategoryFragment.newInstance(id))
        setScreenTitle(getString(category.title))
    }

    fun openPage(fragment: Fragment) {
        val current = supportFragmentManager.findFragmentById(R.id.fragment_container)
        current?.exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        current?.reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
        fragment.enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        fragment.returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
        appBar.setExpanded(true, true)
    }

    private fun updateChrome() {
        val isRoot = supportFragmentManager.backStackEntryCount == 0
        bottomCard.isVisible = isRoot
        supportActionBar?.setDisplayHomeAsUpEnabled(!isRoot)
        if (isRoot) {
            setScreenTitle(tabTitle(bottomNav.selectedItemId))
            refreshAll()
        } else {
            (supportFragmentManager.findFragmentById(R.id.fragment_container) as? CategoryFragment)?.let {
                setScreenTitle(getString(it.category.title))
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    fun refreshAll() {
        (supportFragmentManager.findFragmentById(R.id.fragment_container) as? Refreshable)?.refreshPage()
    }

    fun setScreenTitle(title: CharSequence) {
        collapsingToolbar.title = title
    }
}
