package pro.panopticon.client.eventlogger

import org.slf4j.LoggerFactory
import pro.panopticon.client.awscloudwatch.CloudwatchClient
import pro.panopticon.client.awscloudwatch.HasCloudwatchConfig
import pro.panopticon.client.model.Measurement
import pro.panopticon.client.model.MetricDimension
import pro.panopticon.client.sensor.Sensor
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Vector
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.DoubleAdder
import java.util.stream.Collectors
import java.util.stream.Stream

open class AbstractEventLogger(hasCloudwatchConfig: HasCloudwatchConfig, cloudwatchClient: CloudwatchClient?) : Sensor {
    private val LOG = LoggerFactory.getLogger(this.javaClass)
    private val namespace: String
    private var counts: ConcurrentMap<String, DoubleAdder> = ConcurrentHashMap()
    private var ticksWithDimensions = Vector<CloudwatchClient.CloudwatchStatistic>()
    private val hasCloudwatchConfig: HasCloudwatchConfig?
    private val cloudwatchClient: CloudwatchClient?

    open fun tickAndLog(event: HasEventInfo, vararg logappends: String) {
        tickAndLog(event, 1.0, *logappends)
    }

    fun tickAndLogException(event: HasEventInfo, e: Exception) {
        tickAndLog(event, 1.0, stackTraceToString(e))
    }

    private fun stackTraceToString(e: Exception): String {
        val stringWriter = StringWriter()
        e.printStackTrace(PrintWriter(stringWriter))
        return stringWriter.toString()
    }

    open fun tickAndLog(event: HasEventInfo, count: Double, vararg logappends: String) {
        performLog(event, *logappends)
        performTick(event, count)
    }

    open fun tick(event: HasEventInfo) {
        tick(event, 1.0)
    }

    open fun tick(event: HasEventInfo, count: Double) {
        performTick(event, count)
    }

    fun tick(event: HasEventInfo, vararg dimensions: MetricDimension?) {
        val statistics = CloudwatchClient.CloudwatchStatistic(
            event.eventName,
            1.0
        ).withDimensions(listOf(*dimensions))
        ticksWithDimensions.add(statistics)
    }

    fun tick(event: HasEventInfo, count: Double, vararg dimensions: MetricDimension) {
        val statistics = CloudwatchClient.CloudwatchStatistic(
            event.eventName,
            count
        ).withDimensions(listOf(*dimensions))
        ticksWithDimensions.add(statistics)
    }

    private fun performLog(event: HasEventInfo, vararg logappends: String) {
        LOG.info("AUDIT EVENT - [" + event.eventType + "] - [" + event.eventName + "] - " + Stream.of(*logappends)
            .map { s: String -> "[$s]" }
            .collect(Collectors.joining(" - ")))
    }

    private fun performTick(event: HasEventInfo, count: Double) {
        counts.computeIfAbsent(event.eventName) { s: String? -> DoubleAdder() }.add(count)
    }

    override fun measure(): List<Measurement> {
        val countsToProcess = counts
        val countsWithDimensionToProcess = ticksWithDimensions
        resetTicks()
        if (statisticsEnabled()) {
            val statistics = createCountStatistics(countsToProcess)
            statistics.addAll(countsWithDimensionToProcess)
            cloudwatchClient!!.sendStatistics(namespace, statistics)
        }
        return countsToProcess.entries.stream()
            .map { e: Map.Entry<String, DoubleAdder> ->
                Measurement("audit." + e.key,
                    "INFO",
                    "Last minute: " + e.value.toDouble(),
                    "")
            }
            .collect(Collectors.toList())
    }

    private fun statisticsEnabled(): Boolean {
        return cloudwatchClient != null && hasCloudwatchConfig != null && hasCloudwatchConfig.auditeventStatisticsEnabled()
    }

    private fun resetTicks() {
        counts = ConcurrentHashMap()
        ticksWithDimensions = Vector()
    }

    private fun createCountStatistics(countsToProcess: ConcurrentMap<String, DoubleAdder>): MutableList<CloudwatchClient.CloudwatchStatistic> {
        return countsToProcess.entries.stream()
            .map { e: Map.Entry<String, DoubleAdder> ->
                CloudwatchClient.CloudwatchStatistic(e.key,
                    e.value.toDouble())
            }
            .collect(Collectors.toList())
    }

    init {
        this.hasCloudwatchConfig = hasCloudwatchConfig
        this.cloudwatchClient = cloudwatchClient
        namespace = String.format("audit-%s-%s", hasCloudwatchConfig.appName, hasCloudwatchConfig.environment)
    }
}
