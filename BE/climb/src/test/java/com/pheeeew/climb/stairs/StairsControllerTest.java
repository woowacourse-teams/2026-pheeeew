package com.pheeeew.climb.stairs;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

class StairsControllerTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 13);
    private final TestClock clock = new TestClock();
    private final MemoryRepository repository = new MemoryRepository();
    private final StairsController controller = new StairsController(new StairsService(repository, clock), clock);
    private final JsonMapper json = JsonMapper.builder().build();

    @Test void registersWithoutCookiesAndTrimsOneCharacterNickname() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        var response = mvc.perform(post("/api/v1/climb/stairs/runs").contentType("application/json")
                .content(body("  가  ", 3, 100))).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        var result = json.readTree(response.getContentAsString());
        assertThat(result.path("height").intValue()).isEqualTo(3);
        assertThat(result.path("timeMs").intValue()).isEqualTo(200);
        assertThat(result.path("rank").intValue()).isEqualTo(1);
        assertThat(repository.best(DAY, "가").nickname()).isEqualTo("가");
        assertThat(response.getHeader("Set-Cookie")).isNull();
        var board = mvc.perform(get("/api/v1/climb/stairs/ranking").param("day", DAY.toString()))
                .andReturn().getResponse();
        assertThat(board.getContentAsString()).contains("nickname").doesNotContain("\"me\"", "\"tag\"");
        assertThat(json.readTree(board.getContentAsString()).path("total").longValue()).isEqualTo(1);
    }

    @Test void rejectsInvalidNicknameWithShortMessageAndDoesNotSave() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        for (String nickname : List.of("", " ", "12345678901", "두 단어", "닉!", "😀", "ㄱ")) {
            var response = mvc.perform(post("/api/v1/climb/stairs/runs").contentType("application/json")
                    .content(body(nickname, 2, 100))).andReturn().getResponse();
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains("NICKNAME_");
        }
        assertThat(repository.saves).isZero();
    }

    @Test void sameNicknameKeepsBetterHeightThenTimeAndEqualScoreKeepsFirst() throws Exception {
        controller.submit(request(body("러시", 3, 100), true));
        controller.submit(request(body(" 러시 ", 2, 50), true));
        assertThat(repository.best(DAY, "러시").timeMs()).isEqualTo(200);
        controller.submit(request(body("러시", 3, 90), true));
        assertThat(repository.best(DAY, "러시").timeMs()).isEqualTo(180);
        controller.submit(request(body("러시", 3, 90).replace("angry", "tired"), true));
        assertThat(repository.best(DAY, "러시").character()).isEqualTo("angry");
        controller.submit(request(body("러시", 4, 100), true));
        assertThat(repository.best(DAY, "러시").height()).isEqualTo(4);
        assertThat(repository.saves).isEqualTo(5);
    }

    @Test void limitsEachRemoteIpTo30PerMinuteAndIgnoresUntrustedForwardedHeader() throws Exception {
        for (int i = 0; i < 30; i++) {
            var request = request(body("손님", 1, 100), true);
            request.addHeader("X-Forwarded-For", "198.51.100." + i);
            assertThat(controller.submit(request).getStatusCode().value()).isEqualTo(200);
        }
        assertThat(controller.submit(request(body("손님", 1, 100), true)).getStatusCode().value()).isEqualTo(429);
        var other = request(body("손님", 1, 100), true); other.setRemoteAddr("192.0.2.2");
        assertThat(controller.submit(other).getStatusCode().value()).isEqualTo(200);
        clock.now = clock.now.plusSeconds(60);
        assertThat(controller.submit(request(body("손님", 1, 100), true)).getStatusCode().value()).isEqualTo(200);
    }

    @Test void bodyIsBoundedEvenWithoutContentLength() throws Exception {
        String large = " ".repeat(StairsController.MAX_BODY_BYTES + 1);
        assertThat(controller.submit(request(large, true)).getStatusCode().value()).isEqualTo(413);
        assertThat(controller.submit(request(large, false)).getStatusCode().value()).isEqualTo(413);
    }

    @Test void rejectsFractionalStringMissingTimesAndTooManyInputs() throws Exception {
        for (String input : new String[]{"{\"d\":\"L\",\"t\":1.5}", "{\"d\":\"L\",\"t\":\"0\"}", "{\"d\":\"L\"}"}) {
            String body = "{\"day\":\"2026-11-13\",\"nickname\":\"손님\",\"character\":\"angry\",\"inputs\":[" + input + "]}";
            assertThatThrownBy(() -> controller.submit(request(body, true))).hasMessage("INVALID_INPUTS");
        }
        assertThatThrownBy(() -> controller.submit(request(body("손님", 5001, 1), true))).hasMessage("INVALID_INPUTS");
        assertThatThrownBy(() -> controller.submit(request(body("손님", 4, 1), true))).hasMessage("TOO_FAST");
        assertThat(repository.saves).isZero();
    }

    @Test void exposesOnlyKnownValidationCodes() {
        assertThat(controller.invalid(new IllegalArgumentException("private internal detail")).getBody().toString())
                .contains("INVALID_REQUEST").doesNotContain("private internal detail");
    }

    private String body(String nickname, int count, int interval) {
        return body(nickname, count, interval, controller.today());
    }
    private String body(String nickname, int count, int interval, StairsService.Today today) {
        return json.writeValueAsString(Map.of("day", DAY.toString(), "nickname", nickname, "character", "angry",
                "seed", today.seed(), "ticket", today.ticket(),
                "inputs", StairsRulesTest.inputs(today.seed(), count, interval)));
    }

    @Test void everyRunGetsNewRandomStairs() {
        Set<Integer> seeds = new HashSet<>();
        for (int i = 0; i < 20; i++) seeds.add(controller.today().seed());
        assertThat(seeds).hasSizeGreaterThan(15);
        assertThat(controller.today().day()).isEqualTo(DAY);
    }

    @Test void rejectsForgedSwappedOrExpiredTickets() throws Exception {
        var today = controller.today();
        String forged = body("손님", 3, 100, new StairsService.Today(DAY, today.seed(), today.ticket() + "x"));
        assertThatThrownBy(() -> controller.submit(request(forged, true))).hasMessage("INVALID_TICKET");
        // 다른 판의 표로 쉬운 계단을 고를 수 없다
        var other = controller.today();
        String swapped = body("손님", 3, 100, new StairsService.Today(DAY, other.seed() ^ 1, other.ticket()));
        assertThatThrownBy(() -> controller.submit(request(swapped, true))).hasMessage("INVALID_TICKET");
        String noTicket = json.writeValueAsString(Map.of("day", DAY.toString(), "nickname", "손님", "character", "angry",
                "inputs", StairsRulesTest.inputs(today.seed(), 3, 100)));
        assertThatThrownBy(() -> controller.submit(request(noTicket, true))).hasMessage("INVALID_TICKET");
        String late = body("손님", 3, 100, today);
        clock.now = clock.now.plusSeconds(StairsService.TICKET_SECONDS + 1);
        assertThatThrownBy(() -> controller.submit(request(late, true))).hasMessage("INVALID_TICKET");
        assertThat(repository.saves).isZero();
        assertThat(controller.invalid(new IllegalArgumentException("INVALID_TICKET")).getBody().toString())
                .contains("새로고침");
    }
    private static MockHttpServletRequest request(String text, boolean length) {
        var request = new MockHttpServletRequest() {
            @Override public long getContentLengthLong() { return length ? super.getContentLengthLong() : -1; }
        };
        request.setContentType("application/json"); request.setContent(text.getBytes(StandardCharsets.UTF_8));
        return request;
    }
    private static class TestClock extends Clock {
        Instant now = Instant.parse("2026-11-13T00:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        public Instant instant() { return now; }
    }
    // 서비스 계약용 대역. PostgreSQL 충돌 갱신·정렬은 StairsRepositoryTest에서 별도로 확인한다.
    private static class MemoryRepository extends StairsRepository {
        final Map<String, Item> best = new LinkedHashMap<>();
        int saves;
        MemoryRepository() { super(null); }
        @Override public void save(String nickname, LocalDate day, String character, StairsRules.Score score) {
            saves++;
            Item old = best.get(nickname);
            if (old == null || score.betterThan(new StairsRules.Score(old.height(), old.timeMs()))) {
                best.put(nickname, new Item(1, nickname, character, score.height(), score.timeMs()));
            }
        }
        @Override public Item best(LocalDate day, String nickname) { return best.get(nickname); }
        @Override public Board ranking(LocalDate day) { return new Board(List.copyOf(best.values()), best.size()); }
    }
}
