package com.pheeeew.appconfig.domain.repository;

import com.pheeeew.appconfig.domain.AppConfig;
import com.pheeeew.appconfig.domain.AppConfigPlatform;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppConfigRepository extends JpaRepository<AppConfig, Long> {

    Optional<AppConfig> findByPlatform(AppConfigPlatform platform);
}
