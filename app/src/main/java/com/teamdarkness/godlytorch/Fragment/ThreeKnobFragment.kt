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

package com.teamdarkness.godlytorch.Fragment

import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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

import com.teamdarkness.godlytorch.R
import com.teamdarkness.godlytorch.Settings.SettingsActivity
import com.teamdarkness.godlytorch.Utils.Constrains.PREF_DOUBLE_TONE_ENABLED
import com.teamdarkness.godlytorch.Utils.Constrains.PREF_FLASH_MODE
import com.teamdarkness.godlytorch.Utils.Constrains.PREF_KNOBS_UI
import com.teamdarkness.godlytorch.Utils.Constrains.PREF_TOGGLE_FILE_LOCATION
import com.teamdarkness.godlytorch.Utils.Constrains.PREF_WHITE_FILE_LOCATION
import com.teamdarkness.godlytorch.Utils.Constrains.PREF_YELLOW_FILE_LOCATION
import com.teamdarkness.godlytorch.Utils.OnFragmentBackPressListener
import com.teamdarkness.godlytorch.Utils.Utils
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
    private var brightnessMax = 0

    private var yellowProgress = 1
    private var whiteProgress = 1
    private var masterProgress = 1

    private var flashMode = "rear"
    private var cameraManager: CameraManager? = null
    private var frontFlashCameraId: String? = null
    private var frontFlashChecked = false
    private var frontFlashOn = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        val prefs = context?.defaultSharedPreferences
        val useKnobsUi = prefs?.getBoolean(PREF_KNOBS_UI, false) ?: false

        cameraManager = context?.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

        // Inflate the layout for this fragment
        val layoutRes = if (useKnobsUi) R.layout.fragment_three_knob_classic else R.layout.fragment_three_knob
        val view = inflater.inflate(layoutRes, container, false)

        val settingsButton: ImageButton = view.findViewById(R.id.settingsButton)
        val rearFlashButton: ImageButton = view.findViewById(R.id.rearFlashButton)
        val frontFlashButton: ImageButton = view.findViewById(R.id.frontFlashButton)

        prefs?.let {
            // get torch file location
            whiteLedFileLocation = prefs.getString(PREF_WHITE_FILE_LOCATION, null) ?: ""
            yellowLedFileLocation = prefs.getString(PREF_YELLOW_FILE_LOCATION, null) ?: ""
            toggleFileLocation = prefs.getString(PREF_TOGGLE_FILE_LOCATION, null) ?: ""

            doubleTapEnabled = prefs.getBoolean(PREF_DOUBLE_TONE_ENABLED, true)

            // get max brightness
            brightnessMax = prefs.getInt("brightnessMax", 0)
        }

        settingsButton.setOnClickListener {
            val intent = Intent(context, SettingsActivity::class.java)
            startActivity(intent)
        }

        setupFlashModeButtons(rearFlashButton, frontFlashButton)

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
            if (flashMode != "rear") {
                // switching away from front flash, make sure the screen overlay is cleared
                setFrontFlashActive(false)
            }
            flashMode = "rear"
            prefs?.edit()?.putString(PREF_FLASH_MODE, "rear")?.apply()
            refreshIcons("rear")
        }

        frontFlashButton.setOnClickListener {
            flashMode = "front"
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
                if (whiteValue != whiteValueOld || yellowValue != yellowValueOld) {
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
                    whiteValue = (255 / 20) * (progress - 1)
                    if (whiteValue > 225)
                        whiteValue = 225
                }
                whiteProgress = progress
            }
        }
        whiteSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                if (whiteValue != whiteValueOld) {
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
                    yellowValue = (255 / 20) * (progress - 1)
                    if (yellowValue > 225)
                        yellowValue = 225
                }
                yellowProgress = progress
            }
        }
        yellowSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                if (yellowValue != yellowValueOld) {
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
                if (whiteValue != whiteValueOld || yellowValue != yellowValueOld) {
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
                        whiteValue = (255 / 20) * (progress - 1)
                        if (whiteValue > 225)
                            whiteValue = 225
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
                if (whiteValue != whiteValueOld) {
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
                        yellowValue = (255 / 20) * (progress - 1)
                        if (yellowValue > 225)
                            yellowValue = 225
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
                if (yellowValue != yellowValueOld) {
                    when {
                        yellowOn || whiteOn -> controlLed(whiteValue, yellowValue, true)
                        else -> controlLed(whiteValue, yellowValue, false)
                    }
                    yellowValueOld = yellowValue
                }
            }
        })
    }

    private fun controlLed(whiteLed: Int = 0, yellowLed: Int = 0, torchState: Boolean = false) {
        if (flashMode == "front") {
            setFrontFlashActive(torchState)
            return
        }

        // make sure the front flash is off before driving the rear LED
        setFrontFlashActive(false)

        if (whiteLedFileLocation.isEmpty() || yellowLedFileLocation.isEmpty() || toggleFileLocation.isEmpty())
            return
        var torch = 0
        if (torchState)
            torch = this.brightnessMax

        val command: String = String.format(getString(R.string.cmd_echo), "0", toggleFileLocation) +
                getString(R.string.cmd_sleep) +
                String.format(getString(R.string.cmd_echo), whiteLed, whiteLedFileLocation) +
                String.format(getString(R.string.cmd_echo), yellowLed, yellowLedFileLocation) +
                String.format(getString(R.string.cmd_echo), torch, toggleFileLocation)
        return Utils.runCommand(command)
    }

    /**
     * Finds the ID of the front-facing camera that actually has a flash unit attached
     * (only present on a handful of devices). Result is cached after the first lookup.
     */
    private fun getFrontFlashCameraId(): String? {
        if (frontFlashChecked) return frontFlashCameraId
        frontFlashChecked = true

        val manager = cameraManager ?: return null
        try {
            for (id in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(id)
                val facingFront = characteristics.get(CameraCharacteristics.LENS_FACING) ==
                        CameraCharacteristics.LENS_FACING_FRONT
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                if (facingFront && hasFlash) {
                    frontFlashCameraId = id
                    break
                }
            }
        } catch (e: CameraAccessException) {
            frontFlashCameraId = null
        }
        return frontFlashCameraId
    }

    /** Turns the phone's real front-facing LED flash on or off. */
    private fun setFrontFlashActive(active: Boolean) {
        val ctx = context ?: return
        val manager = cameraManager ?: return

        if (ActivityCompat.checkSelfPermission(ctx, android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            if (active) {
                requestPermissions(arrayOf(android.Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
            }
            return
        }

        val id = getFrontFlashCameraId()
        if (id == null) {
            if (active) {
                Toast.makeText(ctx, "This device has no front flash", Toast.LENGTH_SHORT).show()
            }
            return
        }

        try {
            manager.setTorchMode(id, active)
            frontFlashOn = active
        } catch (e: CameraAccessException) {
            frontFlashOn = false
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE &&
                grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                flashMode == "front") {
            setFrontFlashActive(true)
        }
    }

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 4242
    }

    override fun onDestroyView() {
        if (frontFlashOn) setFrontFlashActive(false)
        super.onDestroyView()
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
