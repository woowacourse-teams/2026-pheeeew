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
 * @param presence      함께 오르기(다른 사람 위치 공유).
 * @param adminKey      관리자 키(환경 변수 PHEEEEW_CLIMB_ADMIN_KEY). 비어 있으면 관리자 기능이 꺼진다.
 */
@ConfigurationProperties("pheeeew.climb")
public record ClimbProperties(
        boolean enabled,
        String allowedOrigin,
        @DefaultValue("10") int guestCreatesPerHour,
        Map<String, MapProperties> maps,
        @DefaultValue Presence presence,
        String adminKey
) {

    public ClimbProperties {
        maps = maps == null ? Map.of() : Map.copyOf(maps);
    }

    public Map<String, ClimbMap> climbMaps() {
        return maps.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                entry -> ClimbMap.of(entry.getKey(), entry.getValue().top(), entry.getValue().zones(),
                        entry.getValue().minFinishSeconds())));
    }

    /**
     * @param enabled 꺼지면 st 를 무시하고 sts 를 보내지 않는다. 순위는 그대로다.
     * @param range   내 높이 위아래 이 칸 수 안의 사람만 보인다.
     * @param limit   한 사람이 받는 최대 인원(가까운 순서).
     */
    public record Presence(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("30") int range,
            @DefaultValue("20") int limit
    ) {
    }

    public record MapProperties(int top, List<Integer> zones, int minFinishSeconds) {
    }
}
