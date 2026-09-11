package com.pheeeew.sigh.presentation;

import com.pheeeew.common.presentation.dto.CursorResponse;
import com.pheeeew.sigh.application.SighService;
import com.pheeeew.sigh.application.dto.SighListResult;
import com.pheeeew.sigh.application.dto.SighResult;
import com.pheeeew.sigh.application.dto.SighSaveResult;
import com.pheeeew.sigh.presentation.dto.SighCreateV2Request;
import com.pheeeew.sigh.presentation.dto.SighFeature;
import com.pheeeew.sigh.presentation.dto.SighListRequest;
import com.pheeeew.sigh.presentation.dto.SighV2Properties;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
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

    private final SighService sighService;

    @Override
    @GetMapping
    public CursorResponse<SighFeature<SighV2Properties>> findAll(
            @ModelAttribute SighListRequest request
    ) {
        SighListResult result;
        if (request.isNextPageRequest()) {
            result = sighService.findNextListPage(request.cursor());
        } else {
            result = sighService.findFirstListPage(request.toBounds());
        }

        List<SighFeature<SighV2Properties>> items = result.items().stream()
                .map(this::toFeature)
                .toList();

        return CursorResponse.of(items, result.hasNext(), result.nextCursor());
    }

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<SighFeature<SighV2Properties>> findById(
            @PathVariable Long id
    ) {
        SighResult result = sighService.findById(id);

        return ResponseEntity.ok()
                .contentType(GEO_JSON)
                .body(toFeature(result));
    }

    @Override
    @PostMapping
    public ResponseEntity<SighFeature<SighV2Properties>> save(
            @RequestBody SighCreateV2Request request
    ) {
        SighSaveResult result = sighService.save(
                request.requestId(),
                request.longitude(),
                request.latitude(),
                request.memo()
        );
        SighResult sigh = result.sigh();

        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (result.created()) {
            response = ResponseEntity.created(URI.create("/api/v2/sighs/" + sigh.id()));
        }

        return response
                .contentType(GEO_JSON)
                .body(toFeature(sigh));
    }

    private SighFeature<SighV2Properties> toFeature(SighResult sigh) {
        return SighFeature.of(sigh, SighV2Properties.from(sigh));
    }
}
