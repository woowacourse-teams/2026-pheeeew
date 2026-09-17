package com.pheeeew.appversion.domain.repository;

import com.pheeeew.appversion.domain.AppPlatform;
import com.pheeeew.appversion.domain.AppVersion;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppVersionRepository extends JpaRepository<AppVersion, Long> {

    Optional<AppVersion> findByPlatformAndActiveTrue(AppPlatform platform);
}
