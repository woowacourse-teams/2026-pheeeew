package com.pheeeew.climb.application;

import com.pheeeew.climb.domain.ClimbHallEntry;
import com.pheeeew.climb.domain.ClimbMap;
import com.pheeeew.climb.domain.ClimbRankEntry;
import com.pheeeew.climb.domain.ClimbRanking;
import com.pheeeew.climb.infra.ClimbRunRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 맵 버전별 순위표. 서버가 1대라 메모리에 두고, 뜰 때 DB 의 기록으로 채운다.
 * shortcut: 순위에 오를 수 있는 등반을 전부 메모리에 올린다. 기록이 수십만 줄을 넘으면 상위 N 줄만 올리도록 바꾼다.
 */
public class ClimbRankingService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final Map<String, ClimbMap> maps;
    private final ClimbRunRepository runs;
    private final Map<String, Map<UUID, ClimbRankEntry>> records = new ConcurrentHashMap<>();
    private final Map<String, Map<UUID, ClimbRankEntry>> online = new ConcurrentHashMap<>();

    public ClimbRankingService(Map<String, ClimbMap> maps, ClimbRunRepository runs) {
        this.maps = Map.copyOf(maps);
        this.runs = runs;
        maps.keySet().forEach(version -> {
            records.put(version, new ConcurrentHashMap<>());
            online.put(version, new ConcurrentHashMap<>());
        });
    }

    public void load() {
        runs.findRankable(maps.keySet()).forEach(ranked ->
                records.get(ranked.mapVersion()).put(ranked.entry().runId(), ranked.entry()));
    }

    public Optional<ClimbMap> map(String version) {
        return Optional.ofNullable(version).map(maps::get);
    }

    /** 접속 중인 손님의 등반 기록이 바뀌었을 때 부른다. 의심 기록은 모든 순위표에서 숨긴다. */
    public void record(String mapVersion, ClimbRankEntry entry, boolean suspect) {
        Map<UUID, ClimbRankEntry> mapRecords = records.get(mapVersion);
        Map<UUID, ClimbRankEntry> mapOnline = online.get(mapVersion);
        if (mapRecords == null) {
            return;
        }
        if (suspect || entry.bestAt() == null) {
            mapRecords.remove(entry.runId());
        } else {
            mapRecords.put(entry.runId(), entry);
        }
        if (suspect) {
            mapOnline.remove(entry.guestId());
        } else {
            mapOnline.put(entry.guestId(), entry);
        }
    }

    public void leave(String mapVersion, UUID guestId) {
        Map<UUID, ClimbRankEntry> mapOnline = online.get(mapVersion);
        if (mapOnline != null) {
            mapOnline.remove(guestId);
        }
    }

    public void rename(UUID guestId, String nickname) {
        for (Map<String, Map<UUID, ClimbRankEntry>> store : List.of(records, online)) {
            store.values().forEach(entries -> entries.replaceAll((key, entry) ->
                    entry.guestId().equals(guestId) ? entry.withNickname(nickname) : entry));
        }
    }

    public void forget(Collection<UUID> guestIds) {
        records.values().forEach(entries -> entries.values().removeIf(entry -> guestIds.contains(entry.guestId())));
        online.values().forEach(entries -> entries.keySet().removeAll(guestIds));
    }

    public List<ClimbRankEntry> board(String mapVersion, ClimbBoard board, Instant now) {
        Map<UUID, ClimbRankEntry> source = (board == ClimbBoard.ONLINE ? online : records).get(mapVersion);
        if (source == null) {
            return List.of();
        }
        if (board != ClimbBoard.TODAY) {
            return ClimbRanking.sort(source.values());
        }
        LocalDate today = LocalDate.ofInstant(now, KST);
        return ClimbRanking.sort(source.values().stream()
                .filter(entry -> LocalDate.ofInstant(entry.bestAt(), KST).equals(today))
                .toList());
    }

    /** 명예의 전당은 작아서 메모리에 두지 않고 그때그때 DB 에서 읽는다. */
    public List<ClimbHallEntry> hall(String mapVersion, int limit) {
        return runs.findHall(mapVersion, limit);
    }

    public int onlineCount() {
        return online.values().stream().mapToInt(Map::size).sum();
    }
}
