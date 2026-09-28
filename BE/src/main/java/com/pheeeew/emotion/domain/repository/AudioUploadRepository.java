package com.pheeeew.emotion.domain.repository;

import com.pheeeew.emotion.domain.AudioUpload;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AudioUploadRepository extends JpaRepository<AudioUpload, Long> {

    Optional<AudioUpload> findByUploadId(String uploadId);
}
