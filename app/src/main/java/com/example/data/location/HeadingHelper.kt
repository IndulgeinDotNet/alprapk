package com.example.data.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class HeadingData(val degrees: Float, val compassLabel: String)

private val compassLabels = listOf(
    "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
    "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
)

fun headingLabelFor(degrees: Float): String {
    val normalized = ((degrees % 360f) + 360f) % 360f
    val index = ((normalized / 22.5f) + 0.5f).toInt() % 16
    return compassLabels[index]
}

/**
 * Reads the direction the device (camera) was facing at capture time from the device's
 * rotation-vector sensor - this is what lets a sighting record which way the vehicle was
 * facing/traveling, not just where it was.
 */
class HeadingHelper(context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    /**
     * Reads a single compass heading sample. Returns null if the device has no rotation
     * sensor or no reading arrives within [timeoutMs].
     */
    suspend fun getCurrentHeading(timeoutMs: Long = 1500L): HeadingData? {
        val manager = sensorManager ?: return null
        val sensor = rotationSensor ?: return null

        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val rotationMatrix = FloatArray(9)
                val orientation = FloatArray(3)
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        SensorManager.getOrientation(rotationMatrix, orientation)
                        val azimuthDegrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
                        val normalized = ((azimuthDegrees % 360f) + 360f) % 360f
                        manager.unregisterListener(this)
                        if (cont.isActive) cont.resume(HeadingData(normalized, headingLabelFor(normalized)))
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }
                manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
                cont.invokeOnCancellation { manager.unregisterListener(listener) }
            }
        }
    }
}
