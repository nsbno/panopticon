package pro.panopticon.client.sensor.impl

import pro.panopticon.client.model.Measurement
import pro.panopticon.client.sensor.Sensor
import java.util.ArrayList

class JavaVersionSensor : Sensor {
    private val version = System.getProperty("java.version")
    override fun measure(): List<Measurement> {
        val measurements: MutableList<Measurement> = ArrayList()
        measurements.add(Measurement("java.version", "INFO", version, DESCRIPTION))
        return measurements
    }

    companion object {
        private const val DESCRIPTION = ""
    }
}
