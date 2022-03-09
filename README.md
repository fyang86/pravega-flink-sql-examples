# Pravega-Flink-SQL-Sample
Sample Program using Pravega and Flink to analyze data(NY Taxi Records), inspired by [Pravega Samples](https://github.com/pravega/pravega-samples) 
and @wuchong's excellent work [flink-sql-training](http://wuchong.me/blog/2019/08/20/flink-sql-training/)

本文假定您已具备基础的 SQL 知识。

## Demo 组件
- Pravega
- Flink
- Zeppelin

本sample 程序是基于 Docker 进行的，因此你只需要安装了 Docker 即可。不需要依赖 Java、Scala 环境、或是IDE。
> **_注意:_** 使用 Docker 启动上述组件，请保证 Docker 内存大于 4G (推荐 6G)。

## 数据集
本sample所使用数据来自 [NY Taxi Records](http://www.nyc.gov/html/tlc/html/about/trip_record_data.shtml).
可以从以下链接下载:
```
https://s3.amazonaws.com/nyc-tlc/trip+data/yellow_tripdata_2018-01.csv
https://s3.amazonaws.com/nyc-tlc/misc/taxi+_zone_lookup.csv
```

## 准备工作
- `git clone https://github.com/fyang86/pravega-flink-sql-examples.git`
- `docker-compose up -d`以启动docker-compose环境
- 打开`localhost:18080`, 进入Zeppelin界面
- 点击右上角`Interpreter`进入Interpreter设置
- 搜索`flink`, 配置`FLINK_HOME`为`/opt/flink-1.13.1`并保存
- 回到Zeppelin首页, Import the note `flink_sql_demo_notebook`
- 执行`%flink.ssql show tables;`, 查看 Flink UI: `localhost:18081`是否工作
- 进入`datagen`目录并执行`mvn install`，在Flink UI中提交之前所生成的jar包文件`datagen-0.1-jar-with-dependencies.jar`, 将数据注入Pravega

### 实例1： 过滤

查询里程大于60mile的行程：
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
    destLocationServiceZone STRING
) with (
    'connector' = 'pravega',
    'controller-uri' = 'tcp://pravega:9090',
    'scope' = 'taxi',
    'scan.execution.type' = 'streaming',
    'scan.reader-group.name' = 'long-distance',
    'scan.streams' = 'trip',
    'format' = 'json'
);

SELECT * FROM TaxiRide1 WHERE tripDistance > 60;
```

### 实例2：Group Aggregate

查询每种乘客数量的行车事件数：
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
    destLocationServiceZone STRING
) with (
    'connector' = 'pravega',
    'controller-uri' = 'tcp://pravega:9090',
    'scope' = 'taxi',
    'scan.execution.type' = 'streaming',
    'scan.reader-group.name' = 'passenger-count',
    'scan.streams' = 'trip',
    'format' = 'json'
);

SELECT passengerCount, COUNT(*) AS cnt
FROM TaxiRide2
GROUP BY passengerCount;
```

### 实例3: Window Aggregate(滚动窗口)

查询指定窗口时间内前往每个目的地的乘客数：
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

SELECT
    destLocationZone,
    TUMBLE_START (dropOffTime, INTERVAL '1' HOUR) as window_start,
    TUMBLE_END (dropOffTime, INTERVAL '1' HOUR) as window_end,
    count(passengerCount) as cnt
FROM
        (SELECT passengerCount, dropOffTime, destLocationZone FROM TaxiRide3)
GROUP BY destLocationZone, TUMBLE (dropOffTime, INTERVAL '1' HOUR);
```

### 实例4：Window Aggregate(滑动窗口)

查询指定窗口时间内最受欢迎的出租车供应商：
```sql
CREATE TABLE TaxiRide4 (
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

SELECT
    vendorId,
    HOP_START (pickupTime, INTERVAL '5' MINUTE, INTERVAL '15' MINUTE) as window_start,
    HOP_END (pickupTime, INTERVAL '5' MINUTE, INTERVAL '15' MINUTE) as window_end,
    count(vendorId) as cnt
FROM
        (SELECT vendorId, pickupTime FROM TaxiRide4)
GROUP BY vendorId, HOP (pickupTime, INTERVAL '5' MINUTE, INTERVAL '15' MINUTE);
```

### 实例5： Append流写入Pravega

将查询指定窗口时间内最热门的目的地结果写入Pravega:
```sql
CREATE TABLE TaxiRide5 (
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

CREATE TABLE PopularDest (
    destLocationId INT,
    window_start TIMESTAMP(3),
    window_end TIMESTAMP(3),
    cnt INT
) with (
    'connector' = 'pravega',
    'controller-uri' = 'tcp://pravega:9090',
    'scope' = 'taxi',
    'sink.stream' = 'popDest',
    'format' = 'json'
);

INSERT INTO PopularDest
SELECT
    destLocationId, window_start, window_end, cnt
FROM
    (SELECT
         destLocationId,
         HOP_START(pickupTime, INTERVAL '5' MINUTE, INTERVAL '15' MINUTE) AS window_start,
         HOP_END(pickupTime, INTERVAL '5' MINUTE, INTERVAL '15' MINUTE) AS window_end,
         COUNT(destLocationId) AS cnt
     FROM
             (SELECT pickupTime, destLocationId FROM TaxiRide5)
     GROUP BY destLocationId, HOP(pickupTime, INTERVAL '5' MINUTE, INTERVAL '15' MINUTE))
WHERE cnt > 8;
```