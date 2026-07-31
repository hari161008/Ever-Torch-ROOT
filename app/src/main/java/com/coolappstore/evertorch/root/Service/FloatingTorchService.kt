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

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import androidx.core.widget.ImageViewCompat
import com.google.android.material.slider.Slider

import com.coolappstore.evertorch.root.R
import com.coolappstore.evertorch.root.Utils.Constrains.PREF_FLASH_MODE
import com.coolappstore.evertorch.root.Utils.Constrains.PREF_FRONT_BRIGHTNESS_MAX
import com.coolappstore.evertorch.root.Utils.Constrains.PREF_FRONT_FLASH_DESIRED_ON
import com.coolappstore.evertorch.root.Utils.Constrains.PREF_FRONT_SINGLE_FILE_LOCATION
import com.coolappstore.evertorch.root.Utils.Constrains.PREF_FRONT_TOGGLE_FILE_LOCATION
import com.coolappstore.evertorch.root.Utils.Constrains.PREF_FRONT_WHITE_FILE_LOCATION
import com.coolappstore.evertorch.root.Utils.Constrains.PREF_FRONT_YELLOW_FILE_LOCATION
import com.coolappstore.evertorch.root.Utils.Constrains.PREF_TOGGLE_FILE_LOCATION
import com.coolappstore.evertorch.root.Utils.Constrains.PREF_WHITE_FILE_LOCATION
import com.coolappstore.evertorch.root.Utils.Constrains.PREF_YELLOW_FILE_LOCATION
import com.coolappstore.evertorch.root.Utils.Utils
import androidx.core.app.NotificationCompat
import org.jetbrains.anko.defaultSharedPreferences

/**
 * Displays the master/yellow/white torch sliders plus the front/rear flash
 * switcher as a floating popup window on top of other apps (requires the
 * "display over other apps" permission). Triggered by long-pressing the
 * Torch quick settings tile.
 */
class FloatingTorchService : Service() {

    private var windowManager: WindowManager? = null
    private var popupView: View? = null

    private var whiteOn = false
    private var yellowOn = false

    private var yellowValue = 0
    private var whiteValue = 0
    private var yellowValueOld = 0
    private var whiteValueOld = 0

    private var yellowProgress = 1
    private var whiteProgress = 1
    private var masterProgress = 1

    private var whiteLedFileLocation = ""
    private var yellowLedFileLocation = ""
    private var toggleFileLocation = ""
    private var rearBrightnessMax = 0

    private var frontWhiteLedFileLocation = ""
    private var frontYellowLedFileLocation = ""
    private var frontSingleLedFileLocation = ""
    private var frontToggleFileLocation = ""
    private var frontBrightnessMax = 0

    private var flashMode = "rear"
    private var brightnessMax = 0

    private var cameraManager: CameraManager? = null
    private var frontFlashCameraId: String? = null
    private var frontFlashCameraIdChecked = false
    private var frontTorchEngaged = false

    private var torchCallback: CameraManager.TorchCallback? = null
    private var frontDesiredOn = false
    private var lastFrontWhite = 0
    private var lastFrontYellow = 0
    private val watchdogHandler = Handler()
    private val WATCHDOG_CHANNEL_ID = "front_flash_watchdog"
    private val WATCHDOG_NOTIFICATION_ID = 4821

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (popupView == null) {
            showPopup()
        }
        return START_NOT_STICKY
    }

    private fun showPopup() {
        if (!canDrawOverlays()) {
            stopSelf()
            return
        }

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val prefs = defaultSharedPreferences
        whiteLedFileLocation = prefs.getString(PREF_WHITE_FILE_LOCATION, null) ?: ""
        yellowLedFileLocation = prefs.getString(PREF_YELLOW_FILE_LOCATION, null) ?: ""
        toggleFileLocation = prefs.getString(PREF_TOGGLE_FILE_LOCATION, null) ?: ""
        rearBrightnessMax = prefs.getInt("brightnessMax", 0)

        frontWhiteLedFileLocation = prefs.getString(PREF_FRONT_WHITE_FILE_LOCATION, null) ?: ""
        frontYellowLedFileLocation = prefs.getString(PREF_FRONT_YELLOW_FILE_LOCATION, null) ?: ""
        frontSingleLedFileLocation = prefs.getString(PREF_FRONT_SINGLE_FILE_LOCATION, null) ?: ""
        frontToggleFileLocation = prefs.getString(PREF_FRONT_TOGGLE_FILE_LOCATION, null) ?: ""
        frontBrightnessMax = prefs.getString(PREF_FRONT_BRIGHTNESS_MAX, null)?.toIntOrNull() ?: 0
        brightnessMax = rearBrightnessMax
        frontDesiredOn = prefs.getBoolean(PREF_FRONT_FLASH_DESIRED_ON, false)

        val themedContext = ContextThemeWrapper(this, R.style.AppTheme)
        val inflater = LayoutInflater.from(themedContext)
        val view = inflater.inflate(R.layout.floating_popup_torch, null)
        popupView = view

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val popupWidth = (screenWidth * 0.92f).toInt()

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
                popupWidth,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT)
        params.gravity = Gravity.CENTER

        windowManager?.addView(view, params)

        view.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                closePopup()
                true
            } else {
                false
            }
        }

        setupDrag(view, params)
        setupCloseButton(view)
        setupFlashModeButtons(view)
        setupSliders(view)
    }

    private fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
    }

    private fun setupDrag(view: View, params: WindowManager.LayoutParams) {
        val dragHandle = view.findViewById<View>(R.id.popupDragHandle)
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f

        dragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchX).toInt()
                    params.y = initialY + (event.rawY - touchY).toInt()
                    windowManager?.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun setupCloseButton(view: View) {
        val closeButton: ImageButton = view.findViewById(R.id.popupCloseButton)
        closeButton.setOnClickListener {
            closePopup()
        }
    }

    private fun closePopup() {
        popupView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
            }
        }
        popupView = null

        if (frontDesiredOn) {
            // Keep the service (and the front-flash watchdog) alive quietly
            // in the background so the physical LED can be reclaimed the
            // instant another app's camera session lets go of it.
            return
        }
        stopSelf()
    }

    private fun setupFlashModeButtons(view: View) {
        val rearFlashButton: ImageButton = view.findViewById(R.id.popupRearFlashButton)
        val frontFlashButton: ImageButton = view.findViewById(R.id.popupFrontFlashButton)

        val prefs = defaultSharedPreferences
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

        flashMode = prefs.getString(PREF_FLASH_MODE, "rear") ?: "rear"
        refreshIcons(flashMode)

        rearFlashButton.setOnClickListener {
            controlLed(0, 0, false)
            flashMode = "rear"
            brightnessMax = rearBrightnessMax
            prefs.edit().putString(PREF_FLASH_MODE, "rear").apply()
            refreshIcons("rear")
        }

        frontFlashButton.setOnClickListener {
            controlLed(0, 0, false)
            flashMode = "front"
            brightnessMax = rearBrightnessMax
            prefs.edit().putString(PREF_FLASH_MODE, "front").apply()
            refreshIcons("front")
        }
    }

    private fun setupSliders(view: View) {
        val masterSlider: Slider = view.findViewById(R.id.popupMasterSlider)
        val whiteSlider: Slider = view.findViewById(R.id.popupWhiteSlider)
        val yellowSlider: Slider = view.findViewById(R.id.popupYellowSlider)

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
                    if (whiteValue > brightnessMax) whiteValue = brightnessMax
                    yellowValue = (brightnessMax / 20) * (progress - 1)
                    if (yellowValue > brightnessMax) yellowValue = brightnessMax
                }
                masterProgress = progress
            }
        }
        masterSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                if (flashMode == "front" || whiteValue != whiteValueOld || yellowValue != yellowValueOld) {
                    controlLed(whiteValue, yellowValue, yellowOn)
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
                    if (yellowSlider.value.toInt() == 1) masterSlider.isEnabled = true
                    whiteValue = 0
                    whiteOn = false
                } else {
                    masterSlider.isEnabled = false
                    whiteOn = true
                    whiteValue = (brightnessMax / 20) * (progress - 1)
                    if (whiteValue > brightnessMax) whiteValue = brightnessMax
                }
                whiteProgress = progress
            }
        }
        whiteSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                if (flashMode == "front" || whiteValue != whiteValueOld) {
                    controlLed(whiteValue, yellowValue, whiteOn || yellowOn)
                    whiteValueOld = whiteValue
                }
            }
        })

        yellowSlider.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val progress = value.toInt()
            if (progress != yellowProgress) {
                if (progress == 1) {
                    if (whiteSlider.value.toInt() == 1) masterSlider.isEnabled = true
                    yellowValue = 0
                    yellowOn = false
                } else {
                    masterSlider.isEnabled = false
                    yellowOn = true
                    yellowValue = (brightnessMax / 20) * (progress - 1)
                    if (yellowValue > brightnessMax) yellowValue = brightnessMax
                }
                yellowProgress = progress
            }
        }
        yellowSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                if (flashMode == "front" || yellowValue != yellowValueOld) {
                    controlLed(whiteValue, yellowValue, yellowOn || whiteOn)
                    yellowValueOld = yellowValue
                }
            }
        })
    }

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

    private fun hasFrontSysfsPaths(): Boolean {
        return (frontWhiteLedFileLocation.isNotEmpty() && frontYellowLedFileLocation.isNotEmpty() && frontToggleFileLocation.isNotEmpty()) ||
                (frontSingleLedFileLocation.isNotEmpty() && frontToggleFileLocation.isNotEmpty()) ||
                frontSingleLedFileLocation.isNotEmpty() ||
                frontToggleFileLocation.isNotEmpty()
    }

    private fun controlFrontLed(whiteLed: Int, yellowLed: Int, torchState: Boolean) {
        lastFrontWhite = whiteLed
        lastFrontYellow = yellowLed
        frontDesiredOn = torchState
        defaultSharedPreferences.edit().putBoolean(PREF_FRONT_FLASH_DESIRED_ON, torchState).apply()

        // Direct sysfs writes bypass the Camera2 framework entirely, so the
        // flash stays on even if another app opens the camera afterwards.
        // The Camera2 setTorchMode() API is only used as a last resort,
        // since the OS forcibly turns that off the moment any other process
        // acquires the camera.
        if (hasFrontSysfsPaths()) {
            frontTorchEngaged = torchState
            controlFrontLedViaSysfs(whiteLed, yellowLed, torchState)
            return
        }

        ensureFrontFlashCameraId()
        val camId = frontFlashCameraId

        if (camId == null) {
            controlFrontLedViaSysfs(whiteLed, yellowLed, torchState)
            return
        }

        if (!torchState) {
            // User turned the flash off from the app - stop trying to
            // reclaim it, there's nothing left to spoof.
            unregisterTorchWatchdog()
            stopWatchdogForeground()
            try {
                cameraManager?.setTorchMode(camId, false)
            } catch (e: Exception) {
            }
            frontTorchEngaged = false
            adjustEngagedFrontBrightness(0, 0, false)
            return
        }

        registerTorchWatchdog(camId)
        startWatchdogForeground()

        try {
            cameraManager?.setTorchMode(camId, true)
            frontTorchEngaged = true
        } catch (e: Exception) {
            controlFrontLedViaSysfs(whiteLed, yellowLed, torchState)
            return
        }

        Handler().postDelayed({
            adjustEngagedFrontBrightness(whiteLed, yellowLed, true)
        }, 80)
    }

    /**
     * Watches the official Camera2 torch state for the front flash. Whenever
     * something else (another app opening the camera) forces it off while
     * the app still wants it on, the framework will keep reporting the
     * torch as off - other apps see that "off" state (effectively spoofed) -
     * while this quietly keeps retrying in the background so the physical
     * LED snaps back on the moment the camera is free again.
     */
    private fun registerTorchWatchdog(camId: String) {
        if (torchCallback != null) return
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId != camId) return
                frontTorchEngaged = enabled
                if (!enabled && frontDesiredOn) {
                    scheduleReclaim(camId)
                }
            }

            override fun onTorchModeUnavailable(cameraId: String) {
                if (cameraId != camId) return
                if (frontDesiredOn) scheduleReclaim(camId)
            }
        }
        torchCallback = callback
        try {
            cameraManager?.registerTorchCallback(callback, watchdogHandler)
        } catch (e: Exception) {
        }
    }

    private fun unregisterTorchWatchdog() {
        torchCallback?.let {
            try {
                cameraManager?.unregisterTorchCallback(it)
            } catch (e: Exception) {
            }
        }
        torchCallback = null
        watchdogHandler.removeCallbacksAndMessages(null)
    }

    private fun scheduleReclaim(camId: String) {
        watchdogHandler.removeCallbacksAndMessages(null)
        val attempt = object : Runnable {
            override fun run() {
                if (!frontDesiredOn) return
                try {
                    cameraManager?.setTorchMode(camId, true)
                    frontTorchEngaged = true
                    Handler().postDelayed({
                        adjustEngagedFrontBrightness(lastFrontWhite, lastFrontYellow, true)
                    }, 80)
                } catch (e: Exception) {
                    // Camera still claimed elsewhere - keep retrying quietly.
                    watchdogHandler.postDelayed(this, 400)
                }
            }
        }
        watchdogHandler.post(attempt)
    }

    private fun startWatchdogForeground() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            val channel = android.app.NotificationChannel(
                    WATCHDOG_CHANNEL_ID, "Front Flash", android.app.NotificationManager.IMPORTANCE_MIN)
            manager?.createNotificationChannel(channel)
            val notification = NotificationCompat.Builder(this, WATCHDOG_CHANNEL_ID)
                    .setContentTitle("Front flash is on")
                    .setSmallIcon(R.drawable.ic_flash_front)
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_MIN)
                    .build()
            startForeground(WATCHDOG_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
        }
    }

    private fun stopWatchdogForeground() {
        try {
            stopForeground(true)
        } catch (e: Exception) {
        }
    }

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

    override fun onDestroy() {
        super.onDestroy()
        unregisterTorchWatchdog()
        stopWatchdogForeground()
        popupView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
            }
        }
        popupView = null
    }
}
