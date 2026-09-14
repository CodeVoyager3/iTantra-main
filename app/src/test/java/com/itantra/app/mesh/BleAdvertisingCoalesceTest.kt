package com.itantra.app.mesh

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the BLE advertising coalescer that keeps the SOS/Rescue
 * beacon on the air: volatile telemetry (coordinates/battery) must not force an
 * advertiser restart on every GPS tick, while a role change must.
 */
class BleAdvertisingCoalesceTest {

    private fun beacon(
        nodeId: Long = 1000L,
        lat: Double = 19.0,
        lon: Double = 72.0,
        battery: Int = 80,
        distress: Boolean = true,
        altitude: Int = 0
    ) = DistressBeaconPayload(
        nodeId = nodeId,
        batteryPercent = battery,
        latitudeDeg = lat,
        longitudeDeg = lon,
        altitudeMeters = altitude,
        languageIso = "hi",
        isDistress = distress
    )

    @Test
    fun nothingOnAirAlwaysRestarts() {
        assertTrue(
            shouldRestartAdvertising(
                onAirIdentity = null,
                onAirBytes = null,
                requested = beacon(),
                lastRestartEpochMs = 0L,
                nowEpochMs = 1_000L,
                refreshMinIntervalMs = 15_000L
            )
        )
    }

    @Test
    fun volatileTelemetryChangeDoesNotRestartInsideWindow() {
        val onAir = beacon(lat = 19.0, lon = 72.0, battery = 80)
        val moved = beacon(lat = 19.0001, lon = 72.0001, battery = 79)
        assertFalse(
            shouldRestartAdvertising(
                onAirIdentity = BeaconIdentity.of(onAir),
                onAirBytes = onAir.toManufacturerData(),
                requested = moved,
                lastRestartEpochMs = 1_000L,
                nowEpochMs = 2_000L,
                refreshMinIntervalMs = 15_000L
            )
        )
    }

    @Test
    fun volatileTelemetryChangeRestartsAfterWindow() {
        val onAir = beacon(lat = 19.0, lon = 72.0)
        val moved = beacon(lat = 19.01, lon = 72.01)
        assertTrue(
            shouldRestartAdvertising(
                onAirIdentity = BeaconIdentity.of(onAir),
                onAirBytes = onAir.toManufacturerData(),
                requested = moved,
                lastRestartEpochMs = 1_000L,
                nowEpochMs = 20_000L,
                refreshMinIntervalMs = 15_000L
            )
        )
    }

    @Test
    fun roleChangeRestartsImmediately() {
        val victim = beacon(distress = true, altitude = 0)
        val rescuerIdle = beacon(distress = false, altitude = 0)
        assertTrue(
            shouldRestartAdvertising(
                onAirIdentity = BeaconIdentity.of(victim),
                onAirBytes = victim.toManufacturerData(),
                requested = rescuerIdle,
                lastRestartEpochMs = 1_000L,
                nowEpochMs = 1_100L,
                refreshMinIntervalMs = 15_000L
            )
        )
    }

    @Test
    fun identicalPayloadNeverRestarts() {
        val b = beacon()
        assertFalse(
            shouldRestartAdvertising(
                onAirIdentity = BeaconIdentity.of(b),
                onAirBytes = b.toManufacturerData(),
                requested = b,
                lastRestartEpochMs = 1_000L,
                nowEpochMs = 100_000L,
                refreshMinIntervalMs = 15_000L
            )
        )
    }
}