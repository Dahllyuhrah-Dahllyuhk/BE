package org.dallyeo.matuabom.global.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP 기반 Rate Limiting 필터.
 *
 * 엔드포인트별 제한:
 *   - POST /api/friends/addFriend : 10회/분 (초대코드 브루트포스 방지)
 *   - POST /oauth2/authorization/* : 20회/분 (로그인 어뷰징 방지)
 *   - POST /api/calendar/sync      : 10회/분 (무한 동기화 방지)
 *   - 그 외 /api/**                : 200회/분 (일반 API)
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    // IP + 버킷 타입별 캐시
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        // Rate Limit 대상 경로만 처리
        if (!isRateLimited(method, path)) {
            chain.doFilter(request, response);
            return;
        }

        String ip = getClientIp(request);
        String bucketType = resolveBucketType(method, path);
        String key = ip + ":" + bucketType;

        Bucket bucket = buckets.computeIfAbsent(key, k -> createBucket(bucketType));

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"too_many_requests\",\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해주세요.\"}");
        }
    }

    private boolean isRateLimited(String method, String path) {
        if (path.startsWith("/api/") || path.startsWith("/oauth2/")) return true;
        return false;
    }

    private String resolveBucketType(String method, String path) {
        if ("POST".equals(method) && path.equals("/api/friends/addFriend")) return "invite";
        if (path.startsWith("/oauth2/authorization/"))                       return "login";
        if ("POST".equals(method) && path.equals("/api/calendar/sync"))      return "sync";
        return "default";
    }

    private Bucket createBucket(String type) {
        Bandwidth limit = switch (type) {
            case "invite" -> Bandwidth.builder().capacity(10).refillGreedy(10, Duration.ofMinutes(1)).build();
            case "login"  -> Bandwidth.builder().capacity(20).refillGreedy(20, Duration.ofMinutes(1)).build();
            case "sync"   -> Bandwidth.builder().capacity(10).refillGreedy(10, Duration.ofMinutes(1)).build();
            default       -> Bandwidth.builder().capacity(200).refillGreedy(200, Duration.ofMinutes(1)).build();
        };
        return Bucket.builder().addLimit(limit).build();
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        return request.getRemoteAddr();
    }
}
