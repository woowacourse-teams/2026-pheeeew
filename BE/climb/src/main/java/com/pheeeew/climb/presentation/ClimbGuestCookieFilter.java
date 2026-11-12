package com.pheeeew.climb.presentation;

import com.pheeeew.climb.application.ClimbGuestService;
import com.pheeeew.climb.domain.ClimbGuest;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** 손님 쿠키를 확인해 인증으로 올리고, 확인될 때마다 쿠키 수명을 다시 180일로 늘린다. */
public class ClimbGuestCookieFilter extends OncePerRequestFilter {

    public static final String COOKIE_NAME = "climb_guest";
    public static final String COOKIE_PATH = "/api/v1/climb";

    private final ClimbGuestService guests;

    public ClimbGuestCookieFilter(ClimbGuestService guests) {
        this.guests = guests;
    }

    public static ResponseCookie cookie(String token) {
        return ResponseCookie.from(COOKIE_NAME, token)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path(COOKIE_PATH)
                .maxAge(ClimbGuestService.COOKIE_MAX_AGE)
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<String> token = Optional.ofNullable(request.getCookies()).stream()
                .flatMap(Arrays::stream)
                .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst();
        Optional<ClimbGuest> guest = token.flatMap(guests::authenticate);
        if (guest.isPresent()) {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    guest.get(), null, AuthorityUtils.NO_AUTHORITIES));
            SecurityContextHolder.setContext(context);
            response.addHeader(HttpHeaders.SET_COOKIE, cookie(token.get()).toString());
        }
        chain.doFilter(request, response);
    }
}
