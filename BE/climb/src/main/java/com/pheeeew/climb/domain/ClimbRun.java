package com.pheeeew.climb.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * 한 번의 등반. 브라우저가 물리를 계산하므로 서버는 보고가 가능한 범위인지만 검사하고, 맞지 않는 보고는 버린다.
 * 높이 검사와 시간 검사의 기준점은 연결마다 attach 로 다시 잡는다.
 */
public class ClimbRun {

    private static final int MAX_RISE_PER_WINDOW = 20;
    private static final long RISE_WINDOW_MILLIS = 2_000;
    private static final long TIME_SLACK_SECONDS = 10;

    private final UUID id;
    private final UUID guestId;
    private final ClimbMap map;
    private final Instant startedAt;
    private String character;
    private int best;
    private Instant bestAt;
    private int height;
    private int x;
    private int falls;
    private int playSeconds;
    private Instant finishedAt;
    private Integer finishSeconds;
    private boolean suspect;
    private Instant updatedAt;
    private int zone;
    private Instant connectedAt;
    private int playSecondsAtConnect;
    private Instant anchorAt;
    private int anchorBest;

    private ClimbRun(ClimbRunRecord record, ClimbMap map) {
        this.id = record.id();
        this.guestId = record.guestId();
        this.map = map;
        this.startedAt = record.startedAt();
        this.character = record.character();
        this.best = record.best();
        this.bestAt = record.bestAt();
        this.height = record.height();
        this.x = record.x();
        this.falls = record.falls();
        this.playSeconds = record.playSeconds();
        this.finishedAt = record.finishedAt();
        this.finishSeconds = record.finishSeconds();
        this.suspect = record.suspect();
        this.updatedAt = record.updatedAt();
        this.zone = map.zonesBelow(record.best());
    }

    public static ClimbRun start(UUID id, UUID guestId, ClimbMap map, String character, Instant now) {
        return new ClimbRun(ClimbRunRecord.of(id, guestId, map.version(), character, 0, null, 0, 0, 0, 0,
                null, null, false, now, now), map);
    }

    public static ClimbRun restore(ClimbRunRecord record, ClimbMap map) {
        return new ClimbRun(record, map);
    }

    public synchronized void attach(Instant now) {
        connectedAt = now;
        playSecondsAtConnect = playSeconds;
        anchorAt = now;
        anchorBest = best;
    }

    public synchronized void changeCharacter(String character) {
        this.character = character;
    }

    public synchronized boolean pos(int h, int reportedBest, int reportedFalls, int time, int reportedX, Instant now) {
        if (finishedAt != null || h < 0 || reportedBest < h || reportedBest > map.top()
                || reportedFalls < 0 || time < 0) {
            return false;
        }
        long elapsed = Math.max(0, Duration.between(anchorAt, now).toMillis());
        long windows = Math.max(1, (elapsed + RISE_WINDOW_MILLIS - 1) / RISE_WINDOW_MILLIS);
        if (reportedBest - anchorBest > MAX_RISE_PER_WINDOW * windows) {
            return false;
        }
        if (elapsed >= RISE_WINDOW_MILLIS) {
            anchorAt = now;
            anchorBest = Math.max(best, reportedBest);
        }
        if (reportedBest > best) {
            best = reportedBest;
            bestAt = now;
        }
        height = h;
        x = reportedX;
        falls = reportedFalls;
        playSeconds = time;
        updatedAt = now;
        return true;
    }

    public synchronized boolean zone(int reportedZone, int time, Instant now) {
        if (finishedAt != null || reportedZone != zone + 1 || reportedZone > map.zones().size() || time < 0) {
            return false;
        }
        zone = reportedZone;
        playSeconds = Math.max(playSeconds, time);
        updatedAt = now;
        return true;
    }

    public synchronized boolean finish(int time, int reportedFalls, Instant now) {
        if (finishedAt != null || zone != map.zones().size() || time < 0 || reportedFalls < 0) {
            return false;
        }
        // 서버가 잰 연결 시간보다 크게 짧은 완주 시간은 믿지 않는다.
        long connected = Duration.between(connectedAt, now).toSeconds();
        if (time < playSecondsAtConnect + connected - TIME_SLACK_SECONDS) {
            return false;
        }
        if (best < map.top()) {
            best = map.top();
            bestAt = now;
        }
        height = map.top();
        falls = reportedFalls;
        playSeconds = time;
        finishedAt = now;
        finishSeconds = time;
        suspect = suspect || time < map.minFinishSeconds();
        updatedAt = now;
        return true;
    }

    public UUID id() {
        return id;
    }

    public UUID guestId() {
        return guestId;
    }

    public ClimbMap map() {
        return map;
    }

    public synchronized boolean suspect() {
        return suspect;
    }

    public synchronized ClimbRankEntry entry(String tag, String nickname) {
        return ClimbRankEntry.of(id, guestId, tag, nickname, character, best, bestAt, finishSeconds);
    }

    public synchronized ClimbRunRecord record() {
        return ClimbRunRecord.of(id, guestId, map.version(), character, best, bestAt, height, x, falls, playSeconds,
                finishedAt, finishSeconds, suspect, startedAt, updatedAt);
    }
}
