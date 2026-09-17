package com.pheeeew.appversion.presentation;

import com.pheeeew.appversion.application.AppVersionService;
import com.pheeeew.appversion.application.dto.AppVersionResult;
import com.pheeeew.appversion.domain.AppPlatform;
import com.pheeeew.appversion.presentation.dto.AppVersionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v2/app")
@RestController
public class AppVersionController implements AppVersionControllerApi {

    private final AppVersionService appVersionService;

    @Override
    @GetMapping("/version")
    public AppVersionResponse findByPlatformAndActiveTrue(
            @RequestParam(name = "platform", required = false) String platform
    ) {
        AppVersionResult result = appVersionService.findByPlatformAndActiveTrue(AppPlatform.from(platform));
        return AppVersionResponse.from(result);
    }
}
