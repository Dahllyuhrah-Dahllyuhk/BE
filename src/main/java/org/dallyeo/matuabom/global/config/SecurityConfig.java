package org.dallyeo.matuabom.global.config;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.auth.filter.JwtAuthFilter;
import org.dallyeo.matuabom.auth.handler.JwtLoginSuccessHandler;
import org.dallyeo.matuabom.auth.service.KakaoOAuth2UserService;
import org.dallyeo.matuabom.global.filter.RateLimitFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

@RequiredArgsConstructor
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.frontend-base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    private final KakaoOAuth2UserService kakaoOAuth2UserService;
    private final JwtLoginSuccessHandler jwtLoginSuccessHandler;
    private final JwtAuthFilter jwtAuthFilter;
    private final RateLimitFilter rateLimitFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/swagger", "/swagger-ui.html", "/swagger-ui/**",
                                "/api-docs", "/api-docs/**", "/v3/api-docs/**",  // prod에서는 springdoc.swagger-ui.enabled=false로 비활성화
                                "/", "/error", "/favicon.ico",
                                "/*.png", "/*.gif", "/*.svg", "/*.jpg", "/*.html", "/*.css", "/*.js",
                                "/actuator/health"   // 헬스체크 (CI/CD 배포 검증용)
                        ).permitAll()
                        .requestMatchers("/oauth2/**", "/login/**").permitAll()
                        .requestMatchers("/api/google/webhook").permitAll()
                        .requestMatchers("/api/auth/logout").permitAll()
                        .requestMatchers("/api/auth/token").permitAll()
                        .requestMatchers("/api/admin/**").permitAll()
                        .requestMatchers("/api/chat/**").denyAll()  // AI 기능 비활성화
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                )

                .exceptionHandling(ex -> ex.authenticationEntryPoint((req, res, e) -> {
                    res.setStatus(401);
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"error\":\"unauthorized\"}");
                }))

                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo.userService(oauth2UserRequest -> {
                            String registrationId = oauth2UserRequest.getClientRegistration().getRegistrationId();
                            if ("kakao".equalsIgnoreCase(registrationId)) {
                                return kakaoOAuth2UserService.loadUser(oauth2UserRequest);
                            }
                            return new DefaultOAuth2UserService().loadUser(oauth2UserRequest);
                        }))
                        .successHandler(jwtLoginSuccessHandler)
                )
                .oauth2Client(Customizer.withDefaults())
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(jwtAuthFilter, OAuth2LoginAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOrigins(List.of("http://localhost:3000", frontendBaseUrl));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "Accept", "X-Admin-Secret"));
        config.setExposedHeaders(List.of("Set-Cookie", "X-New-Access-Token"));

        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", config);
        return src;
    }
}