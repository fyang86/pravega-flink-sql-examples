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

import lombok.extern.slf4j.Slf4j;
import nytaxi.common.Constants;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

@Slf4j
public class ApplicationMain {

    public static void main(String ... args) {

        /**
         * Parameters
         *     -input Input dataset file path, default:/opt/datagen/user_behavior.log
         *     -speedup Data generating speed, default:1000
         *     -controlleruri Pravega controller uri, default:tcp://pravega:9090
         *     -schemaregistryuri Schema registry service uri, default:http://schemaregistry:9092
         */

        // read parameters
        Options options = getOptions();
        CommandLine cmd = null;
        try {
            cmd = parseCommandLineArgs(options, args);
        } catch (ParseException e) {
            log.error("%s.%n", e.getMessage());
            final HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("Datagen", options);
            System.exit(1);
        }

        final String controllerUri = cmd.getOptionValue("controlleruri") == null ?
                Constants.DEFAULT_CONTROLLER_URI : cmd.getOptionValue("controlleruri");

        AbstractHandler handler = new PrepareMain(controllerUri);

        handler.handleRequest();
    }

    private static Options getOptions() {
        final Options options = new Options();
        options.addOption("controlleruri", true, "The Pravega controller uri");
        return options;
    }

    private static CommandLine parseCommandLineArgs(Options options, String[] args) throws ParseException {
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);
        return cmd;
    }
}
