package com.pheeeew.climb.stairs;

import com.pheeeew.climb.domain.ClimbNickname;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class StairsService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Set<String> CHARACTERS = Set.of("defeated", "tired", "frustrated", "annoyed", "angry");
    // 판마다 받은 계단 표는 이 시간 안에만 등록할 수 있다(한 판 최대 10분 + 결과 화면 여유).
    static final long TICKET_SECONDS = 20 * 60;
    private final StairsRepository repository;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    // 서버가 켜질 때마다 새로 만드는 서명 키. 재시작하면 그 전에 받은 표는 등록되지 않는다.
    private final byte[] key = new byte[32];

    public StairsService(StairsRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
        random.nextBytes(key);
    }

    /** 판마다 새 무작위 계단(seed)과, 서버가 낸 계단임을 증명하는 표(ticket). */
    public Today today() {
        LocalDate day = LocalDate.now(clock.withZone(SEOUL));
        int seed = random.nextInt() & 0x7fffffff;
        long issued = clock.instant().getEpochSecond();
        return new Today(day, seed, issued + "." + sign(day, seed, issued));
    }

    public Result submit(String nickname, LocalDate day, String character, int seed, String ticket,
            List<StairsRules.Input> inputs) {
        nickname = ClimbNickname.validate(nickname, 1);
        if (character == null || !CHARACTERS.contains(character)) {
            throw new IllegalArgumentException("INVALID_REQUEST");
        }
        requireDay(day);
        requireTicket(day, seed, ticket);
        StairsRules.Score score = StairsRules.replay(seed, inputs);
        repository.save(nickname, day, character, score);
        StairsRepository.Item best = repository.best(day, nickname);
        return new Result(score.height(), score.timeMs(), new StairsRules.Score(best.height(), best.timeMs()), best.rank());
    }

    public StairsRepository.Board ranking(LocalDate day) {
        if (day == null) throw new IllegalArgumentException("INVALID_DAY");
        return repository.ranking(day);
    }

    public void requireDay(LocalDate day) {
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(SEOUL));
        LocalDate today = now.toLocalDate();
        boolean grace = today.minusDays(1).equals(day) && now.toLocalTime().toSecondOfDay() < 300;
        if (!today.equals(day) && !grace) throw new IllegalArgumentException("INVALID_DAY");
    }

    void requireTicket(LocalDate day, int seed, String ticket) {
        int dot = ticket == null ? -1 : ticket.indexOf('.');
        if (dot <= 0) throw new IllegalArgumentException("INVALID_TICKET");
        long issued;
        try { issued = Long.parseLong(ticket.substring(0, dot)); } catch (NumberFormatException e) {
            throw new IllegalArgumentException("INVALID_TICKET");
        }
        long age = clock.instant().getEpochSecond() - issued;
        byte[] expected = sign(day, seed, issued).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = ticket.substring(dot + 1).getBytes(StandardCharsets.US_ASCII);
        if (age < -60 || age > TICKET_SECONDS || !MessageDigest.isEqual(expected, actual)) {
            throw new IllegalArgumentException("INVALID_TICKET");
        }
    }

    private String sign(LocalDate day, int seed, long issued) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] digest = mac.doFinal((day + "|" + seed + "|" + issued).getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public record Today(LocalDate day, int seed, String ticket) { }
    public record Result(int height, int timeMs, StairsRules.Score best, int rank) { }
}
