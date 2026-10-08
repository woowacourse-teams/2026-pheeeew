package com.pheeeew.groups.presentation;

import com.pheeeew.common.exception.ErrorResponse;
import com.pheeeew.groups.presentation.dto.GroupJoinRequest;
import com.pheeeew.groups.presentation.dto.GroupPreviewResponse;
import com.pheeeew.groups.presentation.dto.GroupResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "그룹 참여", description = "초대 코드로 그룹에 들어가고 나갑니다.")
public interface GroupMemberControllerApi {

    @Operation(
            summary = "초대 코드로 그룹 찾아보기",
            description = """
                    들어가기 전에 **어떤 그룹인지 미리 봅니다.** 참여하지 않습니다.

                    - 코드를 아는 사람만 볼 수 있습니다. 그룹 식별자로는 조회할 수 없습니다.
                    - 이름, 설명, 현재 인원수, 스탬프를 돌려줍니다. **초대 코드는 응답에 담지 않습니다.**
                    - 없는 코드이거나 삭제된 그룹이면 404 입니다.
                    - 대소문자와 혼동되는 글자를 참여와 똑같이 맞춰 읽습니다.
                      `I`, `L` 은 `1` 로, `O` 는 `0` 으로 바꿉니다.

                    **참여와 같은 요청 수 제한이 걸려 있습니다.** 코드를 무작위로 넣어 그룹을 훑는 것을 막기 위함입니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "찾음"),
            @ApiResponse(responseCode = "404", description = "없는 코드이거나 삭제된 그룹",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code": "GROUP-001", "message": "그룹을 찾을 수 없습니다."}
                                    """)))
    })
    GroupPreviewResponse findByInviteCode(String inviteCode);

    @Operation(
            summary = "초대 코드로 그룹 참여",
            description = """
                    초대 코드를 **본문에 담아** 보냅니다. 경로에 넣지 않는 이유는 접근 로그와 브라우저 기록에
                    그룹 입장 열쇠가 남기 때문입니다.

                    - **이미 속한 그룹이면 409** 입니다. 그룹장이 자기 그룹 코드로 요청해도 마찬가지입니다.
                      클라이언트가 재시도하거나 더블 탭했을 때도 409 가 오므로, 이 코드를 "이미 들어가 있음" 으로
                      처리해야 합니다.
                    - 소문자로 보내도 됩니다. 서버가 대문자로 맞춥니다.
                      **`I`, `L` 은 `1` 로, `O` 는 `0` 으로 바꿔 읽습니다.** 실제 코드에 없는 글자라 혼동을 흡수합니다.
                    - 없는 코드이거나 삭제된 그룹의 코드면 404 입니다.
                      코드가 재발급되었으면 **이전 코드는 즉시 무효**입니다.
                    - 나갔던 그룹에 다시 들어갈 수 있습니다. 이때 **예전에 그 그룹에 쓴 기록은 그대로 내 기록**입니다.
                    - 가입 가능한 그룹 수에 제한이 없습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "참여 성공"),
            @ApiResponse(responseCode = "404", description = "없는 코드이거나 삭제된 그룹",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code": "GROUP-001", "message": "그룹을 찾을 수 없습니다."}
                                    """))),
            @ApiResponse(responseCode = "409", description = "이미 속해 있음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code": "GROUP-007", "message": "이미 속해 있는 그룹입니다."}
                                    """)))
    })
    ResponseEntity<GroupResponse> join(
            GroupJoinRequest request,
            @Parameter(hidden = true) UUID devicePublicId
    );

    @Operation(
            summary = "그룹 나가기",
            description = """
                    요청한 기기를 그룹에서 뺍니다.

                    - 멤버가 아니면 403 입니다.
                    - **그룹장은 나갈 수 없습니다.** 409 이며, 위임 기능이 없으므로
                      다른 멤버가 모두 나간 뒤 그룹을 삭제하는 것이 그룹장이 빠지는 유일한 방법입니다.
                    - 나가도 **그동안 쓴 기록은 그룹에 남고 랭킹에도 계속 집계**됩니다.
                    - 멤버 행을 지우지 않고 나간 시각만 남기므로, 다시 들어오면 새 멤버십이 만들어집니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "나가기 성공"),
            @ApiResponse(responseCode = "403", description = "그룹 멤버가 아님",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code": "GROUP-008", "message": "그룹 멤버만 할 수 있습니다."}
                                    """))),
            @ApiResponse(responseCode = "404", description = "없는 그룹이거나 삭제된 그룹",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code": "GROUP-001", "message": "그룹을 찾을 수 없습니다."}
                                    """))),
            @ApiResponse(responseCode = "409", description = "그룹장은 나갈 수 없음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code": "GROUP-006", "message": "그룹장은 그룹을 나갈 수 없습니다."}
                                    """)))
    })
    ResponseEntity<Void> leave(
            @Parameter(description = "나갈 그룹의 공개 식별자", example = "5f2b1c84-9d0e-4a13-b6c7-2e8f0a4d7b19")
            UUID groupId,
            @Parameter(hidden = true) UUID devicePublicId
    );
}
