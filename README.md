# Pravega-Flink-SQL-Examples
Demo using Pravega and Flink to analyze data(NY Taxi Records)

## Prerequsites
- Docker and docker compose installed.

## The dataset
The dataset we are using is from [NY Taxi Records](http://www.nyc.gov/html/tlc/html/about/trip_record_data.shtml).
The follwing files are downloaded and used for this sample application:
```
https://s3.amazonaws.com/nyc-tlc/trip+data/yellow_tripdata_2018-01.csv
https://s3.amazonaws.com/nyc-tlc/misc/taxi+_zone_lookup.csv
```

## Component
- Pravega
- Flink
- Zeppelin

## The demo

### Preparation
- `git clone https://github.com/fyang86/pravega-flink-sql-examples.git`
- `docker-compose up -d`
- Open `localhost:18080` in your browser, enter the Zeppelin UI.
- Open Flink interpreter settings and set `FLINK_HOME` to `/opt/flink-1.13.1`
- Import the note `flink_demo_notebook.zpln`
- Run `%flink.ssql show tables;` and check Flink UI: `localhost:18081`
- Run `mvn install` to install datagen's main artifact and then submit the new job `datagen-0.1-jar-with-dependencies.jar` on Flink Web UI to loads the taxi dataset records to Pravega.

### PopularTaxiVendor

Find the most popular taxi vendor that was used by the commuters.
```sql
CREATE TABLE TaxiRide1 (
    rideId INT,
    vendorId INT,
    pickupTime TIMESTAMP(3),
    dropOffTime TIMESTAMP(3),
    passengerCount INT,
    tripDistance FLOAT,
    startLocationId INT,
    destLocationId INT,
    startLocationBorough STRING,
    startLocationZone STRING,
    startLocationServiceZone STRING,
    destLocationBorough STRING,
    destLocationZone STRING,
    destLocationServiceZone STRING,
    WATERMARK FOR pickupTime AS pickupTime - INTERVAL '30' SECONDS
) with (
    'connector' = 'pravega',
    'controller-uri' = 'tcp://pravega:9090',
    'scope' = 'taxi',
    'scan.execution.type' = 'streaming',
    'scan.reader-group.name' = 'popular-vendor',
    'scan.streams' = 'trip',
    'format' = 'json'
);
```

```sql
SELECT 
    vendorId,
    HOP_START (pickupTime, INTERVAL '5' MINUTE, INTERVAL '15' MINUTE) as window_start,
    HOP_END (pickupTime, INTERVAL '5' MINUTE, INTERVAL '15' MINUTE) as window_end,
    count(vendorId) as cnt
FROM
    (SELECT vendorId, pickupTime FROM TaxiRide1)
GROUP BY vendorId, HOP (pickupTime, INTERVAL '5' MINUTE, INTERVAL '15' MINUTE);
```

### PopularDestination

Find the most popular destination (drop-off location) from the available trip records.
```sql
CREATE TABLE TaxiRide2 (
    rideId INT,
    vendorId INT,
    pickupTime TIMESTAMP(3),
    dropOffTime TIMESTAMP(3),
    passengerCount INT,
    tripDistance FLOAT,
    startLocationId INT,
    destLocationId INT,
    startLocationBorough STRING,
    startLocationZone STRING,
    startLocationServiceZone STRING,
    destLocationBorough STRING,
    destLocationZone STRING,
    destLocationServiceZone STRING,
    WATERMARK FOR pickupTime AS pickupTime - INTERVAL '30' SECONDS
) with (
    'connector' = 'pravega',
    'controller-uri' = 'tcp://pravega:9090',
    'scope' = 'taxi',
    'scan.execution.type' = 'streaming',
    'scan.reader-group.name' = 'popular-dest',
    'scan.streams' = 'trip',
    'format' = 'json'
);
```

```sql
SELECT
    destLocationId, window_start, window_end, cnt
FROM
    (SELECT
        destLocationId,
        HOP_START(pickupTime, INTERVAL '5' MINUTE, INTERVAL '15' MINUTE) AS window_start,
        HOP_END(pickupTime, INTERVAL '5' MINUTE, INTERVAL '15' MINUTE) AS window_end,
        COUNT(destLocationId) AS cnt
    FROM
        (SELECT pickupTime, destLocationId FROM TaxiRide2)
    GROUP BY destLocationId, HOP(pickupTime, INTERVAL '5' MINUTE, INTERVAL '15' MINUTE))
WHERE cnt > 20;
```

### MaxTravellersPerDestination

Group the maximum number of travellers with respect to the destination/drop-off location.
```sql
CREATE TABLE TaxiRide3 (
    rideId INT,
    vendorId INT,
    pickupTime TIMESTAMP(3),
    dropOffTime TIMESTAMP(3),
    passengerCount INT,
    tripDistance FLOAT,
    startLocationId INT,
    destLocationId INT,
    startLocationBorough STRING,
    startLocationZone STRING,
    startLocationServiceZone STRING,
    destLocationBorough STRING,
    destLocationZone STRING,
    destLocationServiceZone STRING,
    WATERMARK FOR dropOffTime AS dropOffTime - INTERVAL '30' SECONDS
) with (
    'connector' = 'pravega',
    'controller-uri' = 'tcp://pravega:9090',
    'scope' = 'taxi',
    'scan.execution.type' = 'streaming',
    'scan.reader-group.name' = 'max-traveller',
    'scan.streams' = 'trip',
    'format' = 'json'
);
```

```sql
SELECT 
    destLocationZone,
    TUMBLE_START (dropOffTime, INTERVAL '1' HOUR) as window_start,
    TUMBLE_END (dropOffTime, INTERVAL '1' HOUR) as window_end,
    count(passengerCount) as cnt
FROM
    (SELECT passengerCount, dropOffTime, destLocationZone FROM TaxiRide3)
GROUP BY destLocationZone, TUMBLE (dropOffTime, INTERVAL '1' HOUR);
```