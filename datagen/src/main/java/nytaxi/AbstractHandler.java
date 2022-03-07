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

import io.pravega.client.ClientConfig;
import io.pravega.client.stream.ScalingPolicy;
import io.pravega.client.stream.Stream;
import io.pravega.client.stream.StreamConfiguration;
import io.pravega.connectors.flink.PravegaConfig;
import lombok.Data;
import nytaxi.common.Helper;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.net.URI;

import static nytaxi.common.Constants.CREATE_STREAM;
import static nytaxi.common.Constants.DEFAULT_CONTROLLER_URI;
import static nytaxi.common.Constants.DEFAULT_NO_SEGMENTS;
import static nytaxi.common.Constants.DEFAULT_POPULAR_DEST_THRESHOLD;
import static nytaxi.common.Constants.DEFAULT_SCOPE;
import static nytaxi.common.Constants.DEFAULT_STREAM;

@Data
public abstract class AbstractHandler {

    private final String scope;
    private final String stream;
    private final String controllerUri;
    private final boolean create;
    private final int limit;

    public AbstractHandler() {
        this.scope = DEFAULT_SCOPE;
        this.stream = DEFAULT_STREAM;
        this.controllerUri = DEFAULT_CONTROLLER_URI;
        this.create = CREATE_STREAM;
        this.limit = DEFAULT_POPULAR_DEST_THRESHOLD;
    }

    public PravegaConfig getPravegaConfig() {
        return  PravegaConfig.fromDefaults()
                .withControllerURI(URI.create(controllerUri))
                .withDefaultScope(scope);
    }

    public void createStream() {
        Stream taxiStream = Stream.of(getScope(), getStream());
        ClientConfig clientConfig = ClientConfig.builder().controllerURI(URI.create(getControllerUri())).build();

        StreamConfiguration streamConfiguration = StreamConfiguration.builder()
                .scalingPolicy(ScalingPolicy.fixed(DEFAULT_NO_SEGMENTS))
                .build();

        Helper helper = new Helper();
        helper.createStream(taxiStream, clientConfig, streamConfiguration);
    }

    public StreamExecutionEnvironment getStreamExecutionEnvironment() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        return env;
    }

    public abstract void handleRequest();
}
