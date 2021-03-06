/*
 * Copyright (c) 2017 Dell Inc., or its subsidiaries. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 */
package nytaxi;

import io.pravega.client.stream.Stream;
import io.pravega.connectors.flink.FlinkPravegaWriter;
import io.pravega.connectors.flink.serialization.JsonSerializer;
import io.pravega.connectors.flink.serialization.PravegaSerializationSchema;
import lombok.extern.slf4j.Slf4j;
import nytaxi.common.Helper;
import nytaxi.common.TripRecord;
import nytaxi.common.ZoneLookup;
import nytaxi.source.TaxiDataSource;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.io.IOException;
import java.util.Map;

import static nytaxi.common.Constants.TRIP_DATA;
import static nytaxi.common.Constants.ZONE_LOOKUP_DATA;

@Slf4j
public class PrepareMain extends AbstractHandler {

    public PrepareMain() {
        super();
    }

    @Override
    public void handleRequest() {

        if (isCreate()) {
            createStream();
        }

        Stream streamInfo = Stream.of(getScope(), getTripStream());

        FlinkPravegaWriter<TripRecord> writer = FlinkPravegaWriter.<TripRecord>builder()
                .withPravegaConfig(getPravegaConfig())
                .forStream(streamInfo)
                .withSerializationSchema(new PravegaSerializationSchema<>(new JsonSerializer<>(TripRecord.class)))
                .withEventRouter(r -> String.valueOf(r.getStartLocationId()))
                .build();

        StreamExecutionEnvironment env = getStreamExecutionEnvironment();

        Helper helper = new Helper();
        Map<Integer, ZoneLookup> zoneLookupRecordMap;
        try {
            zoneLookupRecordMap = helper.parseZoneData(ZONE_LOOKUP_DATA);
        } catch (IOException e) {
            log.error("failed to read zone-lookup data", e);
            return;
        }

        TaxiDataSource taxiDataSource = new TaxiDataSource(TRIP_DATA, zoneLookupRecordMap);

        DataStream<TripRecord> streamSource = env.addSource(taxiDataSource).name("source");

        streamSource.print();

        streamSource.addSink(writer);

        try {
            env.execute("ingest");
        } catch (Exception e) {
            log.error("fail to ingest data", e);
        }
    }
}