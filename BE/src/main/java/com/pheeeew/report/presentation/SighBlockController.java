package com.pheeeew.report.presentation;

import com.pheeeew.auth.presentation.annotation.CurrentDevice;
import com.pheeeew.common.presentation.dto.CursorResponse;
import com.pheeeew.report.application.EmotionBlockService;
import com.pheeeew.report.application.dto.BlockListResult;
import com.pheeeew.report.application.dto.BlockSaveResult;
import com.pheeeew.report.presentation.dto.BlockCreateRequest;
import com.pheeeew.report.presentation.dto.SighBlockResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v2/blocks/sighs")
@RestController
public class SighBlockController implements SighBlockControllerApi {

    private final EmotionBlockService emotionBlockService;

    @Override
    @PostMapping
    public ResponseEntity<SighBlockResponse> save(
            @CurrentDevice UUID devicePublicId,
            @Valid @RequestBody BlockCreateRequest request
    ) {
        BlockSaveResult result = emotionBlockService.save(request.sighId(), devicePublicId);

        HttpStatus status = HttpStatus.OK;
        if (result.created()) {
            status = HttpStatus.CREATED;
        }

        return ResponseEntity.status(status)
                .body(SighBlockResponse.from(result.block()));
    }

    @Override
    @GetMapping
    public CursorResponse<SighBlockResponse> findAll(
            @CurrentDevice UUID devicePublicId,
            @RequestParam(required = false) String cursor
    ) {
        BlockListResult result = emotionBlockService.findAll(devicePublicId, cursor);

        List<SighBlockResponse> items = result.items().stream()
                .map(SighBlockResponse::from)
                .toList();

        return CursorResponse.of(items, result.hasNext(), result.nextCursor());
    }

    @Override
    @DeleteMapping("/{sighId}")
    public ResponseEntity<Void> delete(
            @CurrentDevice UUID devicePublicId,
            @PathVariable Long sighId
    ) {
        emotionBlockService.delete(sighId, devicePublicId);

        return ResponseEntity.noContent().build();
    }
}
