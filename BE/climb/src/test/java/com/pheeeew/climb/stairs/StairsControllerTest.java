package com.pheeeew.climb.stairs;

import static org.assertj.core.api.Assertions.*;

import com.pheeeew.climb.domain.ClimbGuest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class StairsControllerTest {
    private final StairsController controller = new StairsController(new StairsService(null, Clock.systemUTC()));
    private final ClimbGuest guest = new ClimbGuest(UUID.randomUUID(), "abcd", "손님", Instant.EPOCH, Instant.EPOCH);

    @Test void submissionRequiresGuest() throws Exception {
        assertThat(controller.submit(null, request("{}", true)).getStatusCode().value()).isEqualTo(401);
    }
    @Test void bodyIsBoundedEvenWithoutContentLength() throws Exception {
        String large = " ".repeat(StairsController.MAX_BODY_BYTES + 1);
        assertThat(controller.submit(guest, request(large, true)).getStatusCode().value()).isEqualTo(413);
        assertThat(controller.submit(guest, request(large, false)).getStatusCode().value()).isEqualTo(413);
    }
    @Test void rejectsFractionalStringAndMissingTimesInsteadOfCoercing() {
        for (String input : new String[]{"{\"d\":\"L\",\"t\":1.5}", "{\"d\":\"L\",\"t\":\"0\"}", "{\"d\":\"L\"}"}) {
            String body = "{\"day\":\"2026-11-13\",\"character\":\"angry\",\"inputs\":[" + input + "]}";
            assertThatThrownBy(() -> controller.submit(guest, request(body, true))).hasMessage("INVALID_INPUTS");
        }
    }
    @Test void exposesOnlyKnownValidationCodes() {
        assertThat(controller.invalid(new IllegalArgumentException("private internal detail")).getBody().toString())
                .contains("INVALID_REQUEST").doesNotContain("private internal detail");
    }
    private static MockHttpServletRequest request(String text, boolean length) {
        var request = new MockHttpServletRequest() {
            @Override public long getContentLengthLong() { return length ? super.getContentLengthLong() : -1; }
        };
        request.setContentType("application/json"); request.setContent(text.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
