package com.pheeeew.climb.stairs;

import com.pheeeew.climb.domain.ClimbGuest;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/v1/climb/stairs")
@ConditionalOnProperty(prefix = "pheeeew.climb", name = "enabled", havingValue = "true")
public class StairsController {
    public static final int MAX_BODY_BYTES = 160000;
    private final StairsService service;
    private final JsonMapper json = JsonMapper.builder().build();

    public StairsController(StairsService service) { this.service = service; }

    @GetMapping("/today")
    public StairsService.Today today() { return service.today(); }

    @GetMapping("/ranking")
    public StairsRepository.Board ranking(@RequestParam(required = false) String day,
            @AuthenticationPrincipal ClimbGuest guest) {
        return service.ranking(day == null ? service.today().day() : LocalDate.parse(day),
                guest == null ? null : guest.id());
    }

    @PostMapping(value = "/runs", consumes = "application/json")
    public ResponseEntity<?> submit(@AuthenticationPrincipal ClimbGuest guest, HttpServletRequest request)
            throws IOException {
        if (guest == null) return error(401, "UNAUTHORIZED");
        // Content-Length가 없는 chunked 요청도 읽는 양을 제한한 뒤 JSON을 파싱한다.
        if (request.getContentLengthLong() > MAX_BODY_BYTES) return error(413, "BODY_TOO_LARGE");
        byte[] bytes = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (bytes.length > MAX_BODY_BYTES) return error(413, "BODY_TOO_LARGE");
        JsonNode root = json.readTree(bytes);
        if (root == null || !root.isObject() || !root.path("day").isString()
                || !root.path("character").isString()) throw new IllegalArgumentException("INVALID_REQUEST");
        JsonNode array = root.path("inputs");
        if (!array.isArray() || array.isEmpty() || array.size() > StairsRules.MAX_INPUTS) {
            throw new IllegalArgumentException("INVALID_INPUTS");
        }
        var inputs = new ArrayList<StairsRules.Input>();
        for (JsonNode input : array) {
            JsonNode t = input.path("t"), d = input.path("d");
            if (!t.isIntegralNumber() || !t.canConvertToInt() || !d.isString()) {
                throw new IllegalArgumentException("INVALID_INPUTS");
            }
            inputs.add(new StairsRules.Input(d.asString(), t.intValue()));
        }
        return ResponseEntity.ok(service.submit(guest.id(), LocalDate.parse(root.path("day").asString()),
                root.path("character").asString(), inputs));
    }

    @ExceptionHandler({IllegalArgumentException.class, java.time.DateTimeException.class, JacksonException.class})
    public ResponseEntity<?> invalid(Exception exception) {
        String code = exception instanceof IllegalArgumentException ? exception.getMessage() : "INVALID_REQUEST";
        if (!java.util.Set.of("INVALID_REQUEST", "INVALID_INPUTS", "TOO_FAST", "GAUGE_EXPIRED", "INVALID_DAY")
                .contains(code == null ? "" : code)) code = "INVALID_REQUEST";
        return error(400, code);
    }

    private static ResponseEntity<?> error(int status, String code) {
        return ResponseEntity.status(status).body(Map.of("code", code, "message", switch (code) {
            case "TOO_FAST" -> "너무 빠른 연속 입력이에요. 다시 도전해 주세요.";
            case "GAUGE_EXPIRED" -> "시간 게이지가 끝난 뒤의 입력은 기록할 수 없어요.";
            case "INVALID_DAY" -> "오늘의 계단으로 다시 시작해 주세요.";
            case "BODY_TOO_LARGE" -> "기록 크기 한도를 넘었어요.";
            case "UNAUTHORIZED" -> "닉네임을 등록해 주세요.";
            default -> "기록 형식을 확인해 주세요.";
        }));
    }
}
