package com.pheeeew.emotion.presentation;

import com.pheeeew.auth.presentation.annotation.CurrentDevice;
import com.pheeeew.emotion.application.AudioUploadService;
import com.pheeeew.emotion.application.dto.AudioUploadResult;
import com.pheeeew.emotion.presentation.dto.AudioUploadRequest;
import com.pheeeew.emotion.presentation.dto.AudioUploadResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v1/audio-uploads")
@RestController
public class AudioUploadController implements AudioUploadControllerApi {

    private final AudioUploadService audioUploadService;

    @Override
    @PostMapping
    public ResponseEntity<AudioUploadResponse> prepare(
            @RequestBody AudioUploadRequest request,
            @CurrentDevice UUID devicePublicId
    ) {
        var result = audioUploadService.prepare(
                devicePublicId, request.contentType(), request.contentLength());
        var response = AudioUploadResponse.from(result);

        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(response);
    }
}
