package com.flowpilot.app.actions

import android.content.Context
import com.flowpilot.app.data.model.ActionType

/** Routes an action to the executor that can perform it. */
class ActionDispatcher private constructor(
    private val nfc: NfcExecutor,
    private val bluetooth: BluetoothExecutor,
    private val powerSaver: PowerSaverExecutor,
    private val darkTheme: DarkThemeExecutor,
    private val autoRotate: AutoRotateExecutor,
    private val notification: NotificationExecutor,
    private val vibration: VibrationExecutor,
    private val sound: SoundExecutor,
    private val soundProfile: SoundProfileExecutor,
    private val mediaVolume: MediaVolumeExecutor,
    private val launcher: LaunchExecutor,
    private val tts: TtsExecutor,
    private val dnd: DndExecutor,
    private val clock: ClockExecutor,
    private val webhook: WebhookExecutor,
    private val phone: PhoneExecutor,
    private val wifi: WifiExecutor,
    private val mobileData: MobileDataExecutor,
    private val airplaneMode: AirplaneModeExecutor,
    private val torch: TorchExecutor,
) {
    private val map: Map<ActionType, ActionExecutor> by lazy {
        listOf(nfc, bluetooth, powerSaver, darkTheme, autoRotate, notification, vibration, sound, soundProfile, mediaVolume, launcher, tts, dnd, clock, webhook, phone, wifi, mobileData, airplaneMode, torch)
            .flatMap { e -> e.supportedTypes.map { it to e } }
            .toMap()
    }

    fun execute(action: ActionType, parameters: ActionParameters = ActionParameters()): ActionResult {
        val executor = map[action] ?: return ActionResult(false, "No executor for ${action.label}")
        return executor.execute(action, parameters)
    }

    companion object {
        @Volatile
        private var instance: ActionDispatcher? = null

        fun get(context: Context): ActionDispatcher =
            instance ?: synchronized(this) {
                instance ?: ActionDispatcher(
                    NfcExecutor(ShizukuShell.instance),
                    BluetoothExecutor(context.applicationContext, ShizukuShell.instance),
                    PowerSaverExecutor(context.applicationContext, ShizukuShell.instance),
                    DarkThemeExecutor(context.applicationContext, ShizukuShell.instance),
                    AutoRotateExecutor(context.applicationContext),
                    NotificationExecutor(context.applicationContext),
                    VibrationExecutor(context.applicationContext),
                    SoundExecutor(context.applicationContext),
                    SoundProfileExecutor(context.applicationContext),
                    MediaVolumeExecutor(context.applicationContext),
                    LaunchExecutor(context.applicationContext),
                    TtsExecutor(context.applicationContext),
                    DndExecutor(context.applicationContext),
                    ClockExecutor(context.applicationContext),
                    WebhookExecutor(),
                    PhoneExecutor(context.applicationContext),
                    WifiExecutor(context.applicationContext, ShizukuShell.instance),
                    MobileDataExecutor(context.applicationContext, ShizukuShell.instance),
                    AirplaneModeExecutor(context.applicationContext, ShizukuShell.instance),
                    TorchExecutor(context.applicationContext),
                ).also { instance = it }
            }
    }
}
