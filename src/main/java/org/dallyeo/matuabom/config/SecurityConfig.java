package org.dallyeo.matuabom.config;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.filter.JwtAuthFilter;
import org.dallyeo.matuabom.handler.JwtLoginSuccessHandler;
import org.dallyeo.matuabom.service.KakaoOAuth2UserService;
import org.dallyeo.matuabom.util.JwtUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
// import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
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
    // private final JwtFilter jwtFilter;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:3000")); //수정 가능
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(c -> c.configurationSource(corsConfigurationSource())) // ✅ 이 줄 추가
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/swagger", "/swagger-ui.html", "/swagger-ui/**", "/api-docs", "/api-docs/**", "/v3/api-docs/**"
                        ).permitAll()
                        .requestMatchers("/api/auth/login", "/api/auth/signin", "/api/auth/reissue").permitAll()
                        .requestMatchers("/oauth2/**", "/login/oauth2/**", "/oauth2/authorization/kakao").permitAll()
                        .requestMatchers(
                                "/", "/public/**", "/error", "/favicon.ico",
                                "/*.png", "/*.gif", "/*.svg", "/*.jpg", "/*.html", "/*.css", "/*.js"
                        )
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").permitAll()
                        .requestMatchers(HttpMethod.GET, "/kakao/friends").authenticated()
                        .anyRequest()
                        .authenticated()
                )

                // OAuth2 로그인
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(u -> u.userService(kakaoOAuth2UserService))
                        .successHandler(jwtLoginSuccessHandler) // 성공 시 JWT 쿠키/헤더 세팅
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .deleteCookies("ACCESS_TOKEN")
                        .logoutSuccessUrl("/")
                )

                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

                .build();
    }
}
