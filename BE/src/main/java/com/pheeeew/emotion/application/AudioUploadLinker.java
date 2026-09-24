package com.pheeeew.emotion.application;

import com.pheeeew.emotion.exception.EmotionException;
import java.util.UUID;

/**
 * 감정 등록 요청에 녹음 업로드를 연결하는 서버 내부 계약이다.
 * 업로드 담당 구현체가 발급 이력과 S3 객체를 확인하고, 감정 파트는 반환된 키로 Audio를 생성한다.
 *
 * <p>구현체는 감정 저장과 같은 DB 및 트랜잭션에 참여해야 한다.
 * 활성 트랜잭션이 없는 호출은 거부하고, 별도 트랜잭션에서 사용 처리를 먼저 확정하지 않는다.
 * S3 확인은 조회만 수행하며, 이 호출에서 객체를 이동하거나 삭제하지 않는다.
 * 감정 저장에 실패하면 업로드 연결도 롤백되어 같은 업로드로 재시도할 수 있어야 한다.
 *
 * <p>실제 저장 방식, S3 확인, 동시성 제어는 구현체의 책임이다.
 * 다른 DB나 외부 API로 사용 처리를 분리하려면 이 원자성 계약부터 다시 설계해야 한다.
 */
public interface AudioUploadLinker {

    /**
     * 업로드를 하나의 감정 등록 요청에 원자적으로 연결하고 영구 파일 참조값을 반환한다.
     *
     * <p>발급 이력이 없거나 발급 기기가 다르면 동일한 오류로 거부한다.
     * 아직 연결되지 않은 업로드는 실제 업로드 완료를 확인한 후 연결한다.
     * 같은 uploadId에 대한 동시 호출에서도 하나의 (deviceId, requestId)만 연결할 수 있어야 한다.
     * 이미 동일한 기기·요청에 연결됐다면 재사용 오류 대신 최초 연결의 키를 반환한다.
     * 다른 요청에 연결됐다면 거부한다.
     *
     * <p>호출자는 먼저 requestId로 기존 감정을 조회한다. 같은 기기의 재시도는 기존 결과를 반환하고,
     * 다른 기기의 requestId 재사용은 거부하므로, 완료된 감정의 재시도에는 이 메서드를 호출하지 않는다.
     * 동시 삽입 충돌 시에는 실패한 트랜잭션을 롤백한 뒤 별도 트랜잭션에서 기존 감정을 재조회한다.
     *
     * <p>반환 키는 빈 값이나 presigned URL이 아닌 서버가 관리하는 파일 참조값이다.
     * PUT URL 재사용에 따른 덮어쓰기 방지는 별도 합의·구현이 필요하며 이 인터페이스만으로 보장되지 않는다.
     *
     * @param uploadId 업로드 준비 응답의 식별자. 형식을 임의로 변환하지 않는다.
     * @param deviceId Bearer 인증으로 확인한 기기의 내부 ID
     * @param requestId 감정 등록 요청의 식별자
     * @return 최초 연결 시 확인한 objectKey
     * @throws EmotionException EMOTION_AUDIO_UPLOAD_NOT_FOUND: 발급 이력이 없거나 발급 기기가 다름
     * @throws EmotionException EMOTION_AUDIO_UPLOAD_NOT_READY: 업로드가 완료되지 않음
     * @throws EmotionException EMOTION_AUDIO_UPLOAD_ALREADY_USED: 다른 등록 요청에 연결됨
     * @throws EmotionException EMOTION_AUDIO_UPLOAD_UNAVAILABLE: 저장소 장애 등으로 업로드 확인 불가.
     *         업로드 미완료로 처리하지 않고 원인을 보존하며 연결을 확정하지 않는다.
     */
    String claim(String uploadId, Long deviceId, UUID requestId);
}
