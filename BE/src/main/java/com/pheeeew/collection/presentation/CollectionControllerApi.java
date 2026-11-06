package com.pheeeew.collection.presentation;

import com.pheeeew.collection.presentation.dto.CollectionCalendarRequest;
import com.pheeeew.collection.presentation.dto.CollectionCardDetailResponse;
import com.pheeeew.collection.presentation.dto.CollectionCardResponse;
import com.pheeeew.collection.presentation.dto.CollectionClaimRequest;
import com.pheeeew.collection.presentation.dto.CollectionDailyRequest;
import com.pheeeew.collection.presentation.dto.CollectionDailyResponse;
import com.pheeeew.collection.presentation.dto.CollectionHistoryRequest;
import com.pheeeew.collection.presentation.dto.CollectionHistoryResponse;
import com.pheeeew.collection.presentation.dto.CollectionTodayResponse;
import com.pheeeew.common.exception.ErrorResponse;
import com.pheeeew.common.presentation.dto.CursorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;

@Tag(name = "컬렉션", description = "인증된 기기의 컬렉션 진행도 조회 및 카드 수령 API")
public interface CollectionControllerApi {

    @Operation(summary = "컬렉션 날짜 상세 조회", description = """
            date는 매 페이지 필수인 0001~9999년의 yyyy-MM-dd KST 날짜입니다.
            오늘·과거는 기간 제한 없이 조회하며, KST 오늘보다 미래인 날짜는 400 COMMON-001로 거부합니다.
            인증된 기기 기준으로 조회하고 다른 기기의 식별자는 요청받지 않습니다.

            date, card, pressCount, emotions를 한 응답으로 반환합니다.
            card는 해당 날짜의 본인 카드 상세이며 미발급이면 null입니다. 카드가 없어도 정상 조회합니다.
            pressCount는 모든 감정의 서버 저장 프레스 합계이며 카드 유무·감정 목록 개수와 무관합니다.
            카드 내부의 pressCount도 최상위와 같은 합계입니다. 카드·프레스 요약은 매 요청의 현재 저장 상태입니다.
            활동이 없는 날은 200, card=null, pressCount=0, emotions.items=[]입니다. 조회로 카드를 발급하지 않습니다.

            emotions는 items, hasNext, nextCursor이며 작성 시각·ID 내림차순으로 최대 50개씩 반환합니다.
            해당 KST 날짜 00:00 이상, 다음 날 00:00 미만에 본인이 등록한 다섯 감정의 NONE·MEMO·AUDIO를 모두 포함합니다.
            삭제·차단은 매 페이지 반영하며, 공감은 각 항목에 HEART, LAUGH, CRY, DIZZY, RAGE, SKULL 순서로 반환합니다.
            각 공감의 count는 전체 선택 수, selected는 인증 기기의 선택 여부이며 기록이 없으면 0과 false입니다.

            다음 페이지에는 같은 date와 emotions.nextCursor를 cursor로 전달합니다.
            변조되거나 다른 기기·날짜·용도의 커서는 400입니다.
            최초 조회 이후 작성한 감정은 후속 페이지에 끼워 넣지 않으며 새로고침에서는 cursor를 생략합니다.
            최초 조회 전 작성됐지만 저장이 늦어진 감정은 아직 읽지 않은 위치에 포함될 수 있습니다.
            공감 수·카드·프레스는 커서로 고정하지 않습니다. 마지막·빈 페이지는 hasNext=false, nextCursor=null입니다.
            서명 키가 교체되어 커서가 무효화되면 첫 페이지부터 재조회합니다.

            녹음 재생 URL은 1시간 유효하며 만료 시 재조회합니다. 응답은 no-store입니다.
            예: GET /api/v1/collection-cards/daily?date=2026-11-06
            다음 페이지: GET /api/v1/collection-cards/daily?date=2026-11-06&cursor={emotions.nextCursor}
            """, security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "날짜별 카드·프레스·감정. 미발급·빈 날짜도 정상 응답"),
            @ApiResponse(responseCode = "400", description = "COMMON-001: 날짜 누락·형식·지원 범위 오류 또는 미래 날짜. COLLECTION-007: 커서 오류"),
            @ApiResponse(responseCode = "401", description = "유효한 기기 인증 없음"),
            @ApiResponse(responseCode = "503", description = "COLLECTION-004: 발급된 카드의 자산 준비 중 또는 EMOTION-009: 녹음 URL 발급 불가")
    })
    ResponseEntity<CollectionDailyResponse> findDaily(
            @Parameter(hidden = true) UUID devicePublicId,
            @ParameterObject CollectionDailyRequest request
    );

    @Operation(summary = "컬렉션 캘린더 월 조회", description = """
            month는 0001~9999년의 yyyy-MM 형식으로 필수 전달합니다.
            인증된 기기의 한국 시간 기준 카드 날짜가 해당 월인 카드를 날짜 오름차순으로 모두 반환합니다.
            오늘 발급한 카드도 포함합니다. 미발급 날짜의 항목은 만들지 않으며 빈 달은 빈 items를 반환합니다.
            각 항목은 카드 상세와 같은 이미지와 해당 날짜 모든 감정의 최신 스탬프·프레스 합계를 포함합니다.
            한 달은 최대 31장이므로 페이지 이동 없이 hasNext=false, nextCursor=null을 반환합니다.
            조회는 발급이나 재추첨을 수행하지 않으며 응답은 no-store입니다.
            """, security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "월별 카드 목록. 빈 달도 200"),
            @ApiResponse(responseCode = "400", description = "month 누락 또는 잘못된 형식·범위"),
            @ApiResponse(responseCode = "401", description = "유효한 기기 인증 없음"),
            @ApiResponse(responseCode = "503", description = "COLLECTION-004: 카드 자산 준비 중")
    })
    ResponseEntity<CursorResponse<CollectionCardDetailResponse>> findCalendar(
            @Parameter(hidden = true) UUID devicePublicId,
            @ParameterObject CollectionCalendarRequest request
    );

    @Operation(summary = "수령한 컬렉션 카드 목록 조회", description = """
            인증된 기기가 수령한 오늘·과거 카드를 카드 날짜·내부 식별자 내림차순으로 조회합니다.
            첫 요청의 한국 날짜 당일까지 포함하며 페이지 이동 중 날짜 상한과 감정 필터를 유지합니다.
            /today는 오늘 진행도·수령 여부를 조회합니다. 수령 직후·자정 전환·새로고침은 커서를 생략합니다.
            기존 커서는 저장된 날짜 범위를 유지합니다.
            최상위 nickname은 현재 기기 닉네임이며 미설정이면 null입니다. 카드별로 이름을 저장하지 않습니다.
            counts는 감정 필터·커서 위치와 무관한 같은 날짜 범위의 전체·5감정 보유 장수입니다.
            빈 목록에도 nickname과 counts를 반환하며, 목록과 개수는 같은 SQL 조회 시점을 사용합니다.

            늦게 저장된 카드가 이미 지나간 위치에 있으면 새로고침해야 하며 아직 읽지 않은 위치면 다음 페이지에 포함될 수 있습니다.

            카드 조합은 고정이지만 횟수는 각 요청 시점의 최신 집계입니다. 카드 집합이나 횟수의 스냅샷을 고정하지 않습니다.
            횟수는 카드 소유 기기·카드 날짜의 다섯 감정 합계이며, 목록의 감정 필터와 무관합니다.
            해당 날짜의 스탬프 또는 프레스 집계가 없으면 그 횟수를 0으로 반환합니다.

            마지막 페이지는 hasNext=false, nextCursor=null입니다. 조회는 발급이나 재추첨을 수행하지 않습니다.
            """, security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공. 항목은 상세 조회와 같은 응답이며 빈 목록도 200입니다."),
            @ApiResponse(responseCode = "400", description = "잘못된 emotion·limit 또는 COLLECTION-007: 잘못된 커서·필터 불일치, COLLECTION-008: 페이지 크기 범위 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "유효한 기기 인증 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "COLLECTION-004: 카드 자산·이미지 주소 설정 미비",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<CollectionHistoryResponse> findHistory(
            @Parameter(hidden = true) UUID devicePublicId,
            @ParameterObject CollectionHistoryRequest request,
            @Parameter(hidden = true) MultiValueMap<String, String> parameters
    );

    @Operation(
            summary = "컬렉션 카드 상세 조회",
            description = """
                    본인 기기가 수령한 오늘 또는 과거 카드를 조회합니다.
                    카드 조합은 수령 당시 값이며 횟수는 카드 소유 기기·카드 날짜의 다섯 감정을 합한 조회 시점 누적 집계입니다.
                    늦게 저장된 활동은 다음 조회에 반영되며 자정 정산이나 최종 횟수 고정은 하지 않습니다.
                    카드 감정에 활동이 없어도 다른 감정의 활동을 합산하며, 해당 날짜의 스탬프 또는 프레스 집계가 없으면 그 횟수를 0으로 반환합니다.
                    이미지 URL 3개로 앱에서 카드를 조합합니다. 조회는 카드를 생성하거나 재추첨하지 않습니다.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 카드 UUID",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "유효한 기기 인증 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "COLLECTION-005: 없는 카드 또는 다른 기기 카드",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "COLLECTION-004: 카드 자산·이미지 주소 설정 미비",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<CollectionCardDetailResponse> findDetail(
            @Parameter(hidden = true) UUID devicePublicId,
            @Parameter(description = "수령한 카드의 공개 식별자") UUID cardId
    );

    @Operation(
            summary = "오늘 컬렉션 진행도 조회",
            description = """
                    인증된 기기의 한국 시간 기준 오늘 진행도를 반환합니다. 날짜와 기기는 서버가 결정합니다.

                    - 다섯 감정을 FRUSTRATED, IRRITATED, EXHAUSTED, DISCOURAGED, ANGRY 순서로 반환합니다.
                    - 발급 여부와 관계없이 그 감정에 활동이 없으면 횟수는 0입니다. 수령 목표는 오늘 다섯 감정 합계로 스탬프 1회, 프레스 100회입니다.
                    - 프레스는 서버가 인정하여 저장한 횟수이며, 목표를 초과해도 실제 횟수를 유지합니다.
                    - 부족분은 목표에서 현재 횟수를 뺀 값이며 최소 0입니다.
                    - card는 수령 API와 같은 카드·이미지 URL 정보이며 미발급 상태이면 null입니다.
                    - 진행도와 카드는 같은 시점의 저장 상태를 읽습니다. 수령 후에도 횟수는 갱신되고 카드와 그림은 유지됩니다.
                    - 최상위 stamps, presses 는 다섯 감정을 합한 오늘 진행도이며 수령 조건입니다. items 의 감정별 target, remaining 은 참고용이며 수령 조건이 아닙니다.
                    - claimed 는 오늘 카드를 받았는지, claimable 은 아직 받지 않았고 합산 조건을 채웠는지입니다. 하루 한 장이므로 claimed 가 true 이면 claimable 은 false 입니다.
                    - 조회로 활동이나 카드를 생성하지 않습니다.

                    조회 중 날짜가 바뀌면 새 날짜로 다시 조회합니다. 날짜가 계속 바뀌어 503이 반환되면 재시도합니다.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 정보 없음·유효하지 않음 또는 인증된 기기가 존재하지 않음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "COLLECTION-001: 날짜 변경 반복 또는 COLLECTION-004: 수령 카드의 자산·이미지 주소 설정 미비",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<CollectionTodayResponse> findToday(
            @Parameter(hidden = true) UUID devicePublicId
    );

    @Operation(
            summary = "컬렉션 카드 수령",
            description = """
                    인증된 기기가 한국 시간 기준 오늘 카드 한 장을 직접 수령합니다. 카드 감정은 요청의 state 이며 다섯 감정 중 아무것이나 고를 수 있습니다.

                    - 오늘 이 기기의 스탬프 합계 1회 이상, 프레스 합계 100회 이상(다섯 감정 합)이어야 합니다. 요청한 state 의 활동 여부는 보지 않습니다. 서버가 실제 저장된 활동으로 재확인합니다.
                    - 기기마다 하루에 신규 카드 한 장만 발급합니다. 오늘 이미 카드를 받았으면 다른 state 요청은 409 COLLECTION-009 로 거절하며 추첨 조합을 소비하지 않습니다.
                    - 같은 date, state 로 다시 요청하면 기존 카드를 그대로 반환합니다. 이미 수령한 카드는 날짜가 지나도 재요청할 수 있습니다.
                    - 미발급 카드는 당일에만 수령할 수 있습니다. 자정이 지나면 전날 미수령 카드는 발급하지 않습니다.
                    - 같은 기기의 수령 요청은 감정과 관계없이 하나씩 처리합니다. 조건 확인 후 발급 직전에 당일 여부를 최종 판정합니다. 자정 전에 최종 확인된 수령은 응답이 다음 날 도착할 수 있습니다.
                    - 카드 수령 실패로 이미 저장한 감정·프레스가 취소되지는 않습니다.
                    - 응답을 받지 못하면 같은 date·state로 다시 요청합니다. 기기는 인증 정보로 결정합니다.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "신규 수령 또는 기존 수령 결과 반환"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 또는 COLLECTION-002: 미발급 카드의 수령 날짜가 당일이 아님",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 정보 없음·유효하지 않음 또는 인증된 기기가 존재하지 않음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "COLLECTION-003: 오늘 스탬프 합계, 프레스 합계 조건 미달 또는 COLLECTION-009: 오늘 이미 다른 감정의 카드를 수령함",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "COLLECTION-004: 카드 자산 준비 중. 당일 재시도 필요",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<CollectionCardResponse> claim(
            @Parameter(hidden = true) UUID devicePublicId,
            CollectionClaimRequest request
    );
}
