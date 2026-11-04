package com.pheeeew.appconfig.presentation;

import com.pheeeew.appconfig.application.AppConfigService;
import com.pheeeew.appconfig.application.dto.AppConfigResult;
import com.pheeeew.appconfig.domain.AppConfigPlatform;
import com.pheeeew.appconfig.presentation.dto.AppConfigResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v2/app")
@RestController
public class AppConfigController implements AppConfigControllerApi {

    private final AppConfigService appConfigService;

    @Override
    @GetMapping("/config")
    public ResponseEntity<AppConfigResponse> findByPlatform(
            @RequestParam(name = "platform", required = false) String platform,
            HttpServletResponse response
    ) {
        // 오류 응답도 설정 조회 결과이므로 플랫폼 검증 전에 캐시를 금지합니다.
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        AppConfigResult result = appConfigService.findByPlatform(AppConfigPlatform.from(platform));
        return ResponseEntity.ok().body(AppConfigResponse.from(result));
    }
}
