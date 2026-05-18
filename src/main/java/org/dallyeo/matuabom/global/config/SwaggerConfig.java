package org.dallyeo.matuabom.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("맞춰봄 API")
                .description("맞춰봄 모임 일정 조율 서비스 API 문서")
                .version("1.0.0")
                .contact(new Contact()
                    .name("문성현")
                    .email("tjgus9139@gmail.com")));
    }
}
