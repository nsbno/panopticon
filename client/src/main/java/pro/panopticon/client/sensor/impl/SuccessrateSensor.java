package pro.panopticon.client.sensor.impl;

import com.amazonaws.services.cloudwatch.model.StandardUnit;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.panopticon.client.model.Measurement;
import pro.panopticon.client.sensor.Sensor;
import pro.panopticon.client.util.NowSupplier;
import pro.panopticon.client.util.NowSupplierImpl;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static java.util.stream.Collectors.toList;

public class SuccessrateSensor implements Sensor {

    private final Logger LOG = LoggerFactory.getLogger(this.getClass());

    /**
     * Number of events to keep.
     */
    private final int numberToKeep;

    /**
     * Triggers an alert to Slack when reached.
     * Should always be a Double between 0.00 and 1.00
     * Format: percentage / 100
     * <p>
     * Example: 0.1 will trigger a warning at 10% failure rate
     */
    private final Double warnLimit;

    /**
     * Triggers an alert to Slack and PagerDuty when reached.
     * Should always be a Double between 0.00 and 1.00
     * Format: percentage / 100
     * <p>
     * Example: 0.2 will trigger an alert at 20% failure rate
     */
    private final Double errorLimit;

    private final Map<AlertInfo, CircularFifoQueue<Tick>> eventQueues = new HashMap<>();
    private final NowSupplier nowSupplier;

    public SuccessrateSensor(int numberToKeep, Double warnLimit, Double errorLimit) {
        this.numberToKeep = numberToKeep;
        this.warnLimit = warnLimit;
        this.errorLimit = errorLimit;
        this.nowSupplier = new NowSupplierImpl();
    }

    SuccessrateSensor(int numberToKeep, Double warnLimit, Double errorLimit, NowSupplier nowSupplier) {
        this.numberToKeep = numberToKeep;
        this.warnLimit = warnLimit;
        this.errorLimit = errorLimit;
        this.nowSupplier = nowSupplier;
    }
    @Deprecated
    public synchronized void tickSuccess(String key) {
        tickSuccess(new AlertInfo(key, null));
    }

    @Deprecated
    public synchronized void tickFailure(String key) {
        tickFailure(new AlertInfo(key, null));
    }

    public synchronized void tickFailure(AlertInfo alertInfo) {
        try {
            getQueueForKey(alertInfo).add(new Tick(Event.FAILURE, nowSupplier.now()));
        } catch (Exception e) {
            LOG.warn("Something went wrong when counting FAILURE for " + alertInfo.getSensorKey(), e);
        }
    }

    public synchronized void tickSuccess(AlertInfo alertInfo) {
        try {
            getQueueForKey(alertInfo).add(new Tick(Event.SUCCESS, nowSupplier.now()));
        } catch (Exception e) {
            LOG.warn("Something went wrong when counting SUCCESS for " + alertInfo.getSensorKey(), e);
        }
    }

    private CircularFifoQueue<Tick> getQueueForKey(AlertInfo key) {
        return eventQueues.computeIfAbsent(key, k -> new CircularFifoQueue<>(numberToKeep));
    }

    @Override
    public List<Measurement> measure() {
        return eventQueues.entrySet().stream()
                .map((Map.Entry<AlertInfo, CircularFifoQueue<Tick>> e) -> {
                    AlertInfo alertInfo = e.getKey();
                    List<Tick> events = new ArrayList<>(e.getValue());
                    int all = events.size();
                    long success = events.stream().filter(tick -> tick.event == Event.SUCCESS).count();
                    long failure = events.stream().filter(tick -> tick.event == Event.FAILURE).count();
                    double percentFailureDouble = all > 0 ? (double) failure / (double) all : 0;
                    boolean enoughDataToAlert = all == numberToKeep;
                    boolean allTicksAreTooOld = allTicksAreTooOld(events);
                    String display = String.format("Last %s calls: %s success, %s failure (%.2f%% failure)%s%s",
                            Integer.min(all, numberToKeep),
                            success,
                            all - success,
                            percentFailureDouble * 100,
                            enoughDataToAlert ? "" : " - not enough calls to report status yet",
                            allTicksAreTooOld ? "" : " - all ticks are outdated"
                    );
                    String status = decideStatus(enoughDataToAlert, percentFailureDouble, allTicksAreTooOld);
                    return new Measurement(alertInfo.getSensorKey(), status, display, new Measurement.CloudwatchValue(percentFailureDouble * 100, StandardUnit.Percent), alertInfo.getDescription());
                })
                .collect(toList());
    }

    private String decideStatus(boolean enoughDataToAlert, double percentFailure, boolean allTicksAreTooOld) {
        if (!enoughDataToAlert) return "INFO";
        if (allTicksAreTooOld) return "INFO";
        if (errorLimit != null && percentFailure >= errorLimit) return "ERROR";
        if (warnLimit != null && percentFailure >= warnLimit) return "WARN";
        return "INFO";
    }

    private boolean allTicksAreTooOld(List<Tick> events) {
        Optional<Tick> tickFromLastHour = events.stream()
                .filter(tick -> ChronoUnit.HOURS.between(tick.createdAt, nowSupplier.now()) < 1)
                .findAny();
        if (tickFromLastHour.isPresent() || events.isEmpty()) {
            return false;
        } else {
            return true;
        }
    }

    private enum Event {
        SUCCESS,
        FAILURE
    }

    private static class Tick {
        private final Event event;
        private final LocalDateTime createdAt;

        private Tick(Event event, LocalDateTime createdAt) {
            this.event = event;
            this.createdAt = createdAt;
        }
    }

}
