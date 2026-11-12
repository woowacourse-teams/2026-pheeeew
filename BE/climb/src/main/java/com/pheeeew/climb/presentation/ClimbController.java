package com.pheeeew.climb.presentation;

import com.pheeeew.climb.application.ClimbBoard;
import com.pheeeew.climb.application.ClimbGuestService;
import com.pheeeew.climb.application.ClimbRankingService;
import com.pheeeew.climb.domain.ClimbErrorCode;
import com.pheeeew.climb.domain.ClimbException;
import com.pheeeew.climb.domain.ClimbGuest;
import com.pheeeew.climb.domain.ClimbRanking;
import com.pheeeew.climb.presentation.ClimbResponses.ErrorResponse;
import com.pheeeew.climb.presentation.ClimbResponses.GuestResponse;
import com.pheeeew.climb.presentation.ClimbResponses.MeResponse;
import com.pheeeew.climb.presentation.ClimbResponses.RankingResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/v1/climb")
@ConditionalOnProperty(prefix = "pheeeew.climb", name = "enabled", havingValue = "true")
@RestController
public class ClimbController {

    private static final int TOP_SIZE = 10;

    private final ClimbGuestService guests;
    private final ClimbRankingService ranking;

    public ClimbController(ClimbGuestService guests, ClimbRankingService ranking) {
        this.guests = guests;
        this.ranking = ranking;
    }

    @PostMapping("/guests")
    public ResponseEntity<GuestResponse> create(@RequestBody NicknameRequest request, HttpServletRequest servlet) {
        ClimbGuestService.Created created = guests.create(request.nickname(), servlet.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, ClimbGuestCookieFilter.cookie(created.token()).toString())
                .body(GuestResponse.from(created.guest()));
    }

    @GetMapping("/me")
    public MeResponse findMe(@AuthenticationPrincipal ClimbGuest guest) {
        return MeResponse.of(guest, guests.findLatestRun(guest.id()).orElse(null));
    }

    @PatchMapping("/me")
    public GuestResponse updateMe(@AuthenticationPrincipal ClimbGuest guest, @RequestBody NicknameRequest request) {
        return GuestResponse.from(guests.rename(guest, request.nickname()));
    }

    @GetMapping("/ranking")
    public RankingResponse findRanking(@AuthenticationPrincipal ClimbGuest guest, @RequestParam String board,
            @RequestParam String map) {
        return RankingResponse.of(ClimbRanking.view(ranking.board(map, ClimbBoard.from(board), Instant.now()),
                guest == null ? null : guest.id(), TOP_SIZE), ranking.onlineCount());
    }

    @ExceptionHandler(ClimbException.class)
    public ResponseEntity<ErrorResponse> handle(ClimbException exception) {
        return error(exception.code());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> handleInvalidRequest(Exception exception) {
        return error(ClimbErrorCode.INVALID_REQUEST);
    }

    static ResponseEntity<ErrorResponse> error(ClimbErrorCode code) {
        return ResponseEntity.status(code.status()).body(ErrorResponse.of(code.name(), code.message()));
    }

    public record NicknameRequest(String nickname) {
    }
}
