package com.pheeeew.notification.presentation;

import com.pheeeew.auth.presentation.annotation.CurrentDevice;
import com.pheeeew.notification.application.NotificationSettingService;
import com.pheeeew.notification.domain.NotificationSettings;
import com.pheeeew.notification.presentation.dto.NotificationSettingsResponse;
import com.pheeeew.notification.presentation.dto.NotificationSettingsUpdateRequest;
import com.pheeeew.notification.presentation.dto.PushTokenUpdateRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v3/devices/me")
@RestController
public class NotificationSettingController implements NotificationSettingControllerApi {

    private final NotificationSettingService notificationSettingService;

    @Override
    @PutMapping("/push-token")
    public ResponseEntity<Void> updatePushToken(
            @CurrentDevice UUID devicePublicId,
            @Valid @RequestBody PushTokenUpdateRequest request
    ) {
        notificationSettingService.updatePushToken(devicePublicId, request.token());
        return ResponseEntity.noContent().build();
    }

    @Override
    @DeleteMapping("/push-token")
    public ResponseEntity<Void> deletePushToken(
            @CurrentDevice UUID devicePublicId
    ) {
        notificationSettingService.deletePushToken(devicePublicId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping("/notification-settings")
    public ResponseEntity<NotificationSettingsResponse> findSettings(
            @CurrentDevice UUID devicePublicId
    ) {
        NotificationSettings settings = notificationSettingService.findSettings(devicePublicId);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(NotificationSettingsResponse.from(settings));
    }

    @Override
    @PutMapping("/notification-settings")
    public ResponseEntity<Void> updateSettings(
            @CurrentDevice UUID devicePublicId,
            @Valid @RequestBody NotificationSettingsUpdateRequest request
    ) {
        notificationSettingService.updateSettings(
                devicePublicId, request.collectionIssuedEnabled(), request.rankDroppedEnabled());
        return ResponseEntity.noContent().build();
    }
}
