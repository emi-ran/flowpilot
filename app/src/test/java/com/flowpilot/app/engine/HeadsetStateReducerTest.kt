package com.flowpilot.app.engine

import android.media.AudioDeviceInfo
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.TriggerDefinition
import com.flowpilot.app.data.model.TriggerDefinitions
import com.flowpilot.app.data.model.TriggerEvent
import com.flowpilot.app.data.model.legacyTriggerDefinition
import com.flowpilot.app.data.model.normalizedTriggers
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

class HeadsetStateReducerTest {
    private val modern = 34
    private fun reducer(sdk: Int = modern) = HeadsetStateReducer(sdk)
    private fun dev(id: Int, type: Int, sink: Boolean = true) = HeadsetDevice(id, type, sink)
    private val wired = dev(1, HeadsetDeviceTypes.WIRED_HEADSET)
    private val bt = dev(2, HeadsetDeviceTypes.BLUETOOTH_A2DP)
    private val speaker = dev(9, HeadsetDeviceTypes.BUILTIN_SPEAKER)

    @Test fun typeConstantsMatchAndroidSdk() {
        assertThat(HeadsetDeviceTypes.BUILTIN_EARPIECE).isEqualTo(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)
        assertThat(HeadsetDeviceTypes.BUILTIN_SPEAKER).isEqualTo(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
        assertThat(HeadsetDeviceTypes.WIRED_HEADSET).isEqualTo(AudioDeviceInfo.TYPE_WIRED_HEADSET)
        assertThat(HeadsetDeviceTypes.WIRED_HEADPHONES).isEqualTo(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
        assertThat(HeadsetDeviceTypes.BLUETOOTH_SCO).isEqualTo(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
        assertThat(HeadsetDeviceTypes.BLUETOOTH_A2DP).isEqualTo(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        assertThat(HeadsetDeviceTypes.HDMI).isEqualTo(AudioDeviceInfo.TYPE_HDMI)
        assertThat(HeadsetDeviceTypes.USB_DEVICE).isEqualTo(AudioDeviceInfo.TYPE_USB_DEVICE)
        assertThat(HeadsetDeviceTypes.USB_HEADSET).isEqualTo(AudioDeviceInfo.TYPE_USB_HEADSET)
        assertThat(HeadsetDeviceTypes.BLE_HEADSET).isEqualTo(26) // AudioDeviceInfo.TYPE_BLE_HEADSET (API 31)
    }

    @Test fun headsetTypeClassification() {
        val accepted = listOf(
            HeadsetDeviceTypes.WIRED_HEADSET, HeadsetDeviceTypes.WIRED_HEADPHONES,
            HeadsetDeviceTypes.USB_DEVICE, HeadsetDeviceTypes.USB_HEADSET,
            HeadsetDeviceTypes.BLUETOOTH_SCO, HeadsetDeviceTypes.BLUETOOTH_A2DP,
            HeadsetDeviceTypes.BLE_HEADSET,
        )
        accepted.forEach { assertThat(HeadsetDeviceTypes.isHeadsetType(it, modern)).isTrue() }
        listOf(
            HeadsetDeviceTypes.BUILTIN_SPEAKER, HeadsetDeviceTypes.BUILTIN_EARPIECE,
            HeadsetDeviceTypes.HDMI, 0, 5, 12, 25, 27, 999, -1,
        ).forEach { assertThat(HeadsetDeviceTypes.isHeadsetType(it, modern)).isFalse() }
    }

    @Test fun sdkGatingForUsbHeadsetAndBle() {
        assertThat(HeadsetDeviceTypes.isHeadsetType(HeadsetDeviceTypes.USB_HEADSET, 25)).isFalse()
        assertThat(HeadsetDeviceTypes.isHeadsetType(HeadsetDeviceTypes.USB_HEADSET, 26)).isTrue()
        assertThat(HeadsetDeviceTypes.isHeadsetType(HeadsetDeviceTypes.BLE_HEADSET, 30)).isFalse()
        assertThat(HeadsetDeviceTypes.isHeadsetType(HeadsetDeviceTypes.BLE_HEADSET, 31)).isTrue()
        assertThat(HeadsetDeviceTypes.isHeadsetType(HeadsetDeviceTypes.WIRED_HEADSET, 26)).isTrue()
        val old = reducer(30)
        assertThat(old.onDevicesAdded(listOf(dev(5, HeadsetDeviceTypes.BLE_HEADSET)))).isNull()
        assertThat(old.isConnected).isFalse()
    }

    @Test fun firstHeadsetConnectsAndLastDisconnects() {
        val r = reducer()
        assertThat(r.onDevicesAdded(listOf(wired))).isEqualTo(HeadsetEvent.CONNECTED)
        assertThat(r.isConnected).isTrue()
        assertThat(r.onDevicesRemoved(listOf(wired))).isEqualTo(HeadsetEvent.DISCONNECTED)
        assertThat(r.isConnected).isFalse()
    }

    @Test fun repeatedAddIsIgnored() {
        val r = reducer()
        assertThat(r.onDevicesAdded(listOf(wired))).isEqualTo(HeadsetEvent.CONNECTED)
        assertThat(r.onDevicesAdded(listOf(wired))).isNull()
        assertThat(r.onDevicesAdded(listOf(wired, wired))).isNull()
        assertThat(r.onDevicesRemoved(listOf(wired))).isEqualTo(HeadsetEvent.DISCONNECTED)
    }

    @Test fun repeatedAndUnknownRemovalsAreIgnored() {
        val r = reducer()
        assertThat(r.onDevicesRemoved(listOf(wired))).isNull()
        r.onDevicesAdded(listOf(wired))
        assertThat(r.onDevicesRemoved(listOf(wired))).isEqualTo(HeadsetEvent.DISCONNECTED)
        assertThat(r.onDevicesRemoved(listOf(wired))).isNull()
        assertThat(r.onDevicesRemoved(emptyList())).isNull()
        assertThat(r.onDevicesAdded(emptyList())).isNull()
    }

    @Test fun builtInSpeakerAndOtherNonHeadsetsNeverTrigger() {
        val r = reducer()
        val hdmi = dev(10, HeadsetDeviceTypes.HDMI)
        assertThat(r.onDevicesAdded(listOf(speaker, hdmi, dev(11, HeadsetDeviceTypes.BUILTIN_EARPIECE)))).isNull()
        assertThat(r.isConnected).isFalse()
        assertThat(r.onDevicesRemoved(listOf(speaker, hdmi))).isNull()
    }

    @Test fun speakerRemovalDoesNotDisconnectHeadset() {
        val r = reducer()
        r.onDevicesAdded(listOf(speaker, wired))
        assertThat(r.onDevicesRemoved(listOf(speaker))).isNull()
        assertThat(r.isConnected).isTrue()
    }

    @Test fun inputOnlyDevicesAreIgnored() {
        val r = reducer()
        val mic = dev(3, HeadsetDeviceTypes.WIRED_HEADSET, sink = false)
        assertThat(r.onDevicesAdded(listOf(mic))).isNull()
        assertThat(r.isConnected).isFalse()
    }

    @Test fun multipleDevicesOnlyFirstAndLastTransition() {
        val r = reducer()
        assertThat(r.onDevicesAdded(listOf(bt))).isEqualTo(HeadsetEvent.CONNECTED)
        assertThat(r.onDevicesAdded(listOf(wired))).isNull()
        assertThat(r.onDevicesRemoved(listOf(bt))).isNull()
        assertThat(r.isConnected).isTrue()
        assertThat(r.onDevicesRemoved(listOf(wired))).isEqualTo(HeadsetEvent.DISCONNECTED)
    }

    @Test fun simultaneousBatchProducesOneEvent() {
        val r = reducer()
        val sco = dev(4, HeadsetDeviceTypes.BLUETOOTH_SCO)
        assertThat(r.onDevicesAdded(listOf(bt, sco, wired))).isEqualTo(HeadsetEvent.CONNECTED)
        assertThat(r.onDevicesRemoved(listOf(bt, sco, wired))).isEqualTo(HeadsetEvent.DISCONNECTED)
    }

    @Test fun bluetoothProfilesOfOneHeadsetAreOneEvent() {
        val r = reducer()
        val sco = dev(4, HeadsetDeviceTypes.BLUETOOTH_SCO)
        assertThat(r.onDevicesAdded(listOf(bt))).isEqualTo(HeadsetEvent.CONNECTED)
        assertThat(r.onDevicesAdded(listOf(sco))).isNull()
        assertThat(r.onDevicesRemoved(listOf(sco))).isNull()
        assertThat(r.onDevicesRemoved(listOf(bt))).isEqualTo(HeadsetEvent.DISCONNECTED)
    }

    @Test fun reconnectAfterDisconnectFiresAgain() {
        val r = reducer()
        repeat(3) {
            assertThat(r.onDevicesAdded(listOf(wired))).isEqualTo(HeadsetEvent.CONNECTED)
            assertThat(r.onDevicesRemoved(listOf(wired))).isEqualTo(HeadsetEvent.DISCONNECTED)
        }
    }

    @Test fun reusedIdWithDifferentTypeIsTrackedById() {
        val r = reducer()
        r.onDevicesAdded(listOf(dev(1, HeadsetDeviceTypes.WIRED_HEADSET)))
        assertThat(r.onDevicesRemoved(listOf(dev(1, HeadsetDeviceTypes.WIRED_HEADSET)))).isEqualTo(HeadsetEvent.DISCONNECTED)
        assertThat(r.onDevicesAdded(listOf(dev(1, HeadsetDeviceTypes.USB_HEADSET)))).isEqualTo(HeadsetEvent.CONNECTED)
    }

    @Test fun seedingExistingDevicesDoesNotEmitAndStartupReplayIsIgnored() {
        val r = reducer()
        r.seed(listOf(wired, speaker))
        assertThat(r.isConnected).isTrue()
        // Android replays current devices on registration; these must not look like a connect.
        assertThat(r.onDevicesAdded(listOf(wired, speaker))).isNull()
        assertThat(r.onDevicesRemoved(listOf(wired))).isEqualTo(HeadsetEvent.DISCONNECTED)
    }

    @Test fun seedWithNoHeadsetThenFirstAddConnects() {
        val r = reducer()
        r.seed(listOf(speaker))
        assertThat(r.isConnected).isFalse()
        assertThat(r.onDevicesAdded(listOf(bt))).isEqualTo(HeadsetEvent.CONNECTED)
    }

    @Test fun seedReplacesPreviousStateWithoutEvent() {
        val r = reducer()
        r.onDevicesAdded(listOf(wired))
        r.seed(listOf(bt))
        assertThat(r.onDevicesRemoved(listOf(wired))).isNull()
        assertThat(r.onDevicesRemoved(listOf(bt))).isEqualTo(HeadsetEvent.DISCONNECTED)
    }

    @Test fun resetClearsStateSoStopStartDoesNotLeakDisconnect() {
        val r = reducer()
        r.onDevicesAdded(listOf(wired))
        r.reset()
        assertThat(r.isConnected).isFalse()
        assertThat(r.onDevicesRemoved(listOf(wired))).isNull()
        assertThat(r.onDevicesAdded(listOf(wired))).isEqualTo(HeadsetEvent.CONNECTED)
    }

    // --- Rule evaluation ---

    private fun rule(event: TriggerEvent, id: String = event.name) =
        Automation(id = id, name = id, createdAt = 1L, triggerEvent = event)

    @Test fun evaluatorMatchesOnlyTheMatchingHeadsetTrigger() {
        val connected = rule(TriggerEvent.HEADSET_CONNECTED)
        val disconnected = rule(TriggerEvent.HEADSET_DISCONNECTED)
        val unrelated = rule(TriggerEvent.CHARGER_CONNECTED)
        val all = listOf(connected, disconnected, unrelated)
        assertThat(RuleEvaluator.evaluateHeadset(all, HeadsetEvent.CONNECTED)).containsExactly(connected)
        assertThat(RuleEvaluator.evaluateHeadset(all, HeadsetEvent.DISCONNECTED)).containsExactly(disconnected)
    }

    @Test fun evaluatorSkipsDisabledAndCoolingDownRules() {
        val disabled = rule(TriggerEvent.HEADSET_CONNECTED).copy(enabled = false)
        assertThat(RuleEvaluator.evaluateHeadset(listOf(disabled), HeadsetEvent.CONNECTED)).isEmpty()
        val now = 10_000_000L
        val cooling = rule(TriggerEvent.HEADSET_CONNECTED).copy(cooldownMinutes = 5, lastTriggeredAt = now - 60_000L)
        assertThat(RuleEvaluator.evaluateHeadset(listOf(cooling), HeadsetEvent.CONNECTED, nowMs = now)).isEmpty()
    }

    @Test fun evaluatorMatchesHeadsetSourceOfMultiTriggerRule() {
        val multi = rule(TriggerEvent.SCREEN_ON).copy(triggerDefinitions = TriggerDefinitions(items = listOf(
            TriggerDefinition.ScreenOn, TriggerDefinition.HeadsetDisconnected,
        )))
        assertThat(RuleEvaluator.evaluateHeadset(listOf(multi), HeadsetEvent.DISCONNECTED).map { it.id }).containsExactly(multi.id)
        assertThat(RuleEvaluator.evaluateHeadset(listOf(multi), HeadsetEvent.CONNECTED)).isEmpty()
    }

    // --- Model / persistence ---

    @Test fun legacyAndNormalizedDefinitionsMapToHeadsetTriggers() {
        assertThat(rule(TriggerEvent.HEADSET_CONNECTED).legacyTriggerDefinition()).isEqualTo(TriggerDefinition.HeadsetConnected)
        assertThat(rule(TriggerEvent.HEADSET_DISCONNECTED).normalizedTriggers()).containsExactly(TriggerDefinition.HeadsetDisconnected)
        assertThat(TriggerDefinition.HeadsetConnected.event).isEqualTo(TriggerEvent.HEADSET_CONNECTED)
        assertThat(TriggerDefinition.HeadsetDisconnected.event).isEqualTo(TriggerEvent.HEADSET_DISCONNECTED)
    }

    @Test fun headsetTriggerDefinitionsRoundTripThroughJson() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val defs = TriggerDefinitions(items = listOf(TriggerDefinition.HeadsetConnected, TriggerDefinition.HeadsetDisconnected))
        val text = json.encodeToString(defs)
        assertThat(text).contains("HEADSET_CONNECTED")
        assertThat(text).contains("HEADSET_DISCONNECTED")
        assertThat(json.decodeFromString<TriggerDefinitions>(text)).isEqualTo(defs)
        val ruleText = json.encodeToString(rule(TriggerEvent.HEADSET_DISCONNECTED))
        assertThat(json.decodeFromString<Automation>(ruleText).triggerEvent).isEqualTo(TriggerEvent.HEADSET_DISCONNECTED)
    }

    @Test fun categoryIsAudioAndEnumLookupWorks() {
        assertThat(TriggerEvent.HEADSET_CONNECTED.category.name).isEqualTo("AUDIO")
        assertThat(TriggerEvent.HEADSET_DISCONNECTED.category.name).isEqualTo("AUDIO")
        assertThat(TriggerEvent.fromId("HEADSET_CONNECTED")).isEqualTo(TriggerEvent.HEADSET_CONNECTED)
    }
}
