package pro.panopticon.client

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.BasicHttpEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClientBuilder
import org.slf4j.LoggerFactory
import pro.panopticon.client.awscloudwatch.CloudwatchClient
import pro.panopticon.client.awscloudwatch.HasCloudwatchConfig
import pro.panopticon.client.model.ComponentInfo
import pro.panopticon.client.model.Measurement
import pro.panopticon.client.model.Status
import pro.panopticon.client.sensor.Sensor
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.ArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

class PanopticonClient(
    private val baseUri: String,
    private val hasCloudwatchConfig: HasCloudwatchConfig?,
    private val cloudwatchClient: CloudwatchClient?,
) {
    private val LOG = LoggerFactory.getLogger(this.javaClass)
    private val client: CloseableHttpClient
    private val namespace: String

    fun startScheduledStatusUpdate(componentInfo: ComponentInfo, sensors: List<Sensor>) {
        SCHEDULER.scheduleWithFixedDelay({ performSensorCollection(componentInfo, sensors) }, 0, 1, TimeUnit.MINUTES)
    }

    fun shutdownScheduledStatusUpdate() {
        SCHEDULER.shutdown()
    }

    private fun performSensorCollection(componentInfo: ComponentInfo, sensors: List<Sensor>) {
        try {
            val before = System.currentTimeMillis()
            val measurements = collectMeasurementsFromSensors(sensors)
            val afterMeasurements = System.currentTimeMillis()
            val success = sendMeasurementsToPanopticon(Status(componentInfo, measurements))
            val afterPanopticonPost = System.currentTimeMillis()
            sendSelectMeasurementsToCloudwatch(measurements)
            val afterCloudwatchPost = System.currentTimeMillis()
            val measurementTime = afterMeasurements - before
            val panopticonPostTime = afterPanopticonPost - afterMeasurements
            val cloudwatchPostTime = afterCloudwatchPost - afterPanopticonPost
            if (success) {
                LOG.info(String.format("Sent status update with %d measurements. Fetch measurements took %dms. Posting status to panopticon took %dms. Posting to cloudwatch took %dms",
                    measurements.size,
                    measurementTime,
                    panopticonPostTime,
                    cloudwatchPostTime))
            } else {
                LOG.warn("Could not update status")
            }
        } catch (e: Exception) {
            LOG.warn("Got error when measuring sensors to send to panopticon", e)
        }
    }

    private fun collectMeasurementsFromSensors(sensors: List<Sensor>): List<Measurement?> {
        return sensors.parallelStream()
            .map { sensor: Sensor ->
                try {
                    return@map sensor.measure()
                } catch (e: Exception) {
                    LOG.warn("Got error running sensor: " + sensor.javaClass.name, e)
                    return@map ArrayList<Measurement>()
                }
            }
            .flatMap { it.stream() }
            .collect(Collectors.toList())
    }

    fun sendMeasurementsToPanopticon(status: Status?): Boolean {
        try {
            val json = OBJECT_MAPPER.writeValueAsString(status)
            val uri = "$baseUri/external/status"
            LOG.debug("Updating status: $uri")
            LOG.debug("...with JSON: $json")
            val entity = BasicHttpEntity()
            entity.content = ByteArrayInputStream(json.toByteArray())
            val httpPost = HttpPost(uri)
            httpPost.entity = entity
            httpPost.setHeader("Content-Type", "application/json")
            client.execute(httpPost).use { response ->
                LOG.debug("Response: " + response.statusLine.statusCode)
                return response.statusLine.statusCode < 300
            }
        } catch (e: IOException) {
            LOG.warn("Error when updating status", e)
            return false
        }
    }

    private fun sendSelectMeasurementsToCloudwatch(measurements: List<Measurement?>) {
        val statistics = measurements.stream()
            .filter { m: Measurement? -> m!!.cloudwatchValue != null }
            .map { m: Measurement? ->
                CloudwatchClient.CloudwatchStatistic(
                    m!!.key, m.cloudwatchValue!!.value, m.cloudwatchValue!!.unit)
                    .withDimensions(m.cloudwatchValue!!.dimensions)
            }
            .collect(Collectors.toList())
        if (cloudwatchClient != null && hasCloudwatchConfig != null && hasCloudwatchConfig.sensorStatisticsEnabled()) {
            cloudwatchClient.sendStatistics(namespace, statistics)
        }
    }

    private fun createHttpClient(): CloseableHttpClient {
        val requestConfig = RequestConfig.custom()
            .setSocketTimeout(TIMEOUT)
            .setConnectTimeout(TIMEOUT)
            .setConnectionRequestTimeout(TIMEOUT)
            .build()
        return HttpClientBuilder.create()
            .setDefaultRequestConfig(requestConfig)
            .build()
    }

    companion object {
        private const val TIMEOUT = 10000
        private val OBJECT_MAPPER = ObjectMapper()
        private val SCHEDULER = Executors.newScheduledThreadPool(1)
    }

    init {
        client = createHttpClient()
        namespace = String.format("sensor-%s-%s", hasCloudwatchConfig?.appName, hasCloudwatchConfig?.environment)
    }
}
