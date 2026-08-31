package com.flowpilot.app.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WifiStateReducerTest {

    @Test
    fun baseline_seeding_suppresses_replay_of_initial_connected_ssid() {
        val initial = WifiTrackerState()
        val baseline = WifiStateReducer.establishBaseline(initial, "Home-WiFi", "net1")

        assertThat(baseline.baselineEstablished).isTrue()
        assertThat(baseline.currentSsid).isEqualTo("Home-WiFi")
        assertThat(baseline.currentNetwork).isEqualTo("net1")

        // Duplicate capabilities callback for already tracked network and SSID should produce no transitions
        val duplicate = WifiStateReducer.onCapabilitiesChanged(baseline, "net1", "Home-WiFi")
        assertThat(duplicate.transitions).isEmpty()
        assertThat(duplicate.state.currentSsid).isEqualTo("Home-WiFi")
    }

    @Test
    fun cellular_default_with_wifi_callback_transitions_to_connected() {
        // Device starts on cellular (initial wifi is null)
        val initial = WifiTrackerState()
        val baseline = WifiStateReducer.establishBaseline(initial, null)

        assertThat(baseline.baselineEstablished).isTrue()
        assertThat(baseline.currentSsid).isNull()

        // NetworkCallback for Wi-Fi arrives with SSID derived directly from transportInfo
        val connectedResult = WifiStateReducer.onCapabilitiesChanged(baseline, "wifi_net_1", "Office-5G")

        assertThat(connectedResult.transitions).containsExactly(
            WifiTransition(WifiStateEvent.CONNECTED, "Office-5G")
        )
        assertThat(connectedResult.state.currentNetwork).isEqualTo("wifi_net_1")
        assertThat(connectedResult.state.currentSsid).isEqualTo("Office-5G")
    }

    @Test
    fun ssid_swap_emits_disconnected_then_connected() {
        val state = WifiTrackerState(
            currentNetwork = "net1",
            currentSsid = "Home-WiFi",
            baselineEstablished = true,
        )

        val swapResult = WifiStateReducer.onCapabilitiesChanged(state, "net2", "Guest-WiFi")

        assertThat(swapResult.transitions).containsExactly(
            WifiTransition(WifiStateEvent.DISCONNECTED, "Home-WiFi"),
            WifiTransition(WifiStateEvent.CONNECTED, "Guest-WiFi"),
        ).inOrder()
        assertThat(swapResult.state.currentNetwork).isEqualTo("net2")
        assertThat(swapResult.state.currentSsid).isEqualTo("Guest-WiFi")
    }

    @Test
    fun duplicate_capabilities_with_same_ssid_is_suppressed() {
        val state = WifiTrackerState(
            currentNetwork = "net1",
            currentSsid = "Home-WiFi",
            baselineEstablished = true,
        )

        val res1 = WifiStateReducer.onCapabilitiesChanged(state, "net1", "Home-WiFi")
        assertThat(res1.transitions).isEmpty()
        assertThat(res1.state).isEqualTo(state)
    }

    @Test
    fun irrelevant_non_wifi_or_other_network_loss_does_not_drop_active_wifi() {
        val state = WifiTrackerState(
            currentNetwork = "wifi_net_1",
            currentSsid = "Home-WiFi",
            baselineEstablished = true,
        )

        // Cellular network lost callback
        val lossResult = WifiStateReducer.onNetworkLost(state, "cellular_net_0")

        assertThat(lossResult.transitions).isEmpty()
        assertThat(lossResult.state.currentSsid).isEqualTo("Home-WiFi")
        assertThat(lossResult.state.currentNetwork).isEqualTo("wifi_net_1")
    }

    @Test
    fun active_wifi_network_loss_emits_disconnected() {
        val state = WifiTrackerState(
            currentNetwork = "wifi_net_1",
            currentSsid = "Home-WiFi",
            baselineEstablished = true,
        )

        val lossResult = WifiStateReducer.onNetworkLost(state, "wifi_net_1")

        assertThat(lossResult.transitions).containsExactly(
            WifiTransition(WifiStateEvent.DISCONNECTED, "Home-WiFi")
        )
        assertThat(lossResult.state.currentSsid).isNull()
        assertThat(lossResult.state.currentNetwork).isNull()
    }

    @Test
    fun null_ssid_capabilities_disconnects_tracked_network() {
        val state = WifiTrackerState(
            currentNetwork = "wifi_net_1",
            currentSsid = "Home-WiFi",
            baselineEstablished = true,
        )

        val nullSsidResult = WifiStateReducer.onCapabilitiesChanged(state, "wifi_net_1", null)

        assertThat(nullSsidResult.transitions).containsExactly(
            WifiTransition(WifiStateEvent.DISCONNECTED, "Home-WiFi")
        )
        assertThat(nullSsidResult.state.currentSsid).isNull()
        assertThat(nullSsidResult.state.currentNetwork).isNull()
    }
}
