package com.pheeeew.sigh.domain.repository;

import com.pheeeew.sigh.domain.SighLike;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SighLikeRepository extends JpaRepository<SighLike, Long> {

    Optional<SighLike> findBySighIdAndDeviceId(Long sighId, Long deviceId);
}
