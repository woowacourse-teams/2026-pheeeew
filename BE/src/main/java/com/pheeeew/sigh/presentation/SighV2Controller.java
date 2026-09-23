package com.pheeeew.sigh.presentation;

import com.pheeeew.auth.presentation.annotation.CurrentDevice;
import com.pheeeew.common.presentation.dto.CursorResponse;
import com.pheeeew.sigh.application.EmotionService;
import com.pheeeew.sigh.application.dto.SighDetailResult;
import com.pheeeew.sigh.application.dto.SighListResult;
import com.pheeeew.sigh.application.dto.EmotionResult;
import com.pheeeew.sigh.application.dto.SighSaveResult;
import com.pheeeew.sigh.application.like.EmotionLikeRetryService;
import com.pheeeew.sigh.application.like.dto.SighLikeResult;
import com.pheeeew.sigh.presentation.dto.SighCreateV2Request;
import com.pheeeew.sigh.presentation.dto.SighFeature;
import com.pheeeew.sigh.presentation.dto.SighLikeRequest;
import com.pheeeew.sigh.presentation.dto.SighLikeResponse;
import com.pheeeew.sigh.presentation.dto.SighListRequest;
import com.pheeeew.sigh.presentation.dto.SighV2Properties;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v2/sighs")
@RestController
public class SighV2Controller implements SighV2ControllerApi {

    private static final MediaType GEO_JSON = MediaType.parseMediaType("application/geo+json");

    private final EmotionService emotionService;
    private final EmotionLikeRetryService emotionLikeRetryService;

    @Override
    @GetMapping
    public ResponseEntity<CursorResponse<SighFeature<SighV2Properties>>> findAll(
            @ModelAttribute SighListRequest request,
            @CurrentDevice UUID devicePublicId
    ) {
        SighListResult result;
        if (request.isNextPageRequest()) {
            result = emotionService.findNextListPage(request.cursor(), devicePublicId);
        } else {
            result = emotionService.findFirstListPage(request.toBounds(), devicePublicId);
        }

        List<SighFeature<SighV2Properties>> items = result.items().stream()
                .map(item -> toFeature(item.sigh(), item.like()))
                .toList();

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CursorResponse.of(items, result.hasNext(), result.nextCursor()));
    }

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<SighFeature<SighV2Properties>> findById(
            @PathVariable Long id,
            @CurrentDevice UUID devicePublicId
    ) {
        SighDetailResult result = emotionService.findById(id, devicePublicId);

        return ResponseEntity.ok()
                .contentType(GEO_JSON)
                .cacheControl(CacheControl.noStore())
                .body(toFeature(result.sigh(), result.like()));
    }

    @Override
    @PostMapping
    public ResponseEntity<SighFeature<SighV2Properties>> save(
            @RequestBody SighCreateV2Request request,
            @CurrentDevice UUID devicePublicId
    ) {
        SighSaveResult result = emotionService.save(
                request.requestId(),
                request.longitude(),
                request.latitude(),
                request.memo(),
                devicePublicId
        );
        EmotionResult sigh = result.sigh();

        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (result.created()) {
            response = ResponseEntity.created(URI.create("/api/v2/sighs/" + sigh.id()));
        }

        return response
                .contentType(GEO_JSON)
                .cacheControl(CacheControl.noStore())
                .body(toFeature(sigh, result.like()));
    }

    @Override
    @PostMapping("/{sighId}/likes")
    public SighLikeResponse update(
            @PathVariable Long sighId,
            @CurrentDevice UUID devicePublicId,
            @RequestBody SighLikeRequest request
    ) {
        SighLikeResult result = emotionLikeRetryService.update(sighId, devicePublicId, request.liked());
        return SighLikeResponse.from(result);
    }

    private SighFeature<SighV2Properties> toFeature(EmotionResult sigh, SighLikeResult like) {
        return SighFeature.of(sigh, SighV2Properties.of(sigh, like));
    }
}
