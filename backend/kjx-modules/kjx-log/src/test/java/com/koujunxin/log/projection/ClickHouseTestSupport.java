package com.koujunxin.log.projection;

import java.time.Duration;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.ObjectMapper;

final class ClickHouseTestSupport {

    private ClickHouseTestSupport() {
    }

    static GenericContainer<?> container() {
        GenericContainer<?> clickhouse = new GenericContainer<>(DockerImageName.parse("clickhouse/clickhouse-server:24.8"))
                .withExposedPorts(8123)
                .withEnv("CLICKHOUSE_USER", "kjx")
                .withEnv("CLICKHOUSE_PASSWORD", "kjx")
                .withEnv("CLICKHOUSE_DB", "kjx")
                .withEnv("CLICKHOUSE_DEFAULT_ACCESS_MANAGEMENT", "1")
                .waitingFor(Wait.forHttp("/ping").forPort(8123).forStatusCode(200).withStartupTimeout(Duration.ofMinutes(2)));
        clickhouse.start();
        Runtime.getRuntime().addShutdownHook(new Thread(clickhouse::stop));
        return clickhouse;
    }

    static ClickHouseHttpClient client(GenericContainer<?> clickhouse) {
        String url = "http://" + clickhouse.getHost() + ":" + clickhouse.getMappedPort(8123);
        return new ClickHouseHttpClient(url, "kjx", "kjx", Duration.ofSeconds(5));
    }

    static ClickHouseProjectionSink sink(GenericContainer<?> clickhouse, ObjectMapper mapper) {
        ClickHouseHttpClient client = client(clickhouse);
        client.execute("CREATE DATABASE IF NOT EXISTS kjx");
        client.applyClasspathSchema("clickhouse/schema.sql");
        return new ClickHouseProjectionSink(client, mapper);
    }

    static ClickHouseProjectionSink unreachableSink(ObjectMapper mapper) {
        ClickHouseHttpClient client = new ClickHouseHttpClient(
                "http://127.0.0.1:1", "default", "", Duration.ofMillis(400));
        return new ClickHouseProjectionSink(client, mapper);
    }
}
