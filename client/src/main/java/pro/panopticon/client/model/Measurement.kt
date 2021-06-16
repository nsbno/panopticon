package pro.panopticon.client.model

import com.amazonaws.services.cloudwatch.model.StandardUnit

class Measurement(
    var key: String,
    var status: String,
    var displayValue: String,
    var cloudwatchValue: CloudwatchValue?,
    var description: String?,
) {
    constructor(key: String, status: String, displayValue: String, description: String?) : this(key,
        status,
        displayValue,
        null,
        description)

    override fun toString(): String {
        return "Measurement{" +
               "key='" + key + '\'' +
               ", status='" + status + '\'' +
               ", displayValue='" + displayValue + '\'' +
               ", cloudwatchValue=" + cloudwatchValue +
               ", description=" + description +
               '}'
    }

    class CloudwatchValue @JvmOverloads constructor(
        val value: Double,
        val unit: StandardUnit,
        val dimensions: List<MetricDimension> = emptyList(),
    )
}
