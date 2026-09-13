package com.itantra.app.mesh

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * RSSI -> distance estimation and smoothing for the iTantra mesh.
 *
 * Pure Kotlin (no Android imports) so it can be unit-tested on the host JVM.
 */

/** Default BLE transmit power in dBm assumed for iTantra beacon advertisements. */
const val DEFAULT_TX_POWER_DBM: Int = -59

/** Default path-loss exponent; 2.7 is typical for cluttered disaster sites. */
const val DEFAULT_PATH_LOSS_EXPONENT: Double = 2.7

/**
 * Log-Distance Path Loss model:
 *
 *     distance = 10 ^ ((txPower - rssi) / (10 * n))
 *
 * At rssi == txPowerDbm the result is exactly 1.0 meter.
 */
fun estimateMeters(
    rssi: Int,
    txPowerDbm: Int = DEFAULT_TX_POWER_DBM,
    pathLossExponent: Double = DEFAULT_PATH_LOSS_EXPONENT
): Double {
    require(pathLossExponent > 0.0) { "pathLossExponent must be positive" }
    val exponent = (txPowerDbm - rssi) / (10.0 * pathLossExponent)
    return 10.0.pow(exponent)
}

/**
 * Computes Great-Circle bearing angle from (lat1, lon1) to (lat2, lon2) in degrees (0..360°).
 */
fun calculateBearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val dLon = Math.toRadians(lon2 - lon1)
    val lat1Rad = Math.toRadians(lat1)
    val lat2Rad = Math.toRadians(lat2)
    val y = sin(dLon) * cos(lat2Rad)
    val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)
    val bearing = Math.toDegrees(atan2(y, x))
    return ((bearing.toFloat() % 360f) + 360f) % 360f
}

/**
 * Fuses GPS great-circle distance with BLE RSSI distance estimation.
 *
 * Consumer smartphones indoors suffer from multipath GPS reflections and dilution
 * of precision (typically 20m - 50m error between two devices in the same room).
 * When BLE signals are strong (rssi >= -86 dBm), the physical 2.4 GHz radio propagation
 * provides far superior short-range proximity information and clamps indoor GPS drift.
 */
fun fuseGpsAndBleDistance(gpsDist: Int, bleDist: Int, rssi: Int): Int {
    return when {
        // Very strong BLE: devices are in close physical proximity (same room / desk, < 4m).
        // Indoor GPS drift of 20-60m must be completely overridden by BLE ranging.
        rssi >= -68 -> bleDist.coerceIn(1, 4)

        // Strong BLE: devices are within ~10m.
        rssi >= -76 -> minOf(gpsDist, bleDist.coerceIn(1, 10))

        // Moderate BLE: devices are within ~25m (BLE single-hop reach).
        // Clamps indoor/reflected GPS drift to BLE estimate.
        rssi >= -86 -> minOf(gpsDist, bleDist.coerceIn(1, 25))

        // Fringe BLE or long range: trust GPS.
        else -> gpsDist
    }
}

/**
 * Smooths compass heading (0..360°) using an angular low-pass filter (EMA),
 * properly wrapping across the 0°/360° boundary to avoid 359° spin artifacts.
 */
fun smoothCompassHeading(prev: Float, target: Float, alpha: Float = 0.22f): Float {
    var diff = (target - prev) % 360f
    if (diff > 180f) diff -= 360f
    if (diff < -180f) diff += 360f
    val next = (prev + diff * alpha) % 360f
    return (next + 360f) % 360f
}

/**
 * A 1-D Kalman filter for smoothing noisy RSSI readings.
 *
 * State model: the true RSSI is a slowly-wandering scalar. `processNoise` is
 * how much the true value may wander between measurements, `measurementNoise`
 * is the variance of a single RSSI sample.
 */
class KalmanRssiFilter(
    initialRssi: Double,
    processNoise: Double = 0.5,
    measurementNoise: Double = 1.5
) {
    private val initial: Double = initialRssi
    private var estimate: Double = initialRssi
    private var errorCovariance: Double = 1.0

    private val q = processNoise
    private val r = measurementNoise

    /** The current filtered estimate, in dBm. */
    var currentEstimate: Double = estimate
        private set

    /**
     * Runs one predict + update cycle.
     *
     * @return the new filtered RSSI estimate in dBm.
     */
    fun update(measurement: Double): Double {
        // Predict: static model — the estimate is unchanged, uncertainty grows.
        val predictedEstimate = estimate
        val predictedCovariance = errorCovariance + q

        // Update: blend the measurement in proportion to our confidence.
        val kalmanGain = predictedCovariance / (predictedCovariance + r)
        estimate = predictedEstimate + kalmanGain * (measurement - predictedEstimate)
        errorCovariance = (1.0 - kalmanGain) * predictedCovariance

        currentEstimate = estimate
        return estimate
    }

    /** Resets the filter to its initial estimate and covariance. */
    fun reset() {
        estimate = initial
        errorCovariance = 1.0
        currentEstimate = estimate
    }
}

/**
 * Per-node convenience tracker: feeds raw RSSI through a [KalmanRssiFilter]
 * and reports smoothed distances in meters, plus an unsmoothed
 * [rssiToDistance] helper for quick lookups.
 */
class RangedNodeDistanceTracker(
    initialRssi: Double = -75.0,
    private val txPowerDbm: Int = DEFAULT_TX_POWER_DBM,
    private val pathLossExponent: Double = DEFAULT_PATH_LOSS_EXPONENT
) {
    private val filter = KalmanRssiFilter(initialRssi)

    /** Direct (unfiltered) RSSI -> meters conversion. */
    fun rssiToDistance(rssi: Int): Double = estimateMeters(rssi, txPowerDbm, pathLossExponent)

    /** Distance of the current smoothed RSSI estimate, in meters. */
    val currentDistanceMeters: Double
        get() = rssiToDistance(filter.currentEstimate.toInt())

    /**
     * Feeds a new raw RSSI sample through the Kalman filter.
     *
     * @return the smoothed distance in meters.
     */
    fun updateRssi(rssi: Int): Double {
        val smoothed = filter.update(rssi.toDouble())
        return rssiToDistance(smoothed.toInt())
    }

    /** Resets the underlying filter. */
    fun reset() = filter.reset()
}
