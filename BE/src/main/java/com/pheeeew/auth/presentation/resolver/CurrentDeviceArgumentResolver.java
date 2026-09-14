package com.pheeeew.auth.presentation.resolver;

import com.pheeeew.auth.infra.jwt.AccessTokenClaims;
import com.pheeeew.auth.presentation.annotation.CurrentDevice;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
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
                && (UUID.class.equals(parameter.getParameterType()) || isOptionalUuid(parameter));
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer modelAndViewContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        Optional<UUID> devicePublicId = findAuthenticatedDevice()
                .map(this::toDevicePublicId);

        if (isOptionalUuid(parameter)) {
            return devicePublicId;
        }

        return devicePublicId.orElseThrow(() -> new IllegalStateException(
                "인증된 기기 없이 기기 식별자를 요구하는 핸들러에 요청이 도달했습니다."
        ));
    }

    private boolean isOptionalUuid(MethodParameter parameter) {
        return Optional.class.equals(parameter.getParameterType())
                && UUID.class.equals(ResolvableType.forMethodParameter(parameter).getGeneric(0).resolve());
    }

    private Optional<JwtAuthenticationToken> findAuthenticatedDevice() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication && jwtAuthentication.isAuthenticated()) {
            return Optional.of(jwtAuthentication);
        }
        return Optional.empty();
    }

    private UUID toDevicePublicId(JwtAuthenticationToken authentication) {
        return AccessTokenClaims.from(authentication.getToken()).devicePublicId();
    }
}
