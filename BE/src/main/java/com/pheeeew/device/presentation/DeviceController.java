package com.pheeeew.device.presentation;

import com.pheeeew.device.application.DeviceChallengeService;
import com.pheeeew.device.application.DeviceService;
import com.pheeeew.device.application.DeviceTokenService;
import com.pheeeew.device.application.dto.AccessTokenResult;
import com.pheeeew.device.application.dto.DeviceAttestation;
import com.pheeeew.device.application.dto.DeviceChallengeResult;
import com.pheeeew.device.application.dto.DeviceSaveResult;
import com.pheeeew.device.presentation.dto.AccessTokenReissueRequest;
import com.pheeeew.device.presentation.dto.AccessTokenResponse;
import com.pheeeew.device.presentation.dto.DeviceChallengeResponse;
import com.pheeeew.device.presentation.dto.DeviceCreateRequest;
import com.pheeeew.device.presentation.dto.DeviceTokenResponse;
import com.pheeeew.device.presentation.dto.DeviceV3CreateRequest;
import com.pheeeew.device.presentation.dto.NicknameAvailabilityResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api")
@RestController
public class DeviceController implements DeviceControllerApi {

    private final DeviceService deviceService;
    private final DeviceTokenService deviceTokenService;
    private final DeviceChallengeService deviceChallengeService;

    @Override
    @GetMapping("/v2/devices/nicknames/availability")
    public ResponseEntity<NicknameAvailabilityResponse> findNicknameAvailability(
            @RequestParam("nickname") String nickname
    ) {
        boolean available = deviceService.findNicknameAvailability(nickname);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(NicknameAvailabilityResponse.from(available));
    }

    @Override
    @PostMapping("/v2/devices")
    public ResponseEntity<DeviceTokenResponse> save(
            @Valid @RequestBody DeviceCreateRequest request
    ) {
        DeviceSaveResult result = deviceService.save(
                request.requestId(),
                DeviceAttestation.of(
                        request.attestation().platform(),
                        request.attestation().token(),
                        request.attestation().challenge(),
                        request.attestation().keyId()
                )
        );

        HttpStatus status = HttpStatus.OK;
        if (result.created()) {
            status = HttpStatus.CREATED;
        }

        return ResponseEntity.status(status)
                .body(DeviceTokenResponse.from(result));
    }

    @Override
    @PostMapping("/v3/devices")
    public ResponseEntity<DeviceTokenResponse> save(
            @Valid @RequestBody DeviceV3CreateRequest request
    ) {
        DeviceSaveResult result = deviceService.save(
                request.requestId(),
                request.nickname(),
                DeviceAttestation.of(
                        request.attestation().platform(),
                        request.attestation().token(),
                        request.attestation().challenge(),
                        request.attestation().keyId()
                )
        );

        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(DeviceTokenResponse.from(result));
    }

    @Override
    @PostMapping("/v2/devices/challenge")
    public DeviceChallengeResponse issueChallenge() {
        DeviceChallengeResult result = deviceChallengeService.save();

        return DeviceChallengeResponse.from(result);
    }

    @Override
    @PostMapping("/v2/devices/tokens")
    public AccessTokenResponse reissueAccessToken(
            @Valid @RequestBody AccessTokenReissueRequest request
    ) {
        AccessTokenResult result = deviceTokenService.reissueAccessToken(request.refreshToken());

        return AccessTokenResponse.from(result);
    }
}
