package com.pheeeew.activity.infra;

import com.pheeeew.auth.infra.jwt.AccessTokenClaims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class DeviceActivityFilter extends OncePerRequestFilter {

    private static final Set<String> ACTIVITY_METHODS = Set.of("GET", "POST", "DELETE");

    private final DeviceActivityRecorder recorder;
    private final Clock clock;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean activityPath = path.equals("/api/v1/sighs")
                || path.equals("/api/v2/sighs") || path.startsWith("/api/v2/sighs/")
                || path.equals("/api/v2/reports") || path.startsWith("/api/v2/blocks/");
        return !ACTIVITY_METHODS.contains(request.getMethod()) || !activityPath;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        // 인증 필터 뒤에서 식별자와 요청 시각만 복사하고, 요청 객체와 인증 정보는 비동기 작업에 넘기지 않는다.
        UUID devicePublicId = AccessTokenClaims.from(jwt.getToken()).devicePublicId();
        Instant occurredAt = clock.instant();
        filterChain.doFilter(request, response);

        if (response.getStatus() >= 200 && response.getStatus() < 300) {
            recorder.record(devicePublicId, occurredAt);
        }
    }
}
