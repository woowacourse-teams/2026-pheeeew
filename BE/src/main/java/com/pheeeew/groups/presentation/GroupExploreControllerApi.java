package com.pheeeew.groups.presentation;

import com.pheeeew.common.exception.ErrorResponse;
import com.pheeeew.common.presentation.dto.CursorResponse;
import com.pheeeew.groups.presentation.dto.GroupExploreResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.MediaType;

@Tag(name = "그룹 탐색", description = "가입하지 않은 그룹까지 둘러보고 이름으로 찾습니다.")
public interface GroupExploreControllerApi {

    @Operation(
            summary = "전체 그룹 목록과 그룹명 검색",
            description = """
                    **가입 여부와 무관하게 전체 그룹을 둘러봅니다.** 공개 그룹과 비공개 그룹을 모두 담고
                    삭제된 그룹만 제외합니다.

                    ### /search 와 무엇이 다른가

                    | | GET /api/v2/groups/explore | GET /api/v2/groups/search |
                    | --- | --- | --- |
                    | 찾는 방법 | 그룹 **이름** 부분 일치. 생략하면 전체 | **초대 코드** 정확히 일치 |
                    | 결과 | 목록. 커서로 이어 받습니다 | 그룹 **하나** |
                    | 없을 때 | 빈 목록과 `hasNext: false` | **404** |
                    | 쓰는 곳 | 어떤 그룹이 있는지 둘러볼 때 | 받은 코드가 어느 그룹인지 확인할 때 |
                    | 요청 수 제한 | 없습니다 | 있습니다 |

                    `/search` 는 코드를 아는 사람만 그룹 하나를 미리 보는 입구이고, 이 API 는 누구나 카탈로그를
                    훑는 목록입니다. **초대 코드로는 이 API 를 검색할 수 없고, 이 API 는 초대 코드를 돌려주지도
                    않습니다.** 들어가기 전 미리보기는 `/search`, 가입은 `POST /api/v2/groups/join` 또는
                    `POST /api/v2/groups/{groupId}/members/me` 입니다.

                    ### 검색

                    - `keyword` 가 **그룹 이름에 들어 있으면** 찾습니다. 앞뒤 공백은 서버가 떼고 대소문자는 가리지
                      않습니다. `모임` 으로 `한숨모임` 이 나옵니다.
                    - `keyword` 를 생략하거나 공백만 보내면 **전체 목록**입니다.
                    - **이름만 찾습니다.** 설명 본문은 찾지 않고 초성(`ㅎㅅ`)도 찾지 않습니다.
                    - 그룹 이름이 10자 제한이라 11자 이상 `keyword` 는 빈 목록입니다.
                    - `%`, `_` 는 특별한 뜻이 없는 평범한 글자로 취급합니다.

                    ### 페이지 이동

                    - **최근에 만들어진 그룹이 먼저** 나오고, 같은 시각이면 나중에 들어온 그룹이 먼저입니다.
                    - 다음 페이지에는 이전 응답의 `nextCursor` 를 `cursor` 로 보냅니다. **커서는 해석하거나 고치지
                      않고 그대로 씁니다.**
                    - **`cursor` 를 보낼 때 `keyword` 를 함께 보냅니다.** `keyword` 를 바꾸면 `cursor` 를 빼고
                      첫 페이지부터 조회합니다.
                    - `size` 는 1 에서 50 이고 생략하면 20 입니다. 범위를 벗어나면 400 입니다.
                    - `hasNext` 가 `false` 면 마지막 페이지이고 `nextCursor` 는 `null` 입니다.
                    - 조회하는 동안 만들어진 그룹은 뒤 페이지에 끼어들지 않습니다. 삭제된 그룹은 그 페이지부터
                      바로 빠집니다.
                    - 감정 목록이나 차단 목록에서 받은 커서는 쓸 수 없습니다.

                    ### 응답

                    - `items`, `hasNext`, `nextCursor` 를 돌려줍니다.
                    - `memberCount` 는 **지금 속해 있는 멤버 수**입니다. 나간 멤버는 세지 않습니다.
                    - `joined` 는 **요청한 기기가 이미 들어가 있는 그룹인지**입니다. `가입하기` 버튼을 가리는 데
                      씁니다. 나간 그룹은 `false` 입니다.
                    - **초대 코드는 담지 않습니다.** 비공개 그룹도 목록에는 나오지만 들어가려면 코드가 필요합니다.
                    - 멤버 명단은 담지 않습니다. 멤버만 `GET /api/v2/groups/{groupId}/members` 로 볼 수 있습니다.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "그룹 목록과 다음 커서"),
            @ApiResponse(responseCode = "400", description = "커서를 쓸 수 없거나 size 가 범위를 벗어남",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code": "GROUP-010", "message": "그룹 목록 커서를 사용할 수 없습니다."}
                                    """))),
            @ApiResponse(responseCode = "401", description = "인증할 수 없음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code": "DEVICE-004", "message": "인증 정보를 사용할 수 없습니다."}
                                    """)))
    })
    CursorResponse<GroupExploreResponse> findAll(
            @Parameter(description = "그룹 이름 부분 일치 검색어. 생략하면 전체 목록입니다. 앞뒤 공백은 서버가 뗍니다.",
                    example = "모임")
            String keyword,

            @Parameter(description = "다음 페이지 조회에 사용할 서버 발급 커서. 첫 페이지에서는 생략합니다.",
                    example = "opaque-cursor")
            String cursor,

            @Parameter(description = "한 페이지 항목 수. 1 이상 50 이하이며 생략하면 20 입니다.", example = "20")
            @Min(value = 1, message = "한 페이지 항목 수는 1 이상이어야 합니다.")
            @Max(value = 50, message = "한 페이지 항목 수는 50 이하여야 합니다.")
            int size,

            @Parameter(hidden = true) UUID devicePublicId
    );
}
