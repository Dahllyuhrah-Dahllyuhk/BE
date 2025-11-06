package org.dallyeo.matuabom.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@RequiredArgsConstructor
@Profile("!test")
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        return http
            // 기본
            .cors(Customizer.withDefaults())
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)

            // ✅ OAuth2 로그인 과정 동안은 세션이 필요합니다.
            //    (로그인·콜백 시점에만 세션을 사용. 이후 JWT로 가려면 별도 필터 추가)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

            // URL 권한
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/swagger", "/swagger-ui.html", "/swagger-ui/**",
                    "/api-docs", "/api-docs/**", "/v3/api-docs/**",
                    "/", "/error", "/favicon.ico",
                    "/*.png", "/*.gif", "/*.svg", "/*.jpg", "/*.html", "/*.css", "/*.js"
                ).permitAll()

                // ✅ 반드시 허용: OAuth2 엔드포인트들
                .requestMatchers("/oauth2/**", "/login/**").permitAll()

                // 보호할 API
                .requestMatchers("/api/calendar/**").authenticated()

                .anyRequest().authenticated()
            )

            // OAuth2 로그인
            .oauth2Login(oauth -> oauth
                .defaultSuccessUrl("/api/calendar/events", true)
                .failureUrl("/login?error=true")
            )

            .build();
    }
}
