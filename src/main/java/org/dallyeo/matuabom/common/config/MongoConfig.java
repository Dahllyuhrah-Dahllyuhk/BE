package org.dallyeo.matuabom.common.config;

import org.dallyeo.matuabom.common.crypto.EncryptedTokenReadConverter;
import org.dallyeo.matuabom.common.crypto.EncryptedTokenWriteConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.util.List;

@Configuration
public class MongoConfig {

    @Bean
    public MongoCustomConversions mongoCustomConversions(
            EncryptedTokenReadConverter readConverter,
            EncryptedTokenWriteConverter writeConverter) {
        return new MongoCustomConversions(List.of(readConverter, writeConverter));
    }
}
