package com.pheeeew.appversion.application;

import static com.pheeeew.appversion.exception.AppVersionErrorCode.POLICY_NOT_FOUND;

import com.pheeeew.appversion.application.dto.AppVersionResult;
import com.pheeeew.appversion.domain.AppPlatform;
import com.pheeeew.appversion.domain.AppVersion;
import com.pheeeew.appversion.domain.repository.AppVersionRepository;
import com.pheeeew.appversion.exception.AppVersionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class AppVersionService {

    private final AppVersionRepository appVersionRepository;

    public AppVersionResult findByPlatformAndActiveTrue(AppPlatform platform) {
        AppVersion appVersion = appVersionRepository.findByPlatformAndActiveTrue(platform)
                .orElseThrow(() -> new AppVersionException(POLICY_NOT_FOUND));
        return AppVersionResult.from(appVersion);
    }
}
