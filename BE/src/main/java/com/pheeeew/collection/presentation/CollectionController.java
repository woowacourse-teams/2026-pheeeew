package com.pheeeew.collection.presentation;

import com.pheeeew.auth.presentation.annotation.CurrentDevice;
import com.pheeeew.collection.application.CollectionClaimService;
import com.pheeeew.collection.application.CollectionQueryService;
import com.pheeeew.collection.presentation.dto.CollectionCardResponse;
import com.pheeeew.collection.presentation.dto.CollectionClaimRequest;
import com.pheeeew.collection.presentation.dto.CollectionTodayResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v1/collection-cards")
@RestController
public class CollectionController implements CollectionControllerApi {

    private final CollectionQueryService collectionQueryService;
    private final CollectionClaimService collectionClaimService;

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
