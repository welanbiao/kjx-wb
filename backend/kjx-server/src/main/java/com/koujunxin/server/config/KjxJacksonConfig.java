package com.koujunxin.server.config;

import com.fasterxml.jackson.datatype.jsr310.deser.InstantDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.OffsetDateTimeSerializer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.TimeZone;

/**
 * 契约时间：ISO-8601 带 +08:00。覆盖 RuoYi LocalDateTime 无时区格式。
 */
@AutoConfiguration
public class KjxJacksonConfig {

    public static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer kjxJacksonCustomizer() {
        return builder -> {
            builder.timeZone(TimeZone.getTimeZone(SHANGHAI));
            builder.serializerByType(OffsetDateTime.class, OffsetDateTimeSerializer.INSTANCE);
            builder.deserializerByType(OffsetDateTime.class, InstantDeserializer.OFFSET_DATE_TIME);
        };
    }
}
