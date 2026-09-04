package com.flowpilot.app.actions

import android.content.ContentResolver
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.flowpilot.app.data.model.ActionType

/**
 * Sets Android system screen brightness (SCREEN_BRIGHTNESS).
 *
 * Requirements:
 * Requires user-grantable WRITE_SETTINGS special access (Settings.System.canWrite(context)).
 */
class BrightnessExecutor(
    private val context: Context,
    private val permissionChecker: (Context) -> Boolean = { ctx ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.System.canWrite(ctx)
        } else {
            true
        }
    },
    private val settingsWriter: (ContentResolver, String, Int) -> Boolean = { cr, name, value ->
        Settings.System.putInt(cr, name, value)
    },
    private val settingsReader: (ContentResolver, String, Int) -> Int = { cr, name, defaultVal ->
        Settings.System.getInt(cr, name, defaultVal)
    },
) : ActionExecutor {

    override val supportedTypes: Set<ActionType> =
        setOf(
            ActionType.SET_SCREEN_BRIGHTNESS,
            ActionType.AUTO_BRIGHTNESS_ON,
            ActionType.AUTO_BRIGHTNESS_OFF,
        )

    override fun execute(action: ActionType, parameters: ActionParameters): ActionResult {
        if (action !in supportedTypes) {
            return ActionResult(false, "Unsupported action for brightness executor")
        }

        if (!permissionChecker(context)) {
            return ActionResult(
                false,
                "Screen brightness requires Modify system settings (WRITE_SETTINGS) permission",
            )
        }

        return when (action) {
            ActionType.AUTO_BRIGHTNESS_ON -> {
                try {
                    val written = settingsWriter(
                        context.contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
                    )
                    if (!written) {
                        ActionResult(false, "Failed to enable auto-brightness")
                    } else {
                        val readBack = settingsReader(
                            context.contentResolver,
                            Settings.System.SCREEN_BRIGHTNESS_MODE,
                            -1,
                        )
                        if (readBack == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
                            ActionResult(true, "Auto-brightness enabled (mode: automatic)")
                        } else {
                            ActionResult(true, "Auto-brightness command sent")
                        }
                    }
                } catch (e: Exception) {
                    ActionResult(false, "Auto-brightness error: ${e.message ?: "Unknown"}")
                }
            }

            ActionType.AUTO_BRIGHTNESS_OFF -> {
                try {
                    val written = settingsWriter(
                        context.contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                    )
                    if (!written) {
                        ActionResult(false, "Failed to disable auto-brightness")
                    } else {
                        val readBack = settingsReader(
                            context.contentResolver,
                            Settings.System.SCREEN_BRIGHTNESS_MODE,
                            -1,
                        )
                        if (readBack == Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) {
                            ActionResult(true, "Auto-brightness disabled (mode: manual)")
                        } else {
                            ActionResult(true, "Auto-brightness command sent")
                        }
                    }
                } catch (e: Exception) {
                    ActionResult(false, "Auto-brightness error: ${e.message ?: "Unknown"}")
                }
            }

            ActionType.SET_SCREEN_BRIGHTNESS -> {
                val percent = parameters.screenBrightnessPercent.coerceIn(0, 100)
                val targetBrightness = (percent * 255) / 100

                try {
                    // Set manual brightness mode (0 = manual, 1 = automatic) so adaptive brightness does not immediately revert
                    settingsWriter(
                        context.contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                    )

                    val written = settingsWriter(
                        context.contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS,
                        targetBrightness,
                    )
                    if (!written) {
                        return ActionResult(false, "Failed to write screen brightness setting")
                    }

                    val readBack = settingsReader(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, -1)
                    // Allow ±2 difference due to rounding/hardware scaling
                    if (readBack >= 0 && kotlin.math.abs(readBack - targetBrightness) <= 2) {
                        ActionResult(true, "Screen brightness set to $percent% ($readBack/255)")
                    } else {
                        ActionResult(true, "Screen brightness set command sent ($percent%)")
                    }
                } catch (e: Exception) {
                    ActionResult(false, "Screen brightness error: ${e.message ?: "Unknown"}")
                }
            }

            else -> ActionResult(false, "Unsupported action for brightness executor")
        }
    }
}
