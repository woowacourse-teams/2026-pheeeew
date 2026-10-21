package com.pheeeew.common.logging;

import com.pheeeew.emotion.infra.metrics.MapReadMetrics;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.util.MultiValueMap;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.UriComponentsBuilder;

public final class PheeeewRequestLogPolicy implements RequestLogPolicy {

    private static final Set<String> ALLOWED_NAMES = Set.of(
            "emotionId", "groupId", "blockId", "emojiType", "state", "weeksAgo", "platform"
    );
    private static final Pattern VALUE_SHAPE = Pattern.compile("^[A-Za-z0-9_-]{1,36}$");
    private static final List<RequestTimingStage> TIMING_STAGES = Stream.of(
            Arrays.stream(DataSourceTimingStage.values()), Arrays.stream(PheeeewRequestTimingStage.values()),
            Arrays.stream(MapReadMetrics.Stage.values())).flatMap(stream -> stream.map(stage -> (RequestTimingStage) stage)).toList();

    @Override
    public Map<String, String> requestValues(HttpServletRequest request) {
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

    @Override
    public List<RequestTimingStage> timingStages() {
        return TIMING_STAGES;
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
