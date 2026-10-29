package com.pheeeew.appconfig.application;

import static com.pheeeew.appconfig.exception.AppConfigErrorCode.CONFIG_NOT_FOUND;

import com.pheeeew.appconfig.application.dto.AppConfigResult;
import com.pheeeew.appconfig.domain.AppConfig;
import com.pheeeew.appconfig.domain.AppConfigPlatform;
import com.pheeeew.appconfig.domain.repository.AppConfigRepository;
import com.pheeeew.appconfig.exception.AppConfigException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class AppConfigService {

    private final AppConfigRepository appConfigRepository;

    public AppConfigResult findByPlatform(AppConfigPlatform platform) {
        AppConfig config = appConfigRepository.findByPlatform(platform)
                .orElseThrow(() -> new AppConfigException(CONFIG_NOT_FOUND));
        return AppConfigResult.from(config);
    }
}
