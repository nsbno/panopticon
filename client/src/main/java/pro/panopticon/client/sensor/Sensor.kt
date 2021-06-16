package pro.panopticon.client.sensor

import pro.panopticon.client.model.Measurement
import java.util.Objects

fun interface Sensor {
    fun measure(): List<Measurement>
    class AlertInfo(
        /**
         * Key used to separate alerts from each other.
         * Example:
         * "entur.rest.calls"
         */
        val sensorKey: String,
        /**
         * A human / guard-friendly description of what is happening and which actions that needs to be taken.
         *
         * Example:
         * "When this alert is triggered, the critical Feature X is not working properly. You should contact Company Y."
         */
        val description: String,
    ) {

        override fun equals(o: Any?): Boolean {
            if (this === o) return true
            if (o == null || javaClass != o.javaClass) return false
            val alertInfo = o as AlertInfo
            return sensorKey == alertInfo.sensorKey &&
                   description == alertInfo.description
        }

        override fun hashCode(): Int {
            return Objects.hash(sensorKey, description)
        }

        override fun toString(): String {
            return "AlertInfo{" +
                   "sensorKey='" + sensorKey + '\'' +
                   ", description='" + description + '\'' +
                   '}'
        }
    }
}
