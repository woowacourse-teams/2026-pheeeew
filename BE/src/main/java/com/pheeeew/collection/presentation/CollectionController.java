package com.pheeeew.collection.presentation;

import com.pheeeew.auth.presentation.annotation.CurrentDevice;
import com.pheeeew.collection.application.CollectionQueryService;
import com.pheeeew.collection.presentation.dto.CollectionTodayResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v1/collection-cards")
@RestController
public class CollectionController implements CollectionControllerApi {

    private final CollectionQueryService collectionQueryService;

    @Override
    @GetMapping("/today")
    public ResponseEntity<CollectionTodayResponse> findToday(
            @CurrentDevice UUID devicePublicId
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(CollectionTodayResponse.from(collectionQueryService.findToday(devicePublicId)));
    }
}
