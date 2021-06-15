package pro.panopticon.client.sensor.impl

import com.amazonaws.services.cloudwatch.model.StandardUnit
import pro.panopticon.client.model.Measurement
import pro.panopticon.client.model.MetricDimension.Companion.instanceDimension
import pro.panopticon.client.sensor.Sensor
import pro.panopticon.client.util.SystemStatus
import java.util.ArrayList
import java.util.Optional

class MemorySensor @JvmOverloads constructor(
    private val warnLimitNow: Int = 85,
    private val errorLimitNow: Int = 95,
    private val warnLimitHeap: Int = 75,
    private val errorLimitHeap: Int = 95,
    hostname: String? = null
) : Sensor {
    private val hostname: Optional<String> = Optional.ofNullable(hostname)
    override fun measure(): List<Measurement> {
        val s = SystemStatus()
        val measurements: MutableList<Measurement> = ArrayList()
        putMemoryStatus(measurements, "mem.heap.now", s.heapUsed(), s.heapMax(), warnLimitNow, errorLimitNow)
        putMemoryStatus(measurements, "mem.heap.lastGC", s.heapAfterGC(), s.heapMax(), warnLimitHeap, errorLimitHeap)
        return measurements
    }

    private fun putMemoryStatus(
        measurements: MutableList<Measurement>,
        key: String,
        used: Long,
        max: Long,
        warnLimit: Int,
        errorLimit: Int
    ) {
        if (max == 0L || used == -1L) {
            return
        }
        val percentUsed = used / (max / 100)
        val displayValue = toMB(used).toString() + " of " + toMB(max) + " MB (" + percentUsed + "%)"
        val dimensions = hostname
            .map { h: String? ->
                listOf(instanceDimension(
                    h!!))
            }
            .orElse(emptyList())
        measurements.add(
            Measurement(
                key,
                status(percentUsed, warnLimit, errorLimit),
                displayValue,
                Measurement.CloudwatchValue(
                    percentUsed.toDouble(),
                    StandardUnit.Percent,
                    dimensions
                ),
                DESCRIPTION))
    }

    private fun status(percentUsed: Long, warnLimit: Int, errorLimit: Int): String {
        return if (percentUsed > errorLimit) {
            "ERROR"
        } else if (percentUsed > warnLimit) {
            "WARN"
        } else {
            "INFO"
        }
    }

    private fun toMB(bytes: Long): Long {
        return bytes / BYTES_IN_MB
    }

    companion object {
        private const val DESCRIPTION =
            "When this alarm is triggered, you should check the memory status of the other nodes as well. " +
            "There might be a memory leak somewhere in the application triggering this, so a restart will buy you some time"
        private const val BYTES_IN_MB = (1024 * 1024).toLong()
    }
}
