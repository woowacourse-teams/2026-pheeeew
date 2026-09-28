package com.pheeeew.emotion.domain.repository;

import com.pheeeew.emotion.domain.AudioUpload;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AudioUploadRepository extends JpaRepository<AudioUpload, Long> {

    Optional<AudioUpload> findByUploadId(String uploadId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select upload from AudioUpload upload where upload.uploadId = :uploadId")
    Optional<AudioUpload> findByUploadIdForUpdate(@Param("uploadId") String uploadId);
}
