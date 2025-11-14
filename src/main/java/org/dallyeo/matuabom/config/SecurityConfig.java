package org.dallyeo.matuabom.config;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.filter.JwtAuthFilter;
import org.dallyeo.matuabom.handler.JwtLoginSuccessHandler;
import org.dallyeo.matuabom.service.KakaoOAuth2UserService;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@RequiredArgsConstructor
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final KakaoOAuth2UserService kakaoOAuth2UserService;
    private final JwtLoginSuccessHandler jwtLoginSuccessHandler;
    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                // ✅ JWT는 stateless지만, OAuth2 로그인은 세션이 필요해서 IF_REQUIRED
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )

                .authorizeHttpRequests(auth -> auth
                        // preflight
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // docs & static
                        .requestMatchers(
                                "/swagger", "/swagger-ui.html", "/swagger-ui/**",
                                "/api-docs", "/api-docs/**", "/v3/api-docs/**",
                                "/", "/error", "/favicon.ico",
                                "/*.png", "/*.gif", "/*.svg", "/*.jpg", "/*.html", "/*.css", "/*.js"
                        ).permitAll()

                        // oauth2 endpoints
                        .requestMatchers("/oauth2/**", "/login/**").permitAll()

                        // protected API
                        .requestMatchers("/api/calendar/**").authenticated()

                        .anyRequest().permitAll()
                )

                // ✅ 인증 안 된 상태에서 /api/**로 들어오면
                //    → 리다이렉트 하지 말고 JSON 401만 응답
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> {
                            res.setStatus(401);
                            res.setContentType("application/json;charset=UTF-8");
                            res.getWriter().write("{\"error\":\"unauthorized\"}");
                        })
                )

                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(oauth2UserRequest -> {
                                    String registrationId = oauth2UserRequest.getClientRegistration().getRegistrationId();

                                    if ("kakao".equalsIgnoreCase(registrationId)) {
                                        return kakaoOAuth2UserService.loadUser(oauth2UserRequest);
                                    }

                                    // 구글은 기본 서비스 사용
                                    return new DefaultOAuth2UserService().loadUser(oauth2UserRequest);
                                })
                        )
                        .successHandler(jwtLoginSuccessHandler)
                )

                .oauth2Client(Customizer.withDefaults())

                .addFilterAfter(jwtAuthFilter, OAuth2LoginAuthenticationFilter.class)

                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOrigins(List.of("http://localhost:3000"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
