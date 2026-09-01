package com.flowpilot.app.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BluetoothDeviceReducerTest {
    @Test fun first_disconnect_after_start_is_emitted_without_replaying_an_initial_connection() {
        val initial = BluetoothTrackerState()

        val disconnected = BluetoothDeviceReducer.reduce(initial, BluetoothDeviceEvent.DISCONNECTED, "AA:BB:CC:DD:EE:FF")

        assertThat(disconnected.second).isEqualTo(BluetoothDeviceTransition(BluetoothDeviceEvent.DISCONNECTED, "AA:BB:CC:DD:EE:FF"))
        assertThat(disconnected.first.connectedAddresses).isEmpty()
    }

    @Test fun duplicate_consecutive_transitions_are_suppressed_per_address() {
        val connected = BluetoothDeviceReducer.reduce(BluetoothTrackerState(), BluetoothDeviceEvent.CONNECTED, "aa:bb:cc:dd:ee:ff", "Headphones")
        val duplicateConnect = BluetoothDeviceReducer.reduce(connected.first, BluetoothDeviceEvent.CONNECTED, "AA:BB:CC:DD:EE:FF", "Headphones")
        val disconnected = BluetoothDeviceReducer.reduce(duplicateConnect.first, BluetoothDeviceEvent.DISCONNECTED, "AA:BB:CC:DD:EE:FF")
        val duplicateDisconnect = BluetoothDeviceReducer.reduce(disconnected.first, BluetoothDeviceEvent.DISCONNECTED, "AA:BB:CC:DD:EE:FF")

        assertThat(connected.second).isEqualTo(BluetoothDeviceTransition(BluetoothDeviceEvent.CONNECTED, "AA:BB:CC:DD:EE:FF", "Headphones"))
        assertThat(duplicateConnect.second).isNull()
        assertThat(disconnected.second).isEqualTo(BluetoothDeviceTransition(BluetoothDeviceEvent.DISCONNECTED, "AA:BB:CC:DD:EE:FF"))
        assertThat(duplicateDisconnect.second).isNull()
    }
}
