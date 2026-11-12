package com.pheeeew.climb;

import com.pheeeew.climb.domain.ClimbMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * pheeeew.climb.* 설정.
 *
 * @param allowedOrigin 게임 주소 하나. CORS 와 웹소켓 핸드셰이크가 이 출처만 허용한다.
 * @param maps          맵 버전별 꼭대기 높이, 구간 경계, 완주 최소 시간. 여기 없는 맵 버전은 순위에 넣지 않는다.
 */
@ConfigurationProperties("pheeeew.climb")
public record ClimbProperties(
        boolean enabled,
        String allowedOrigin,
        @DefaultValue("10") int guestCreatesPerHour,
        Map<String, MapProperties> maps
) {

    public ClimbProperties {
        maps = maps == null ? Map.of() : Map.copyOf(maps);
    }

    public Map<String, ClimbMap> climbMaps() {
        return maps.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                entry -> ClimbMap.of(entry.getKey(), entry.getValue().top(), entry.getValue().zones(),
                        entry.getValue().minFinishSeconds())));
    }

    public record MapProperties(int top, List<Integer> zones, int minFinishSeconds) {
    }
}
