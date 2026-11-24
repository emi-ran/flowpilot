package com.flowpilot.app.permission

/** True for Xiaomi-family devices (Xiaomi, Redmi, POCO) running MIUI/HyperOS. */
internal fun isXiaomiFamily(manufacturer: String?): Boolean =
    manufacturer?.trim()?.lowercase() in setOf("xiaomi", "redmi", "poco")

/** Show the autostart guidance when access is granted but the listener never bound on a Xiaomi-family device. */
internal fun shouldShowMiuiAutostartHint(manufacturer: String?, status: CapabilityStatus): Boolean =
    isXiaomiFamily(manufacturer) && status == CapabilityStatus.LISTENER_DISCONNECTED
