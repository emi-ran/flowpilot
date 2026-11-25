package com.flowpilot.app.engine

import com.flowpilot.app.data.backup.BackupManager
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.ConditionCombinator
import com.flowpilot.app.data.model.ConditionNode
import com.flowpilot.app.data.model.ConditionType
import com.flowpilot.app.data.model.RingerModeCondition
import com.flowpilot.app.data.model.RuleCondition
import com.flowpilot.app.data.model.TriggerEvent
import com.flowpilot.app.data.model.flattenConditions
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SystemStateConditionsTest {

    private fun cond(type: ConditionType, address: String = "", ringer: RingerModeCondition = RingerModeCondition.NORMAL) =
        RuleCondition(type = type, bluetoothDeviceAddress = address, ringerMode = ringer)

    private fun matches(condition: RuleCondition, state: LiveSystemState) =
        RuleEvaluator.matchesConditions(listOf(condition), state)

    private fun rule(vararg conditions: RuleCondition, tree: ConditionNode? = null, enabled: Boolean = true) = Automation(
        id = "r${conditions.hashCode()}${tree.hashCode()}", name = "n", createdAt = 1L,
        triggerEvent = TriggerEvent.SCREEN_ON, enabled = enabled,
        conditions = tree?.flattenConditions() ?: conditions.toList(), conditionTree = tree,
    )

    // --- leaf evaluation ---

    @Test fun bluetoothAddress_matchesCaseInsensitivelyAndTrimmed() {
        val state = LiveSystemState(connectedBluetoothAddresses = setOf("AA:BB:CC:DD:EE:FF"))
        assertThat(matches(cond(ConditionType.BLUETOOTH_DEVICE_CONNECTED, "aa:bb:cc:dd:ee:ff"), state)).isTrue()
        assertThat(matches(cond(ConditionType.BLUETOOTH_DEVICE_CONNECTED, " AA:BB:CC:DD:EE:FF "), state)).isTrue()
        assertThat(matches(cond(ConditionType.BLUETOOTH_DEVICE_CONNECTED, "11:22:33:44:55:66"), state)).isFalse()
    }

    @Test fun bluetoothBlankAddress_matchesAnyConnectedDevice() {
        val blank = cond(ConditionType.BLUETOOTH_DEVICE_CONNECTED)
        assertThat(matches(blank, LiveSystemState(connectedBluetoothAddresses = setOf("AA:BB:CC:DD:EE:FF")))).isTrue()
        assertThat(matches(blank, LiveSystemState(connectedBluetoothAddresses = emptySet()))).isFalse()
    }

    @Test fun bluetooth_missingStateIsFalse() {
        assertThat(matches(cond(ConditionType.BLUETOOTH_DEVICE_CONNECTED), LiveSystemState())).isFalse()
        assertThat(matches(cond(ConditionType.BLUETOOTH_DEVICE_CONNECTED, "AA:BB:CC:DD:EE:FF"), LiveSystemState())).isFalse()
    }

    @Test fun headsetDndAirplane_trueFalseAndMissing() {
        for (type in listOf(ConditionType.HEADSET_CONNECTED, ConditionType.DND_ON, ConditionType.AIRPLANE_MODE_ON)) {
            val on = when (type) {
                ConditionType.HEADSET_CONNECTED -> LiveSystemState(isHeadsetConnected = true)
                ConditionType.DND_ON -> LiveSystemState(isDndOn = true)
                else -> LiveSystemState(isAirplaneModeOn = true)
            }
            val off = when (type) {
                ConditionType.HEADSET_CONNECTED -> LiveSystemState(isHeadsetConnected = false)
                ConditionType.DND_ON -> LiveSystemState(isDndOn = false)
                else -> LiveSystemState(isAirplaneModeOn = false)
            }
            assertThat(matches(cond(type), on)).isTrue()
            assertThat(matches(cond(type), off)).isFalse()
            assertThat(matches(cond(type), LiveSystemState())).isFalse()
        }
    }

    @Test fun ringerMode_matchesOnlyConfiguredMode() {
        for (expected in RingerModeCondition.entries) {
            for (actual in RingerModeCondition.entries) {
                assertThat(
                    matches(cond(ConditionType.RINGER_MODE, ringer = expected), LiveSystemState(ringerMode = actual)),
                ).isEqualTo(expected == actual)
            }
            assertThat(matches(cond(ConditionType.RINGER_MODE, ringer = expected), LiveSystemState())).isFalse()
        }
    }

    // --- tree evaluation ---

    @Test fun tree_andOrNotCombinations() {
        val headset = ConditionNode.Leaf(cond(ConditionType.HEADSET_CONNECTED))
        val dnd = ConditionNode.Leaf(cond(ConditionType.DND_ON))
        val airplane = ConditionNode.Leaf(cond(ConditionType.AIRPLANE_MODE_ON))
        val silent = ConditionNode.Leaf(cond(ConditionType.RINGER_MODE, ringer = RingerModeCondition.SILENT))
        val state = LiveSystemState(
            isHeadsetConnected = true, isDndOn = false, isAirplaneModeOn = false, ringerMode = RingerModeCondition.SILENT,
        )
        fun eval(tree: ConditionNode) = RuleEvaluator.matchesRuleConditions(rule(tree = tree), state)

        assertThat(eval(ConditionNode.Group(ConditionCombinator.AND, children = listOf(headset, silent)))).isTrue()
        assertThat(eval(ConditionNode.Group(ConditionCombinator.AND, children = listOf(headset, dnd)))).isFalse()
        assertThat(eval(ConditionNode.Group(ConditionCombinator.OR, children = listOf(dnd, airplane, silent)))).isTrue()
        assertThat(eval(ConditionNode.Group(ConditionCombinator.OR, children = listOf(dnd, airplane)))).isFalse()
        assertThat(eval(ConditionNode.Group(ConditionCombinator.OR, negate = true, children = listOf(dnd, airplane)))).isTrue()
        assertThat(eval(ConditionNode.Group(ConditionCombinator.AND, negate = true, children = listOf(headset, silent)))).isFalse()
        // headset AND NOT(dnd OR airplane)
        assertThat(
            eval(
                ConditionNode.Group(
                    ConditionCombinator.AND,
                    children = listOf(
                        headset,
                        ConditionNode.Group(ConditionCombinator.OR, negate = true, children = listOf(dnd, airplane)),
                    ),
                ),
            ),
        ).isTrue()
    }

    @Test fun tree_missingStateInsideNotGroupBecomesTrue() {
        // Unknown state evaluates the leaf to false; NOT flips it, matching the existing tree semantics.
        val tree = ConditionNode.Group(
            negate = true,
            children = listOf(ConditionNode.Leaf(cond(ConditionType.DND_ON))),
        )
        assertThat(RuleEvaluator.matchesRuleConditions(rule(tree = tree), LiveSystemState())).isTrue()
        assertThat(RuleEvaluator.matchesRuleConditions(rule(tree = tree), LiveSystemState(isDndOn = true))).isFalse()
    }

    @Test fun existingConditionsUnchangedWithNewFieldsNull() {
        val state = LiveSystemState(batteryPercent = 20, isChargerConnected = true, isScreenOn = true, connectedWifiSsid = "Home")
        assertThat(matches(RuleCondition(ConditionType.BATTERY_BELOW, batteryLevel = 30), state)).isTrue()
        assertThat(matches(RuleCondition(ConditionType.CHARGER_CONNECTED), state)).isTrue()
        assertThat(matches(RuleCondition(ConditionType.WIFI_CONNECTED, wifiSsid = "home"), state)).isTrue()
        assertThat(matches(RuleCondition(ConditionType.SCREEN_OFF), state)).isFalse()
    }

    // --- lazy demand ---

    @Test fun demand_isEmptyWithoutNewConditions() {
        val rules = listOf(rule(RuleCondition(ConditionType.BATTERY_BELOW), RuleCondition(ConditionType.WIFI_CONNECTED)))
        assertThat(ConditionStateDemand.from(rules)).isEqualTo(ConditionStateDemand())
        assertThat(ConditionStateDemand.from(emptyList())).isEqualTo(ConditionStateDemand())
        assertThat(TriggerListenerDemand.from(rules).needsBluetoothCondition).isFalse()
    }

    @Test fun demand_readsOnlyUsedFields() {
        val demand = ConditionStateDemand.from(listOf(rule(cond(ConditionType.DND_ON), cond(ConditionType.AIRPLANE_MODE_ON))))
        assertThat(demand).isEqualTo(ConditionStateDemand(dnd = true, airplane = true))
    }

    @Test fun demand_ignoresDisabledRulesAndReadsTreeLeaves() {
        val disabled = rule(cond(ConditionType.HEADSET_CONNECTED), enabled = false)
        assertThat(ConditionStateDemand.from(listOf(disabled))).isEqualTo(ConditionStateDemand())
        val tree = ConditionNode.Group(
            ConditionCombinator.OR,
            children = listOf(
                ConditionNode.Group(children = listOf(ConditionNode.Leaf(cond(ConditionType.BLUETOOTH_DEVICE_CONNECTED)))),
                ConditionNode.Leaf(cond(ConditionType.RINGER_MODE)),
            ),
        )
        val enabled = rule(tree = tree)
        assertThat(ConditionStateDemand.from(listOf(enabled)))
            .isEqualTo(ConditionStateDemand(bluetoothDevices = true, ringer = true))
        assertThat(TriggerListenerDemand.from(listOf(enabled)).needsBluetoothCondition).isTrue()
        assertThat(TriggerListenerDemand.from(listOf(enabled.copy(enabled = false))).needsBluetoothCondition).isFalse()
    }

    // --- pure decoders ---

    @Test fun decoders_headsetTypes() {
        assertThat(ConditionStateDecoders.isHeadsetOutput(listOf(2, 1))).isFalse() // speaker, earpiece
        assertThat(ConditionStateDecoders.isHeadsetOutput(emptyList())).isFalse()
        for (type in listOf(3, 4, 7, 8, 11, 22, 26)) {
            assertThat(ConditionStateDecoders.isHeadsetOutput(listOf(2, type))).isTrue()
        }
    }

    @Test fun decoders_bluetoothAudioAddresses() {
        val addresses = ConditionStateDecoders.bluetoothAudioAddresses(
            listOf(8 to "aa:bb:cc:dd:ee:ff", 7 to "", 3 to "11:22:33:44:55:66", 2 to "99:99:99:99:99:99"),
        )
        assertThat(addresses).containsExactly("AA:BB:CC:DD:EE:FF")
    }

    @Test fun decoders_dndAndRinger() {
        assertThat(ConditionStateDecoders.isDndActive(1)).isFalse() // all
        assertThat(ConditionStateDecoders.isDndActive(0)).isFalse() // unknown
        for (filter in 2..4) assertThat(ConditionStateDecoders.isDndActive(filter)).isTrue()
        assertThat(ConditionStateDecoders.ringerModeOf(0)).isEqualTo(RingerModeCondition.SILENT)
        assertThat(ConditionStateDecoders.ringerModeOf(1)).isEqualTo(RingerModeCondition.VIBRATE)
        assertThat(ConditionStateDecoders.ringerModeOf(2)).isEqualTo(RingerModeCondition.NORMAL)
        assertThat(ConditionStateDecoders.ringerModeOf(9)).isNull()
    }

    // --- backup round-trip and validation ---

    @Test fun backup_roundTripsNewConditions() {
        val tree = ConditionNode.Group(
            ConditionCombinator.AND,
            children = listOf(
                ConditionNode.Leaf(
                    RuleCondition(
                        ConditionType.BLUETOOTH_DEVICE_CONNECTED,
                        bluetoothDeviceAddress = "AA:BB:CC:DD:EE:FF", bluetoothDeviceName = "Buds",
                    ),
                ),
                ConditionNode.Group(
                    ConditionCombinator.OR, negate = true,
                    children = listOf(
                        ConditionNode.Leaf(cond(ConditionType.RINGER_MODE, ringer = RingerModeCondition.VIBRATE)),
                        ConditionNode.Leaf(cond(ConditionType.DND_ON)),
                        ConditionNode.Leaf(cond(ConditionType.HEADSET_CONNECTED)),
                        ConditionNode.Leaf(cond(ConditionType.AIRPLANE_MODE_ON)),
                    ),
                ),
            ),
        )
        val original = rule(tree = tree)
        val parsed = BackupManager.parseImport(BackupManager.exportToString(listOf(original))).getOrThrow().single()
        assertThat(parsed.conditionTree).isEqualTo(tree)
        assertThat(parsed.conditions).isEqualTo(tree.flattenConditions())
    }

    @Test fun import_rejectsInvalidBluetoothConditionAddress() {
        val bad = rule(tree = ConditionNode.Group(children = listOf(
            ConditionNode.Leaf(cond(ConditionType.BLUETOOTH_DEVICE_CONNECTED, "not-an-address")),
        )))
        val result = BackupManager.parseImport(BackupManager.exportToString(listOf(bad)))
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Invalid Bluetooth condition")
    }

    @Test fun import_acceptsBlankAndValidBluetoothAddress() {
        for (address in listOf("", "aa:bb:cc:dd:ee:ff")) {
            val ok = rule(tree = ConditionNode.Group(children = listOf(
                ConditionNode.Leaf(cond(ConditionType.BLUETOOTH_DEVICE_CONNECTED, address)),
            )))
            assertThat(BackupManager.parseImport(BackupManager.exportToString(listOf(ok))).isSuccess).isTrue()
        }
    }

    @Test fun import_stillEnforcesLeafLimitWithNewConditions() {
        val tree = ConditionNode.Group(children = List(ConditionNode.MAX_LEAVES + 1) { ConditionNode.Leaf(cond(ConditionType.DND_ON)) })
        val result = BackupManager.parseImport(BackupManager.exportToString(listOf(rule(tree = tree))))
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Too many condition leaves")
    }
}
