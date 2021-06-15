package pro.panopticon.client.sensor.impl

import com.amazonaws.services.cloudwatch.model.StandardUnit
import pro.panopticon.client.model.Measurement
import pro.panopticon.client.sensor.Sensor
import pro.panopticon.client.util.SystemStatus
import java.util.ArrayList

class FileHandlesSensor(private val warnAfter: Long, private val errorAfter: Long) : Sensor {
    override fun measure(): List<Measurement> {
        val s = SystemStatus()
        val measurements: MutableList<Measurement> = ArrayList()
        val open = s.openFileHandles()
        val max = s.maxFileHandles()
        val percent = open.toDouble() / max.toDouble() * 100
        val displayValue = String.format("%s of %s filehandles used (%.2f%%)", open, max, percent)
        measurements.add(Measurement("filehandles",
            statusFromOpenFileHandles(open),
            displayValue,
            Measurement.CloudwatchValue(percent, StandardUnit.Percent),
            DESCRIPTION))
        return measurements
    }

    private fun statusFromOpenFileHandles(open: Long): String {
        return if (open >= errorAfter) {
            "ERROR"
        } else if (open >= warnAfter) {
            "WARN"
        } else {
            "INFO"
        }
    }

    companion object {
        private const val DESCRIPTION = ""
    }
}
