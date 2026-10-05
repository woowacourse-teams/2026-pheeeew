package com.pheeeew.common.logging;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.springframework.util.MultiValueMap;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.UriComponentsBuilder;

public final class RequestLogValues {

    private static final Set<String> ALLOWED_NAMES = Set.of(
            "emotionId", "groupId", "blockId", "emojiType", "state", "weeksAgo", "platform"
    );
    private static final Pattern VALUE_SHAPE = Pattern.compile("^[A-Za-z0-9_-]{1,36}$");

    private RequestLogValues() {
    }

    public static Map<String, String> from(HttpServletRequest request) {
        Map<String, String> values = new TreeMap<>();
        if (request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) instanceof Map<?, ?> pathVariables) {
            pathVariables.forEach((name, value) -> putAllowedValue(values, name, value));
        }
        MultiValueMap<String, String> queryValues = queryStringValues(request);
        for (String name : ALLOWED_NAMES) {
            putAllowedValue(values, name, queryValues.getFirst(name));
        }
        return values;
    }

    private static void putAllowedValue(Map<String, String> values, Object name, Object value) {
        if (name instanceof String allowedName && ALLOWED_NAMES.contains(allowedName)
                && value instanceof String text && VALUE_SHAPE.matcher(text).matches()) {
            values.putIfAbsent(allowedName, text);
        }
    }

    private static MultiValueMap<String, String> queryStringValues(HttpServletRequest request) {
        return UriComponentsBuilder.newInstance()
                .replaceQuery(request.getQueryString())
                .build()
                .getQueryParams();
    }
}
