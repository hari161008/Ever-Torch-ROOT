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

package com.coolappstore.evertorch.root.Service

import android.annotation.TargetApi
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.coolappstore.evertorch.root.Dialog.TileDialog
import com.coolappstore.evertorch.root.R
import com.coolappstore.evertorch.root.Utils.Constrains.PREF_SELECTED_DEVICE
import com.coolappstore.evertorch.root.Utils.Constrains.PREF_SINGLE_FILE_LOCATION
import com.coolappstore.evertorch.root.Utils.Constrains.PREF_TOGGLE_FILE_LOCATION
import com.coolappstore.evertorch.root.Utils.Constrains.PREF_WHITE_FILE_LOCATION
import com.coolappstore.evertorch.root.Utils.Constrains.PREF_YELLOW_FILE_LOCATION
import com.coolappstore.evertorch.root.Utils.Utils.askRoot
import com.coolappstore.evertorch.root.Utils.Utils.readDevice
import com.coolappstore.evertorch.root.Utils.Utils.runCommand
import org.jetbrains.anko.defaultSharedPreferences

/**
 * Simple on/off "Torch" quick settings tile. Tap toggles the torch at full
 * brightness. Long press opens the floating popup with the full slider +
 * front/rear controls (see SettingsActivity, which intercepts the
 * QS_TILE_PREFERENCES long-press intent for this tile).
 */
@TargetApi(Build.VERSION_CODES.N)
class TorchTileService : TileService() {

    private val TILE_STATUS = "torchTileStatus"

    override fun onStartListening() {
        super.onStartListening()
        val prefs = applicationContext.defaultSharedPreferences
        val selectedDevice = prefs.getString(PREF_SELECTED_DEVICE, "") ?: ""

        if (selectedDevice.isEmpty()) {
            qsTile.label = "Torch Unsupported"
            qsTile.updateTile()
            return
        }

        qsTile.label = "Torch"
        val isOn = prefs.getInt(TILE_STATUS, 0) > 0
        qsTile.state = if (isOn) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        qsTile.updateTile()
    }

    override fun onClick() {
        super.onClick()

        val prefs = applicationContext.defaultSharedPreferences
        val selectedDevice = prefs.getString(PREF_SELECTED_DEVICE, "") ?: ""

        if (selectedDevice.isEmpty()) {
            showDialog(TileDialog.getDialog(this, "Ever Torch [ROOT]",
                    "Your device is not set or supported yet. Open the app to request support."))
            return
        }

        if (!askRoot()) {
            if (!isLocked) {
                showDialog(TileDialog.getDialog(this, "Ever Torch [ROOT]",
                        "Root access is required to run this app. " +
                                "Make sure your device is rooted and root access is enabled."))
            }
            return
        }

        val tile = qsTile
        val editor = prefs.edit()
        val tileStatus = prefs.getInt(TILE_STATUS, 0)

        val whiteLedFileLocation = prefs.getString(PREF_WHITE_FILE_LOCATION, null)
        val yellowLedFileLocation = prefs.getString(PREF_YELLOW_FILE_LOCATION, null)
        val toggleFileLocation = prefs.getString(PREF_TOGGLE_FILE_LOCATION, null)
        val singleLedFileLocation = prefs.getString(PREF_SINGLE_FILE_LOCATION, null)
        val brightnessMax = prefs.getInt("brightnessMax", 0)
        val currentDevice = readDevice(baseContext)

        currentDevice?.let {
            if (tileStatus > 0) {
                val command: String = if (currentDevice.isDualTone) {
                    String.format(getString(R.string.cmd_echo), 0, whiteLedFileLocation) +
                            String.format(getString(R.string.cmd_echo), 0, yellowLedFileLocation) +
                            String.format(getString(R.string.cmd_echo), 0, toggleFileLocation)
                } else {
                    String.format(getString(R.string.cmd_echo), 0, singleLedFileLocation)
                }
                runCommand(command)
                editor.putInt(TILE_STATUS, 0)
                editor.apply()
                tile.state = Tile.STATE_INACTIVE
                tile.updateTile()
            } else {
                val command: String = if (currentDevice.isDualTone) {
                    String.format(getString(R.string.cmd_echo), "0", toggleFileLocation) +
                            getString(R.string.cmd_sleep) +
                            String.format(getString(R.string.cmd_echo), brightnessMax, whiteLedFileLocation) +
                            String.format(getString(R.string.cmd_echo), brightnessMax, yellowLedFileLocation) +
                            String.format(getString(R.string.cmd_echo), brightnessMax, toggleFileLocation)
                } else {
                    String.format(getString(R.string.cmd_echo), "0", singleLedFileLocation) +
                            getString(R.string.cmd_sleep) +
                            String.format(getString(R.string.cmd_echo), brightnessMax, singleLedFileLocation)
                }
                runCommand(command)
                editor.putInt(TILE_STATUS, 1)
                editor.apply()
                tile.state = Tile.STATE_ACTIVE
                tile.updateTile()
            }
        }
    }
}
