package com.pheeeew.collection.presentation;

import com.pheeeew.auth.presentation.annotation.CurrentDevice;
import com.pheeeew.collection.application.CollectionClaimService;
import com.pheeeew.collection.application.CollectionQueryService;
import com.pheeeew.collection.presentation.dto.CollectionCardDetailResponse;
import com.pheeeew.collection.presentation.dto.CollectionCardResponse;
import com.pheeeew.collection.presentation.dto.CollectionClaimRequest;
import com.pheeeew.collection.presentation.dto.CollectionHistoryRequest;
import com.pheeeew.collection.presentation.dto.CollectionTodayResponse;
import com.pheeeew.common.exception.CommonErrorCode;
import com.pheeeew.common.exception.PheeeewException;
import com.pheeeew.common.presentation.dto.CursorResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v1/collection-cards")
@RestController
public class CollectionController implements CollectionControllerApi {

    private final CollectionQueryService collectionQueryService;
    private final CollectionClaimService collectionClaimService;

    @Override
    @GetMapping
    public ResponseEntity<CursorResponse<CollectionCardDetailResponse>> findHistory(
            @CurrentDevice UUID devicePublicId,
            @Valid @ModelAttribute CollectionHistoryRequest request,
            @RequestParam MultiValueMap<String, String> parameters
    ) {
        if (parameters.containsKey("limit") && request.limit() == null
                || parameters.containsKey("emotion") && request.emotion() == null) {
            throw new PheeeewException(CommonErrorCode.INVALID_REQUEST, null);
        }

        var page = collectionQueryService.findHistory(devicePublicId, request.emotion(), request.limit(), request.cursor());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(CursorResponse.of(page.items().stream().map(CollectionCardDetailResponse::from).toList(),
                        page.hasNext(), page.nextCursor()));
    }

    @Override
    @GetMapping("/{cardId}")
    public ResponseEntity<CollectionCardDetailResponse> findDetail(
            @CurrentDevice UUID devicePublicId,
            @PathVariable UUID cardId
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(CollectionCardDetailResponse.from(collectionQueryService.findDetail(devicePublicId, cardId)));
    }

    @Override
    @GetMapping("/today")
    public ResponseEntity<CollectionTodayResponse> findToday(
            @CurrentDevice UUID devicePublicId
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(CollectionTodayResponse.from(collectionQueryService.findToday(devicePublicId)));
    }

    @Override
    @PostMapping("/claim")
    public ResponseEntity<CollectionCardResponse> claim(
            @CurrentDevice UUID devicePublicId,
            @Valid @RequestBody CollectionClaimRequest request
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(CollectionCardResponse.from(collectionClaimService.claim(devicePublicId, request.date(), request.state())));
    }
}
