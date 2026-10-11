package com.example.billing.auth

/**
 * Is laptop / phone ki pehchan.
 * fingerprint = machine ki apni pehchan (Windows MachineGuid / Android ID)
 * label       = developer ko dikhane wala naam (jaise "ALI-PC (Windows 11)")
 */
data class DeviceIdentity(val fingerprint: String, val label: String)
