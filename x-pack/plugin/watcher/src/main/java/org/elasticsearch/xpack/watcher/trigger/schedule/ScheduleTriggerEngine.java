/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.watcher.trigger.schedule;

import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.xpack.core.watcher.support.WatcherDateTimeUtils;
import org.elasticsearch.xpack.core.watcher.trigger.TriggerEvent;
import org.elasticsearch.xpack.watcher.trigger.TriggerEngine;
import org.elasticsearch.xpack.watcher.trigger.TriggerService;
import org.joda.time.DateTime;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.elasticsearch.xpack.core.watcher.support.Exceptions.illegalArgument;
import static org.joda.time.DateTimeZone.UTC;

public abstract class ScheduleTriggerEngine extends AbstractComponent implements TriggerEngine<ScheduleTrigger, ScheduleTriggerEvent> {

    public static final String TYPE = ScheduleTrigger.TYPE;

    protected final List<Consumer<Iterable<TriggerEvent>>> consumers = new CopyOnWriteArrayList<>();
    protected final ScheduleRegistry scheduleRegistry;
    protected final Clock clock;

    public ScheduleTriggerEngine(Settings settings, ScheduleRegistry scheduleRegistry, Clock clock) {
        super(settings);
        this.scheduleRegistry = scheduleRegistry;
        this.clock = clock;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public void register(Consumer<Iterable<TriggerEvent>> consumer) {
        consumers.add(consumer);
    }


    @Override
    public ScheduleTriggerEvent simulateEvent(String jobId, @Nullable Map<String, Object> data, TriggerService service) {
        DateTime now = new DateTime(clock.millis(), UTC);
        if (data == null) {
            return new ScheduleTriggerEvent(jobId, now, now);
        }

        Object value = data.get(ScheduleTriggerEvent.Field.TRIGGERED_TIME.getPreferredName());
        DateTime triggeredTime = value != null ? WatcherDateTimeUtils.convertToDate(value, clock) : now;
        if (triggeredTime == null) {
            throw illegalArgument("could not simulate schedule event. could not convert provided triggered time [{}] to date/time", value);
        }

        value = data.get(ScheduleTriggerEvent.Field.SCHEDULED_TIME.getPreferredName());
        DateTime scheduledTime = value != null ? WatcherDateTimeUtils.convertToDate(value, clock) : triggeredTime;
        if (scheduledTime == null) {
            throw illegalArgument("could not simulate schedule event. could not convert provided scheduled time [{}] to date/time", value);
        }

        return new ScheduleTriggerEvent(jobId, triggeredTime, scheduledTime);
    }

    @Override
    public ScheduleTrigger parseTrigger(String context, XContentParser parser) throws IOException {
        Schedule schedule = scheduleRegistry.parse(context, parser);
        return new ScheduleTrigger(schedule);
    }

    @Override
    public ScheduleTriggerEvent parseTriggerEvent(TriggerService service, String watchId, String context, XContentParser parser) throws
            IOException {
        return ScheduleTriggerEvent.parse(parser, watchId, context, clock);
    }
}
