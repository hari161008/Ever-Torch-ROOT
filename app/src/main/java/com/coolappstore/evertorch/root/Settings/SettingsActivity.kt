/*
 * This file is part of Godly Torch.
 *
 *     Godly Torch is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Godly Torch is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Godly Torch.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.coolappstore.evertorch.root.Settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings.canDrawOverlays
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.appcompat.widget.Toolbar
import com.coolappstore.evertorch.root.R
import com.coolappstore.evertorch.root.Service.FloatingTorchService
import com.coolappstore.evertorch.root.Service.TorchTileService
import uk.co.chrisjenx.calligraphy.CalligraphyConfig
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Long-pressing a quick settings tile opens whichever activity in this
        // app declares the QS_TILE_PREFERENCES intent filter (this one), with
        // the tile's ComponentName passed as an extra. If it was the Torch
        // tile that was long-pressed, show the floating popup instead of the
        // normal settings screen. Switch to a fully invisible/no-animation
        // theme first so this pass-through activity never visibly flashes
        // open before the floating popup takes over.
        if (intent?.action == "android.service.quicksettings.action.QS_TILE_PREFERENCES") {
            val tileComponent: ComponentName? =
                    intent.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME)
            if (tileComponent?.className == TorchTileService::class.java.name) {
                setTheme(android.R.style.Theme_NoDisplay)
                super.onCreate(savedInstanceState)
                openTorchPopup()
                finish()
                overridePendingTransition(0, 0)
                return
            }
        }

        super.onCreate(savedInstanceState)

        CalligraphyConfig.initDefault(CalligraphyConfig.Builder()
                .setDefaultFontPath("fonts/sans_regular.ttf")
                .setFontAttrId(R.attr.fontPath)
                .build())
        setContentView(R.layout.activity_settings)
        val toolbar: Toolbar = findViewById(R.id.toolbar)

        setSupportActionBar(toolbar)
        supportActionBar?.title = "Settings"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressed() }

        fragmentManager.beginTransaction().replace(R.id.fragment_container,
                PreferenceFragment()).commit()
    }

    private fun openTorchPopup() {
        val canOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            canDrawOverlays(this)
        else true

        if (!canOverlay) {
            val permissionIntent = Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"))
            permissionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(permissionIntent)
            return
        }

        startService(Intent(this, FloatingTorchService::class.java))
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase))
    }
}