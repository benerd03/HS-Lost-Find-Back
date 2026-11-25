package kr.ac.hanseo.lostfound.common.config;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import kr.ac.hanseo.lostfound.common.time.KstTime;

@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.system(KstTime.ZONE);
    }

    /** JSON의 LocalDateTime: 응답은 +09:00 붙여서, 요청은 세 형식 모두 허용 */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer kstDateTimeCustomizer() {
        return builder -> builder
                .serializerByType(LocalDateTime.class, new JsonSerializer<LocalDateTime>() {
                    @Override
                    public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers)
                            throws IOException {
                        gen.writeString(KstTime.format(value));
                    }
                })
                .deserializerByType(LocalDateTime.class, new JsonDeserializer<LocalDateTime>() {
                    @Override
                    public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                        String text = p.getValueAsString();
                        if (text == null || text.isBlank()) {
                            return null;
                        }
                        try {
                            return KstTime.parse(text);
                        } catch (IllegalArgumentException e) {
                            throw InvalidFormatException.from(p, e.getMessage(), text, LocalDateTime.class);
                        }
                    }
                });
    }
}
