package pro.panopticon.client.sensor.impl

import com.amazonaws.services.cloudwatch.model.StandardUnit
import pro.panopticon.client.model.Measurement
import pro.panopticon.client.sensor.Sensor
import pro.panopticon.client.util.SystemStatus
import java.text.DecimalFormat
import java.util.ArrayList

class ServerLoadSensor : Sensor {
    override fun measure(): List<Measurement> {
        val s = SystemStatus()
        val measurements: MutableList<Measurement> = ArrayList()
        val load = s.load()
        val formatted = DecimalFormat("#.##").format(load)
        val status = when {
            load > 10 -> "ERROR"
            load > 5 -> "WARN"
            else -> "INFO"
        }

        measurements.add(Measurement("load.avg",
            status,
            formatted,
            Measurement.CloudwatchValue(load, StandardUnit.None),
            DESCRIPTION))

        return measurements
    }

    companion object {
        private const val DESCRIPTION = ""
    }
}
