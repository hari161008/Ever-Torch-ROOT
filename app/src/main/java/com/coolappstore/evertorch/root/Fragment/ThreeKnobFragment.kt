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

package com.coolappstore.evertorch.root.Fragment

import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.widget.ImageViewCompat
import com.google.android.material.slider.Slider
import com.sdsmdg.harjot.crollerTest.Croller
import com.sdsmdg.harjot.crollerTest.OnCrollerChangeListener

import com.coolappstore.evertorch.root.R
import com.coolappstore.evertorch.root.Settings.SettingsActivity
import com.coolappstore.evertorch.root.Utils.Constrains.PREF_DOUBLE_TONE_ENABLED
import com.coolappstore.evertorch.root.Utils.Constrains.PREF_FLASH_MODE
import com.coolappstore.evertorch.root.Utils.Constrains.PREF_FRONT_BRIGHTNESS_MAX
import com.coolappstore.evertorch.root.Utils.Constrains.PREF_FRONT_SINGLE_FILE_LOCATION
import com.coolappstore.evertorch.root.Utils.Constrains.PREF_FRONT_TOGGLE_FILE_LOCATION
import com.coolappstore.evertorch.root.Utils.Constrains.PREF_FRONT_WHITE_FILE_LOCATION
import com.coolappstore.evertorch.root.Utils.Constrains.PREF_FRONT_YELLOW_FILE_LOCATION
import com.coolappstore.evertorch.root.Utils.Constrains.PREF_KNOBS_UI
import com.coolappstore.evertorch.root.Utils.Constrains.PREF_TOGGLE_FILE_LOCATION
import com.coolappstore.evertorch.root.Utils.Constrains.PREF_WHITE_FILE_LOCATION
import com.coolappstore.evertorch.root.Utils.Constrains.PREF_YELLOW_FILE_LOCATION
import com.coolappstore.evertorch.root.Utils.OnFragmentBackPressListener
import com.coolappstore.evertorch.root.Utils.Utils
import org.jetbrains.anko.defaultSharedPreferences

class ThreeKnobFragment : Fragment(), OnFragmentBackPressListener {

    private var doubleBackToExitPressedOnce = false
    private var whiteSingleTap = false
    private var yellowSingleTap = false
    private var masterSingleTap = false

    private var whiteOn = false
    private var yellowOn = false
    private var doubleTapEnabled = false

    private var yellowValue = 0
    private var whiteValue = 0
    private var yellowValueOld = 0
    private var whiteValueOld = 0

    private var whiteLedFileLocation = ""
    private var yellowLedFileLocation = ""
    private var toggleFileLocation = ""
    private var rearBrightnessMax = 0

    private var frontWhiteLedFileLocation = ""
    private var frontYellowLedFileLocation = ""
    private var frontSingleLedFileLocation = ""
    private var frontToggleFileLocation = ""
    private var frontBrightnessMax = 0

    private var yellowProgress = 1
    private var whiteProgress = 1
    private var masterProgress = 1

    private var flashMode = "rear"
    private var brightnessMax = 0

    // Front flash is turned on/off the same way normal camera & flashlight apps
    // do it: through the official Camera2 CameraManager.setTorchMode() call on
    // whichever camera id reports a front-facing flash unit. Once it's engaged
    // that way, the sysfs brightness files are used only to fine-tune the
    // intensity, instead of trying to toggle the LED on/off ourselves - this is
    // what avoids the front/rear LED mix-up some devices (e.g. Moto Z2 Play)
    // show when the LED is engaged purely through raw sysfs writes.
    private var cameraManager: CameraManager? = null
    private var frontFlashCameraId: String? = null
    private var frontFlashCameraIdChecked = false
    private var frontTorchEngaged = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        val prefs = context?.defaultSharedPreferences
        val useKnobsUi = prefs?.getBoolean(PREF_KNOBS_UI, false) ?: false

        // Inflate the layout for this fragment
        val layoutRes = if (useKnobsUi) R.layout.fragment_three_knob_classic else R.layout.fragment_three_knob
        val view = inflater.inflate(layoutRes, container, false)

        val settingsButton: ImageButton = view.findViewById(R.id.settingsButton)
        val rearFlashButton: ImageButton = view.findViewById(R.id.rearFlashButton)
        val frontFlashButton: ImageButton = view.findViewById(R.id.frontFlashButton)

        prefs?.let {
            // get rear torch file locations
            whiteLedFileLocation = prefs.getString(PREF_WHITE_FILE_LOCATION, null) ?: ""
            yellowLedFileLocation = prefs.getString(PREF_YELLOW_FILE_LOCATION, null) ?: ""
            toggleFileLocation = prefs.getString(PREF_TOGGLE_FILE_LOCATION, null) ?: ""
            rearBrightnessMax = prefs.getInt("brightnessMax", 0)

            // get front torch file locations (root level, same mechanism as the rear LED)
            frontWhiteLedFileLocation = prefs.getString(PREF_FRONT_WHITE_FILE_LOCATION, null) ?: ""
            frontYellowLedFileLocation = prefs.getString(PREF_FRONT_YELLOW_FILE_LOCATION, null) ?: ""
            frontSingleLedFileLocation = prefs.getString(PREF_FRONT_SINGLE_FILE_LOCATION, null) ?: ""
            frontToggleFileLocation = prefs.getString(PREF_FRONT_TOGGLE_FILE_LOCATION, null) ?: ""
            frontBrightnessMax = prefs.getString(PREF_FRONT_BRIGHTNESS_MAX, null)?.toIntOrNull() ?: 0

            doubleTapEnabled = prefs.getBoolean(PREF_DOUBLE_TONE_ENABLED, true)
        }

        settingsButton.setOnClickListener {
            val intent = Intent(context, SettingsActivity::class.java)
            startActivity(intent)
        }

        setupFlashModeButtons(rearFlashButton, frontFlashButton)
        brightnessMax = rearBrightnessMax

        if (useKnobsUi) {
            setupClassicKnobs(view)
        } else {
            setupSliders(view)
        }

        return view
    }

    private fun setupFlashModeButtons(rearFlashButton: ImageButton, frontFlashButton: ImageButton) {
        val prefs = context?.defaultSharedPreferences
        val activeColor = ColorStateList.valueOf(android.graphics.Color.parseColor("#FFE240"))
        val inactiveColor = ColorStateList.valueOf(android.graphics.Color.parseColor("#80FFFFFF"))

        fun refreshIcons(mode: String) {
            if (mode == "front") {
                ImageViewCompat.setImageTintList(frontFlashButton, activeColor)
                ImageViewCompat.setImageTintList(rearFlashButton, inactiveColor)
            } else {
                ImageViewCompat.setImageTintList(rearFlashButton, activeColor)
                ImageViewCompat.setImageTintList(frontFlashButton, inactiveColor)
            }
        }

        flashMode = prefs?.getString(PREF_FLASH_MODE, "rear") ?: "rear"
        refreshIcons(flashMode)

        rearFlashButton.setOnClickListener {
            controlLed(0, 0, false)
            flashMode = "rear"
            brightnessMax = rearBrightnessMax
            prefs?.edit()?.putString(PREF_FLASH_MODE, "rear")?.apply()
            refreshIcons("rear")
        }

        frontFlashButton.setOnClickListener {
            controlLed(0, 0, false)
            flashMode = "front"
            brightnessMax = rearBrightnessMax
            prefs?.edit()?.putString(PREF_FLASH_MODE, "front")?.apply()
            refreshIcons("front")
        }
    }

    private fun setupSliders(view: View) {
        val masterSlider: Slider = view.findViewById(R.id.masterSlider)
        val whiteSlider: Slider = view.findViewById(R.id.whiteSlider)
        val yellowSlider: Slider = view.findViewById(R.id.yellowSlider)

        masterSlider.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val progress = value.toInt()
            if (progress != masterProgress) {
                if (progress == 1) {
                    whiteSlider.isEnabled = true
                    yellowSlider.isEnabled = true
                    whiteValue = 0
                    yellowValue = 0
                    whiteOn = false
                    yellowOn = false
                } else {
                    whiteSlider.isEnabled = false
                    yellowSlider.isEnabled = false
                    yellowOn = true
                    whiteOn = true
                    whiteValue = (brightnessMax / 20) * (progress - 1)
                    if (whiteValue > brightnessMax)
                        whiteValue = brightnessMax
                    yellowValue = (brightnessMax / 20) * (progress - 1)
                    if (yellowValue > brightnessMax)
                        yellowValue = brightnessMax
                }
                masterProgress = progress
            }
        }
        masterSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                if (flashMode == "front" || whiteValue != whiteValueOld || yellowValue != yellowValueOld) {
                    if (yellowOn)
                        controlLed(whiteValue, yellowValue, true)
                    else
                        controlLed(whiteValue, yellowValue, false)
                    whiteValueOld = whiteValue
                    yellowValueOld = yellowValue
                }
            }
        })

        whiteSlider.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val progress = value.toInt()
            if (progress != whiteProgress) {
                if (progress == 1) {
                    if (yellowSlider.value.toInt() == 1)
                        masterSlider.isEnabled = true
                    whiteValue = 0
                    whiteOn = false
                } else {
                    masterSlider.isEnabled = false
                    whiteOn = true
                    whiteValue = (brightnessMax / 20) * (progress - 1)
                    if (whiteValue > brightnessMax)
                        whiteValue = brightnessMax
                }
                whiteProgress = progress
            }
        }
        whiteSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                if (flashMode == "front" || whiteValue != whiteValueOld) {
                    when {
                        whiteOn || yellowOn -> controlLed(whiteValue, yellowValue, true)
                        else -> controlLed(whiteValue, yellowValue, false)
                    }
                    whiteValueOld = whiteValue
                }
            }
        })

        yellowSlider.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val progress = value.toInt()
            if (progress != yellowProgress) {
                if (progress == 1) {
                    if (whiteSlider.value.toInt() == 1)
                        masterSlider.isEnabled = true
                    yellowValue = 0
                    yellowOn = false
                } else {
                    masterSlider.isEnabled = false
                    yellowOn = true
                    yellowValue = (brightnessMax / 20) * (progress - 1)
                    if (yellowValue > brightnessMax)
                        yellowValue = brightnessMax
                }
                yellowProgress = progress
            }
        }
        yellowSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                if (flashMode == "front" || yellowValue != yellowValueOld) {
                    when {
                        yellowOn || whiteOn -> controlLed(whiteValue, yellowValue, true)
                        else -> controlLed(whiteValue, yellowValue, false)
                    }
                    yellowValueOld = yellowValue
                }
            }
        })
    }

    private fun setupClassicKnobs(view: View) {
        val masterCroller: Croller = view.findViewById(R.id.bothCroller)
        val whiteCroller: Croller = view.findViewById(R.id.whiteCroller)
        val yellowCroller: Croller = view.findViewById(R.id.yellowCroller)

        masterCroller.setOnCrollerChangeListener(object : OnCrollerChangeListener {

            override fun onProgressChanged(croller: Croller?, progress: Int) {

                if (progress != masterProgress) {
                    if (progress == 1) {
                        whiteCroller.isEnabled = true
                        yellowCroller.isEnabled = true
                        whiteValue = 0
                        yellowValue = 0
                        whiteOn = false
                        yellowOn = false
                    } else {
                        whiteCroller.isEnabled = false
                        yellowCroller.isEnabled = false
                        yellowOn = true
                        whiteOn = true
                        whiteValue = (brightnessMax / 20) * (progress - 1)
                        if (whiteValue > brightnessMax)
                            whiteValue = brightnessMax
                        yellowValue = (brightnessMax / 20) * (progress - 1)
                        if (yellowValue > brightnessMax)
                            yellowValue = brightnessMax
                    }
                    masterProgress = progress
                }
            }

            override fun onTap(croller: Croller?) {
                if (doubleTapEnabled) {
                    if (masterSingleTap) {
                        if (masterProgress > 1)
                            masterCroller.progress = 1
                        else
                            masterCroller.progress = 20
                    }
                    masterSingleTap = true
                    Handler().postDelayed({ masterSingleTap = false }, 300)
                }
            }

            override fun onStartTrackingTouch(croller: Croller?) {
            }

            override fun onStopTrackingTouch(croller: Croller?) {
                if (flashMode == "front" || whiteValue != whiteValueOld || yellowValue != yellowValueOld) {
                    if (yellowOn)
                        controlLed(whiteValue, yellowValue, true)
                    else
                        controlLed(whiteValue, yellowValue, false)
                    whiteValueOld = whiteValue
                    yellowValueOld = yellowValue
                }
            }
        })

        whiteCroller.setOnCrollerChangeListener(object : OnCrollerChangeListener {
            override fun onProgressChanged(croller: Croller?, progress: Int) {
                if (progress != whiteProgress) {
                    if (progress == 1) {
                        if (yellowCroller.progress == 1)
                            masterCroller.isEnabled = true
                        whiteValue = 0
                        whiteOn = false
                    } else {
                        masterCroller.isEnabled = false
                        whiteOn = true
                        whiteValue = (brightnessMax / 20) * (progress - 1)
                        if (whiteValue > brightnessMax)
                            whiteValue = brightnessMax
                    }
                    whiteProgress = progress
                }
            }

            override fun onTap(croller: Croller?) {
                if (doubleTapEnabled) {
                    if (whiteSingleTap) {
                        if (whiteOn)
                            whiteCroller.progress = 1
                        else
                            whiteCroller.progress = 20
                    }
                    whiteSingleTap = true
                    Handler().postDelayed({ whiteSingleTap = false }, 300)
                }
            }

            override fun onStartTrackingTouch(croller: Croller?) {
            }

            override fun onStopTrackingTouch(croller: Croller?) {
                if (flashMode == "front" || whiteValue != whiteValueOld) {
                    when {
                        whiteOn || yellowOn -> controlLed(whiteValue, yellowValue, true)
                        else -> controlLed(whiteValue, yellowValue, false)
                    }
                    whiteValueOld = whiteValue
                }
            }
        })

        yellowCroller.setOnCrollerChangeListener(object : OnCrollerChangeListener {
            override fun onProgressChanged(croller: Croller?, progress: Int) {
                if (progress != yellowProgress) {
                    if (progress == 1) {
                        if (whiteCroller.progress == 1)
                            masterCroller.isEnabled = true
                        yellowValue = 0
                        yellowOn = false
                    } else {
                        masterCroller.isEnabled = false
                        yellowOn = true
                        yellowValue = (brightnessMax / 20) * (progress - 1)
                        if (yellowValue > brightnessMax)
                            yellowValue = brightnessMax
                    }
                    yellowProgress = progress
                }
            }

            override fun onTap(croller: Croller?) {
                if (doubleTapEnabled) {
                    if (yellowSingleTap) {
                        if (yellowOn)
                            yellowCroller.progress = 1
                        else
                            yellowCroller.progress = 20
                    }
                    yellowSingleTap = true
                    Handler().postDelayed({ yellowSingleTap = false }, 300)
                }
            }

            override fun onStartTrackingTouch(croller: Croller?) {
            }

            override fun onStopTrackingTouch(croller: Croller?) {
                if (flashMode == "front" || yellowValue != yellowValueOld) {
                    when {
                        yellowOn || whiteOn -> controlLed(whiteValue, yellowValue, true)
                        else -> controlLed(whiteValue, yellowValue, false)
                    }
                    yellowValueOld = yellowValue
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        cameraManager = context?.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    }

    override fun onPause() {
        super.onPause()
        // Front flash intentionally stays engaged after leaving this screen
        // (matches how the rear torch behaves) instead of being force-stopped.
    }

    /**
     * Looks up the camera id (if any) that reports itself as front-facing AND
     * has a flash unit attached, exactly how a normal camera/flashlight app
     * would find the front flash. Cached after the first lookup.
     */
    private fun ensureFrontFlashCameraId() {
        if (frontFlashCameraIdChecked) return
        frontFlashCameraIdChecked = true
        val manager = cameraManager ?: return
        try {
            for (id in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                if (facing == CameraCharacteristics.LENS_FACING_FRONT && hasFlash) {
                    frontFlashCameraId = id
                    return
                }
            }
        } catch (e: Exception) {
        }
    }

    private fun controlLed(whiteLed: Int = 0, yellowLed: Int = 0, torchState: Boolean = false) {
        if (flashMode == "front") {
            controlFrontLed(whiteLed, yellowLed, torchState)
            return
        }

        val whiteFile = whiteLedFileLocation
        val yellowFile = yellowLedFileLocation
        val toggleFile = toggleFileLocation
        val maxBrightness = rearBrightnessMax

        if (whiteFile.isEmpty() || yellowFile.isEmpty() || toggleFile.isEmpty())
            return

        var torch = 0
        if (torchState)
            torch = maxBrightness

        val command: String = String.format(getString(R.string.cmd_echo), "0", toggleFile) +
                getString(R.string.cmd_sleep) +
                String.format(getString(R.string.cmd_echo), whiteLed, whiteFile) +
                String.format(getString(R.string.cmd_echo), yellowLed, yellowFile) +
                String.format(getString(R.string.cmd_echo), torch, toggleFile)
        return Utils.runCommand(command)
    }

    /**
     * Turns the front flash on/off the same way normal apps do: via the
     * official CameraManager.setTorchMode() call on the front-facing camera's
     * flash unit. Once engaged this way, the brightness/intensity is fine
     * tuned by writing straight to the front LED's sysfs brightness file(s) -
     * no toggle-file juggling needed, and no front/rear LED mix-up, since the
     * kernel already knows this LED belongs to the front camera.
     *
     * If this device doesn't expose a front-facing flash unit through
     * Camera2, we fall back to the old pure sysfs control as a best effort.
     */
    private fun controlFrontLed(whiteLed: Int, yellowLed: Int, torchState: Boolean) {
        ensureFrontFlashCameraId()
        val camId = frontFlashCameraId

        if (camId == null) {
            controlFrontLedViaSysfs(whiteLed, yellowLed, torchState)
            return
        }

        if (torchState != frontTorchEngaged) {
            try {
                cameraManager?.setTorchMode(camId, torchState)
                frontTorchEngaged = torchState
            } catch (e: Exception) {
                // Official API failed (camera in use, etc.) - fall back to sysfs only.
                controlFrontLedViaSysfs(whiteLed, yellowLed, torchState)
                return
            }
        }

        // Once the front flash is engaged through the official camera API,
        // this device's physical front LED responds to the SAME sysfs nodes
        // normally used for the rear torch (confirmed: using rear mode's
        // sliders while the front flash is on from a 3rd-party app adjusts
        // the front flash's intensity correctly). So intensity is driven
        // through those rear LED files here, not the separate front files.
        if (torchState) {
            // Give the kernel a brief moment to engage the front flash through
            // the official path before we push a brightness value onto the
            // shared LED node.
            Handler().postDelayed({
                adjustEngagedFrontBrightness(whiteLed, yellowLed, true)
            }, 80)
        } else {
            adjustEngagedFrontBrightness(0, 0, false)
        }
    }

    /**
     * Writes the intensity to the rear torch's own sysfs nodes (white/yellow/
     * toggle), which is what actually drives the physical front LED once the
     * front flash has been engaged through the official Camera2 torch call.
     */
    private fun adjustEngagedFrontBrightness(whiteLed: Int, yellowLed: Int, torchState: Boolean) {
        if (whiteLedFileLocation.isEmpty() || yellowLedFileLocation.isEmpty() || toggleFileLocation.isEmpty())
            return

        val torch = if (torchState) rearBrightnessMax else 0

        val command: String = String.format(getString(R.string.cmd_echo), "0", toggleFileLocation) +
                getString(R.string.cmd_sleep) +
                String.format(getString(R.string.cmd_echo), whiteLed, whiteLedFileLocation) +
                String.format(getString(R.string.cmd_echo), yellowLed, yellowLedFileLocation) +
                String.format(getString(R.string.cmd_echo), torch, toggleFileLocation)
        Utils.runCommand(command)
    }

    /**
     * Fallback used only when this device doesn't expose a front-facing flash
     * unit through Camera2, so the official on/off switch isn't available.
     * Drives the sysfs nodes directly, supporting dual-tone, single-tone, and
     * toggle-only front flash layouts.
     */
    private fun controlFrontLedViaSysfs(whiteLed: Int, yellowLed: Int, torchState: Boolean) {
        val maxBrightness = frontBrightnessMax
        val torch = if (torchState) maxBrightness else 0

        when {
            frontWhiteLedFileLocation.isNotEmpty() && frontYellowLedFileLocation.isNotEmpty() && frontToggleFileLocation.isNotEmpty() -> {
                val command = String.format(getString(R.string.cmd_echo), "0", frontToggleFileLocation) +
                        getString(R.string.cmd_sleep) +
                        String.format(getString(R.string.cmd_echo), whiteLed, frontWhiteLedFileLocation) +
                        String.format(getString(R.string.cmd_echo), yellowLed, frontYellowLedFileLocation) +
                        String.format(getString(R.string.cmd_echo), torch, frontToggleFileLocation)
                Utils.runCommand(command)
            }

            frontSingleLedFileLocation.isNotEmpty() && frontToggleFileLocation.isNotEmpty() -> {
                val brightnessValue = if (torchState) maxOf(whiteLed, yellowLed).let { if (it > 0) it else maxBrightness } else 0
                val command = String.format(getString(R.string.cmd_echo), "0", frontToggleFileLocation) +
                        getString(R.string.cmd_sleep) +
                        String.format(getString(R.string.cmd_echo), brightnessValue, frontSingleLedFileLocation) +
                        String.format(getString(R.string.cmd_echo), torch, frontToggleFileLocation)
                Utils.runCommand(command)
            }

            frontSingleLedFileLocation.isNotEmpty() -> {
                val brightnessValue = if (torchState) maxOf(whiteLed, yellowLed).let { if (it > 0) it else maxBrightness } else 0
                Utils.runCommand(String.format(getString(R.string.cmd_echo), brightnessValue, frontSingleLedFileLocation))
            }

            frontToggleFileLocation.isNotEmpty() -> {
                Utils.runCommand(String.format(getString(R.string.cmd_echo), torch, frontToggleFileLocation))
            }

            else -> return
        }
    }


    override fun onBackPressed() {
        if (doubleBackToExitPressedOnce) {

            val alertDialogBuilder = ProgressDialog.show(context, "Quit", "Please wait...")
            alertDialogBuilder.setCancelable(false)
            alertDialogBuilder.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            alertDialogBuilder.show()

            controlLed(0)

            Handler().postDelayed({
                alertDialogBuilder.dismiss()
                activity?.finishAffinity()
            }, 300)

            return
        }

        this.doubleBackToExitPressedOnce = true
        Toast.makeText(context, "Please click BACK again to exit", Toast.LENGTH_SHORT).show()

        Handler().postDelayed({ doubleBackToExitPressedOnce = false }, 2000)
    }
}
