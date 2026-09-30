package com.pheeeew.emotion.presentation;

import com.pheeeew.auth.presentation.AuthenticatedDevices;
import com.pheeeew.emotion.infra.ratelimit.EmotionCreateRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@RequiredArgsConstructor
public class EmotionCreateRateLimitInterceptor implements HandlerInterceptor {

    private final EmotionCreateRateLimiter emotionCreateRateLimiter;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }
        AuthenticatedDevices.findDevicePublicId().ifPresent(emotionCreateRateLimiter::require);
        return true;
    }
}
