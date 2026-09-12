package com.pheeeew.auth.presentation.resolver;

import com.pheeeew.auth.infra.jwt.AccessTokenClaims;
import com.pheeeew.auth.presentation.annotation.CurrentDevice;
import java.util.UUID;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class CurrentDeviceArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentDevice.class)
                && UUID.class.equals(parameter.getParameterType());
    }

    @Override
    public UUID resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer modelAndViewContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        JwtAuthenticationToken authentication = requireAuthenticatedDevice();
        return AccessTokenClaims.from(authentication.getToken()).devicePublicId();
    }

    private JwtAuthenticationToken requireAuthenticatedDevice() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication && jwtAuthentication.isAuthenticated()) {
            return jwtAuthentication;
        }
        throw new IllegalStateException("인증된 기기 없이 기기 식별자를 요구하는 핸들러에 요청이 도달했습니다.");
    }
}
