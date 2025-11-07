package org.dallyeo.matuabom.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
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
            // ✅ CORS 필수 (프리플라이트 포함)
            .cors(Customizer.withDefaults())
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)

            // ✅ OAuth2 로그인 동안 세션 사용
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

            .authorizeHttpRequests(auth -> auth
                // ✅ 프리플라이트는 무조건 허용
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                .requestMatchers(
                    "/swagger", "/swagger-ui.html", "/swagger-ui/**",
                    "/api-docs", "/api-docs/**", "/v3/api-docs/**",
                    "/", "/error", "/favicon.ico",
                    "/*.png", "/*.gif", "/*.svg", "/*.jpg", "/*.html", "/*.css", "/*.js"
                ).permitAll()

                // ✅ OAuth2 엔드포인트 허용
                .requestMatchers("/oauth2/**", "/login/**").permitAll()

                // 보호 대상
                .requestMatchers("/api/calendar/**").authenticated()
                .anyRequest().authenticated()
            )

            // ✅ AJAX(API) 요청은 401로, 로그인 리다이렉트(302) 금지
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> {
                    // /api/로 시작하면 401로 돌려서 브라우저 fetch가 제대로 처리하게 함
                    String uri = req.getRequestURI();
                    if (uri != null && uri.startsWith("/api/")) {
                        res.setStatus(401);
                        res.setContentType("application/json;charset=UTF-8");
                        res.getWriter().write("{\"error\":\"unauthorized\"}");
                    } else {
                        // 그 외엔 기존 동작 유지(리다이렉트)
                        res.sendRedirect("/oauth2/authorization/google");
                    }
                })
            )

            // ✅ OAuth2 로그인 완료 후 FE로
            .oauth2Login(oauth -> oauth
                .defaultSuccessUrl("http://localhost:3000", true)
                .failureUrl("/login?error=true")
            )

            // ✅ @RegisteredOAuth2AuthorizedClient 주입을 위해 필요
            .oauth2Client(Customizer.withDefaults())

            .build();
    }
}
