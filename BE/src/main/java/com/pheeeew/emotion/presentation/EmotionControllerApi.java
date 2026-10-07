package com.pheeeew.emotion.presentation;

import com.pheeeew.common.exception.ErrorResponse;
import com.pheeeew.common.presentation.dto.CursorResponse;
import com.pheeeew.emotion.presentation.dto.EmotionListRequest;
import com.pheeeew.emotion.presentation.dto.EmotionMapResponse;
import com.pheeeew.emotion.presentation.dto.EmotionRegionMapRequest;
import com.pheeeew.emotion.presentation.dto.EmotionRegionMapResponse;
import com.pheeeew.emotion.presentation.dto.EmotionUpdateRequest;
import com.pheeeew.emotion.domain.EmojiType;
import com.pheeeew.emotion.presentation.dto.EmotionDetailResponse;
import com.pheeeew.emotion.presentation.dto.EmotionCreateRequest;
import com.pheeeew.emotion.presentation.dto.EmotionV3CreateRequest;
import com.pheeeew.emotion.presentation.dto.EmotionCreateResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

@Tag(name = "감정", description = "감정과 이모지 API")
public interface EmotionControllerApi {

    @Operation(summary = "바텀시트 감정 목록 조회", description = """
            메모 또는 녹음이 있는 감정(contentType=MEMO, AUDIO)만 반환합니다. 감정 상태만 기록한 NONE은 제외합니다.
            내용 조건은 페이지 제한 전에 적용하며 별도의 유형 필터 파라미터는 받지 않습니다.
            첫 페이지에는 minLongitude, minLatitude, maxLongitude, maxLatitude를 전달합니다.
            groupId를 생략하면 그룹 없는 감정까지 전체 조회하며, 지정하면 해당 그룹의 스탬프만 조회합니다.
            그룹 필터는 공개 감정의 조회 조건이며 그룹 가입 여부로 제한하지 않습니다.
            다음 페이지에는 영역과 그룹 조건이 담긴 cursor만 전달합니다. 날짜변경선을 넘는 영역은 minLongitude > maxLongitude로 표현합니다.
            기간 제한 없이 (createdAt DESC, id DESC) 순으로 최대 20개씩 조회합니다.
            최초 조회 이후 작성된 감정은 제외하고, 삭제·차단은 매 페이지에 반영합니다.
            각 항목은 GeoJSON Feature이며 여섯 이모지 집계와 본인 선택 여부를 포함합니다.
            각 항목의 properties.isMine은 인증된 기기가 작성했는지 나타내며 작성 기기가 없으면 false입니다.
            contentType이 AUDIO이면 audio.playbackUrl과 audio.expiresAt을 함께 반환합니다.
            재생 URL은 1시간 동안 유효하며 만료되면 목록 또는 상세를 다시 조회합니다. MEMO의 audio는 null입니다.
            """, security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "감정 목록과 다음 커서"),
            @ApiResponse(responseCode = "400", description = "영역 또는 커서가 올바르지 않음"),
            @ApiResponse(responseCode = "401", description = "인증할 수 없음"),
            @ApiResponse(responseCode = "503", description = "녹음 재생 URL을 발급할 수 없음")
    })
    ResponseEntity<CursorResponse<EmotionDetailResponse>> findAll(@Valid EmotionListRequest request,
            @Parameter(hidden = true) UUID devicePublicId);


    @Operation(summary = "지도 스탬프 조회", description = """
            내용 유무와 관계없이 NONE, MEMO, AUDIO 유형의 감정을 모두 조회합니다.
            지도 표시에 필요한 감정 ID·좌표·작성 시각·상태·각도·그룹 스탬프만 반환합니다.
            메모·닉네임·이모지 집계·본인 작성 여부·녹음 재생 URL은 포함하지 않습니다. 바텀시트는 감정 목록 API로 조회합니다.
            첫 페이지에는 minLongitude, minLatitude, maxLongitude, maxLatitude를 전달합니다.
            groupId를 생략하면 전체를 조회하며 지정하면 해당 그룹만 조회합니다. 그룹 가입 여부로 제한하지 않습니다.
            다음 페이지에는 반환된 cursor만 전달합니다. 영역이나 그룹을 바꾸면 첫 페이지부터 다시 조회합니다.
            기간 제한 없이 (createdAt DESC, id DESC) 순으로 최대 200개씩 반환합니다. 총량을 잘라내지 않으며 hasNext가 false일 때까지 이어서 조회합니다.
            지도에서는 오래된 감정부터 그려 최신 감정이 위에 표시되도록 합니다.
            최초 조회 이후 작성된 감정은 제외하고 삭제·차단은 매 페이지에 반영합니다.
            """, security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "지도 스탬프와 다음 커서"),
            @ApiResponse(responseCode = "400", description = "영역 또는 커서가 올바르지 않음"),
            @ApiResponse(responseCode = "401", description = "인증할 수 없음")
    })
    ResponseEntity<CursorResponse<EmotionMapResponse>> findMap(
            @Valid EmotionListRequest request,
            @Parameter(hidden = true) UUID devicePublicId
    );

    @Operation(summary = "행정구역 지도 감정 요약 조회", description = """
            minLongitude, minLatitude, maxLongitude, maxLatitude와 level은 필수입니다.
            날짜변경선을 넘는 영역은 minLongitude > maxLongitude로 표현합니다.
            클라이언트가 확대 정도에 따라 SIDO, SIGUNGU, EMD를 선택합니다. 서버는 계층을 자동으로 전환하지 않습니다.
            SIGUNGU는 SGIS 시군구 계층입니다. 구가 있는 수원·성남·용인 등은 시 전체 대신 각 구를 반환합니다.

            화면과 경계가 교차하는 지역을 선택하며 경계선 접촉도 포함합니다.
            개수와 대표 감정은 화면이 아닌 선택한 지역 전체의 기간 하한 없는 누적 기록에서 계산합니다.
            NONE, MEMO, AUDIO를 모두 포함하고 삭제·서비스 전체 비노출 기록은 제외합니다.
            개인 감정 차단과 작성자 차단은 요약에 적용하지 않습니다. 기존 개별 조회의 차단 정책은 유지합니다.
            groupId를 생략하면 그룹 없는 감정까지 전체 조회하고 지정하면 해당 그룹만 집계합니다. 그룹 가입 여부로 제한하지 않습니다.

            지역 코드순으로 모든 결과를 최상위 JSON 배열로 한 번에 반환합니다. 페이지네이션 필드는 없습니다.
            0개 지역은 생략하며 결과가 없으면 빈 배열을 반환합니다.
            각 항목은 지역 코드를 문자열 id로 갖는 GeoJSON Feature이며 응답 전체는 application/json입니다.
            geometry는 지역 내부의 고정 표시점으로 요청 화면 밖일 수 있습니다. 좌표는 경도, 위도 순서입니다.
            대표 감정은 최빈값이며 동률은 ANGRY, DISCOURAGED, EXHAUSTED, FRUSTRATED, IRRITATED 순서입니다.
            NULL 상태도 총 개수에 포함하며 모든 대상의 상태가 NULL이면 대표 감정은 null입니다.

            최신 성공 응답이 현재 요청의 완전한 결과입니다. 이전 결과와 단순 병합하지 않습니다.
            level·groupId·화면이 바뀐 요청과 늦게 도착한 이전 응답을 구분합니다.
            지역 요약 준비가 완료되지 않으면 빈 결과 대신 EMOTION-013과 503을 반환합니다.
            """, security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "행정구역별 누적 감정 요약"),
            @ApiResponse(responseCode = "400", description = "필수 영역·계층이 누락됐거나 영역·계층·그룹 ID가 올바르지 않음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증할 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "지역 요약 준비가 완료되지 않음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<List<EmotionRegionMapResponse>> findRegionMap(
            @Valid EmotionRegionMapRequest request,
            @Parameter(hidden = true) UUID devicePublicId
    );

    @Operation(summary = "감정 등록", description = """
            선택 위치에 감정을 등록합니다. contentType은 NONE, MEMO, AUDIO 중 하나입니다.
            v1은 기존 앱을 위한 익명 전용 등록 경로입니다. 신규 감정은 익명으로 등록하며 '익명'으로 표시합니다.
            anonymous는 v1 요청 필드가 아니며, 전달해도 신규 등록의 익명 여부에 영향을 주지 않습니다.
            기존 성공 requestId의 재시도는 최초 등록 결과를 유지합니다.

            최종 저장 좌표가 SGIS 2025년 2분기 읍면동 경계에 포함되거나 경계에서 1km 이내일 때 신규 등록할 수 있습니다.
            경계 밖은 폴리곤까지 가장 가까운 지역에 연결하며 좌표는 이동시키지 않습니다.
            배정할 지역이 없으면 EMOTION-014와 400을 반환합니다. 녹음 확인·연결과 감정 저장은 시작하지 않습니다.
            이는 선택 좌표의 지원 범위이며 사용자의 실제 위치를 검증하는 정책은 아닙니다.
            최초 등록과 같은 기기의 requestId 재시도는 생성 제한을 통과하면 최초 감정 ID를 200으로 반환합니다.
            다른 기기가 사용한 requestId는 409입니다. 녹음은 업로드 완료된 audioUploadId로 연결합니다.
            groupId를 전달하면 현재 소속된 그룹의 스탬프를 연결합니다. 생략하거나 null이면 그룹 스탬프가 없습니다.
            연결된 스탬프가 변경되면 기존 감정에도 최신 모양을 표시합니다.
            서버의 지역 분류 준비가 완료되지 않은 경우 신규 등록이 일시적으로 제한되며, EMOTION-013과 503을 반환합니다.
            기존 성공 requestId는 지역 자료 상태나 지원 범위 밖 좌표와 관계없이 최초 감정을 반환하며 재분류하지 않습니다.
            기존 범위 밖 기록도 수정·삭제할 수 있습니다.

            한 기기는 1초에 한 번만 등록할 수 있습니다. 초과하면 429와 Retry-After 헤더를 반환합니다.
            이 제한은 requestId 조회와 지역 검사 전에 적용되므로 같은 requestId의 빠른 재시도도 429가 될 수 있습니다.
            지역 거부 후 다시 요청할 때도 이 제한을 적용합니다.
            재시도할 때 같은 requestId를 보내면 감정이 중복 생성되지 않습니다.
            """, security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장된 감정 ID"),
            @ApiResponse(responseCode = "400", description = "등록 필드·내용 조합이 올바르지 않거나 지원 범위 밖 위치(EMOTION-014)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증할 수 없음"),
            @ApiResponse(responseCode = "404", description = "녹음 업로드가 없거나 해당 기기의 업로드가 아님, 또는 사용할 수 없는 그룹"),
            @ApiResponse(responseCode = "409", description = "요청 식별자 충돌 또는 녹음 미완료·이미 사용됨"),
            @ApiResponse(responseCode = "429", description = "같은 기기가 1초 안에 다시 생성을 요청함",
                    headers = @Header(name = "Retry-After", description = "다시 시도하기까지 기다려야 하는 초입니다.",
                            schema = @Schema(type = "string", example = "1")),
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "녹음 확인 기능을 사용할 수 없거나 서버의 지역 분류 준비가 완료되지 않음(EMOTION-013)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<EmotionCreateResponse> save(@Valid EmotionCreateRequest request,
            @Parameter(hidden = true) UUID devicePublicId);


    @Operation(summary = "감정 등록 v3", description = """
            선택 위치에 감정을 등록합니다. contentType은 NONE, MEMO, AUDIO 중 하나입니다.
            anonymous는 JSON boolean으로 전달합니다. true이면 익명이며 생략하거나 null이어도 익명으로 등록합니다.
            false이면 기명이며 기기 닉네임을 먼저 설정해야 합니다. 미설정이면 DEVICE-010과 409를 반환합니다.
            닉네임 설정 후 같은 requestId로 다시 등록할 수 있습니다. 기존 성공 requestId는 최초 익명 선택을 유지합니다.
            익명 감정은 '익명'으로 표시하며, 기명 감정은 닉네임 수정 후에도 기기의 현재 닉네임으로 표시합니다.

            최종 저장 좌표가 SGIS 2025년 2분기 읍면동 경계에 포함되거나 경계에서 1km 이내일 때 신규 등록할 수 있습니다.
            경계 밖은 폴리곤까지 가장 가까운 지역에 연결하며 좌표는 이동시키지 않습니다.
            배정할 지역이 없으면 EMOTION-014와 400을 반환합니다. 녹음 확인·연결과 감정 저장은 시작하지 않습니다.
            이는 선택 좌표의 지원 범위이며 사용자의 실제 위치를 검증하는 정책은 아닙니다.
            최초 등록과 같은 기기의 requestId 재시도는 생성 제한을 통과하면 최초 감정 ID를 200으로 반환합니다.
            다른 기기가 사용한 requestId는 409입니다. 녹음은 업로드 완료된 audioUploadId로 연결합니다.
            groupId를 전달하면 현재 소속된 그룹의 스탬프를 연결합니다. 생략하거나 null이면 그룹 스탬프가 없습니다.
            연결된 스탬프가 변경되면 기존 감정에도 최신 모양을 표시합니다.
            서버의 지역 분류 준비가 완료되지 않은 경우 신규 등록이 일시적으로 제한되며, EMOTION-013과 503을 반환합니다.
            기존 성공 requestId는 지역 자료 상태나 지원 범위 밖 좌표와 관계없이 최초 감정을 반환하며 재분류하지 않습니다.
            기존 범위 밖 기록도 수정·삭제할 수 있습니다.

            한 기기는 v1과 v3를 합쳐 1초에 한 번만 등록할 수 있습니다. 초과하면 429와 Retry-After 헤더를 반환합니다.
            이 제한은 requestId 조회와 지역 검사 전에 적용되므로 같은 requestId의 빠른 재시도도 429가 될 수 있습니다.
            지역 거부 후 다시 요청할 때도 이 제한을 적용합니다.
            재시도할 때 같은 requestId를 보내면 감정이 중복 생성되지 않습니다.
            """, security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장된 감정 ID"),
            @ApiResponse(responseCode = "400", description = "등록 필드·내용 조합이 올바르지 않거나 지원 범위 밖 위치(EMOTION-014)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증할 수 없음"),
            @ApiResponse(responseCode = "404", description = "녹음 업로드가 없거나 해당 기기의 업로드가 아님, 또는 사용할 수 없는 그룹"),
            @ApiResponse(responseCode = "409", description = "기명 등록에 필요한 닉네임 미설정(DEVICE-010), 요청 식별자 충돌 또는 녹음 미완료·이미 사용됨"),
            @ApiResponse(responseCode = "429", description = "같은 기기가 1초 안에 다시 생성을 요청함",
                    headers = @Header(name = "Retry-After", description = "다시 시도하기까지 기다려야 하는 초입니다.",
                            schema = @Schema(type = "string", example = "1")),
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "녹음 확인 기능을 사용할 수 없거나 서버의 지역 분류 준비가 완료되지 않음(EMOTION-013)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<EmotionCreateResponse> saveV3(@Valid EmotionV3CreateRequest request,
            @Parameter(hidden = true) UUID devicePublicId);


    @Operation(summary = "감정 상세 조회", description = """
            감정 정보와 여섯 이모지 코드별 전체 선택 수 및 인증된 기기의 선택 여부를 함께 반환합니다.
            properties.nickname은 익명 감정이면 '익명', 기명 감정이면 작성 기기의 현재 닉네임입니다.
            기기 닉네임 수정은 기존 기명 감정에도 반영되며, 기기 닉네임이 없으면 '익명'으로 표시합니다.
            properties.isMine은 인증된 기기가 작성했는지 나타내며, 수정·삭제의 서버 소유권 검사를 대체하지 않습니다.
            조회 기간 제한은 없으며 삭제되거나 인증된 기기가 차단한 감정·작성자의 감정은 반환하지 않습니다.
            녹음이 있으면 1시간 동안 유효한 audio.playbackUrl과 audio.expiresAt을 반환합니다. URL 만료 시 상세를 다시 조회합니다.
            """, security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "감정 상세 조회 성공",
                    headers = @Header(name = "Cache-Control", description = "기기별 응답을 개인 캐시에 저장할 수 있지만 재사용 전 서버 확인이 필요합니다.",
                            schema = @Schema(type = "string", example = "no-cache, private")),
                    content = @Content(mediaType = "application/geo+json",
                            schema = @Schema(implementation = EmotionDetailResponse.class))),
            @ApiResponse(responseCode = "400", description = "감정 ID가 올바르지 않음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증할 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "녹음 재생 URL을 발급할 수 없음"),
            @ApiResponse(responseCode = "404", description = "감정이 없거나 삭제·차단됨",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<EmotionDetailResponse> findById(
            @Parameter(description = "감정 ID", example = "42", schema = @Schema(minimum = "1"))
            @Min(value = 1, message = "감정 ID는 1 이상이어야 합니다.") Long emotionId,
            @Parameter(hidden = true) UUID devicePublicId
    );

    @Operation(summary = "본인 감정 수정", description = """
            작성 기기의 감정만 기한 없이 수정합니다. state와 contentType은 필수입니다.
            상태·메모·녹음·그룹 스탬프를 수정하며 위치·각도·작성 시각·등록 당시의 익명 선택은 바뀌지 않습니다.
            기기 닉네임은 PUT /api/v3/devices/me/nickname으로 별도 수정합니다. 수정한 이름은 기존 기명 감정에도 반영됩니다.
            contentType=NONE이면 내용을 제거합니다. MEMO이면 memo로 바꾸며 null·공백은 내용 없음입니다.
            AUDIO이면 audioUploadId로 새 녹음을 연결합니다. audioUploadId가 null이면 기존 녹음을 유지합니다.
            groupId가 null이면 그룹 스탬프를 제거합니다. 현재 groupId를 보내면 기존 선택을 유지합니다.
            새 그룹 선택은 현재 소속된 그룹만 가능합니다. 수정 완료 후 204를 반환합니다.
            """, security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "수정 완료"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 또는 유지할 기존 녹음이 없음"),
            @ApiResponse(responseCode = "401", description = "인증할 수 없음"),
            @ApiResponse(responseCode = "404", description = "감정이 없거나 본인 글이 아니거나 삭제됨, 또는 사용할 수 없는 그룹·업로드"),
            @ApiResponse(responseCode = "409", description = "녹음 미완료 또는 다른 감정에서 사용한 업로드"),
            @ApiResponse(responseCode = "503", description = "녹음 업로드를 확인할 수 없음")
    })
    ResponseEntity<Void> update(
            @Min(1) Long emotionId,
            @Valid EmotionUpdateRequest request,
            @Parameter(hidden = true) UUID devicePublicId
    );

    @Operation(summary = "본인 감정 삭제", description = """
            작성 기기의 감정만 소프트 삭제합니다. 삭제된 감정은 지도·목록·상세에 표시하지 않습니다.
            본인 글을 다시 삭제해도 204를 반환합니다. 기록과 신고 이력은 보존하며 녹음 파일은 즉시 삭제하지 않습니다.
            """, security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 완료 또는 이미 삭제됨"),
            @ApiResponse(responseCode = "400", description = "감정 ID가 올바르지 않음"),
            @ApiResponse(responseCode = "401", description = "인증할 수 없음"),
            @ApiResponse(responseCode = "404", description = "감정이 없거나 본인 글이 아님")
    })
    ResponseEntity<Void> delete(
            @Min(1) Long emotionId,
            @Parameter(hidden = true) UUID devicePublicId
    );

    @Operation(summary = "감정 이모지 선택", description = """
            인증된 기기가 선택한 이모지를 저장합니다. 같은 요청을 다시 보내도 한 번만 저장합니다.
            요청 본문 없이 경로의 영문 이모지 코드를 사용합니다.
            """, security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "선택 완료 또는 이미 선택됨"),
            @ApiResponse(responseCode = "400", description = "감정 ID나 이모지 코드가 올바르지 않음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증할 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "감정이 없거나 삭제·차단됨",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> select(
            @Parameter(description = "감정 ID", example = "42", schema = @Schema(minimum = "1"))
            @Min(value = 1, message = "감정 ID는 1 이상이어야 합니다.") Long emotionId,
            @Parameter(description = "이모지 코드", example = "HEART") EmojiType emojiType,
            @Parameter(hidden = true) UUID devicePublicId
    );

    @Operation(summary = "감정 이모지 선택 취소", description = """
            인증된 기기의 선택을 취소합니다. 선택하지 않은 상태에서 다시 요청해도 취소 상태를 유지합니다.
            요청 본문 없이 경로의 영문 이모지 코드를 사용합니다.
            """, security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "취소 완료 또는 이미 선택되지 않음"),
            @ApiResponse(responseCode = "400", description = "감정 ID나 이모지 코드가 올바르지 않음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증할 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "감정이 없거나 삭제·차단됨",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> cancel(
            @Parameter(description = "감정 ID", example = "42", schema = @Schema(minimum = "1"))
            @Min(value = 1, message = "감정 ID는 1 이상이어야 합니다.") Long emotionId,
            @Parameter(description = "이모지 코드", example = "HEART") EmojiType emojiType,
            @Parameter(hidden = true) UUID devicePublicId
    );
}
