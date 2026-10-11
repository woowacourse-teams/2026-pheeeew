package com.pheeeew.groups.presentation;

import com.pheeeew.auth.presentation.annotation.CurrentDevice;
import com.pheeeew.common.presentation.dto.CursorResponse;
import com.pheeeew.groups.application.GroupService;
import com.pheeeew.groups.application.dto.GroupMemberListResult;
import com.pheeeew.groups.application.dto.GroupResult;
import com.pheeeew.groups.application.InviteCodeGenerator;
import com.pheeeew.groups.presentation.dto.GroupJoinRequest;
import com.pheeeew.groups.presentation.dto.GroupMemberResponse;
import com.pheeeew.groups.presentation.dto.GroupPreviewResponse;
import com.pheeeew.groups.presentation.dto.GroupResponse;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v2/groups")
@RestController
public class GroupMemberController implements GroupMemberControllerApi {

    private final GroupService groupService;

    @Override
    @GetMapping("/search")
    public GroupPreviewResponse findByInviteCode(
            @RequestParam String inviteCode
    ) {
        return GroupPreviewResponse.from(
                groupService.findByInviteCode(InviteCodeGenerator.normalize(inviteCode))
        );
    }

    @Override
    @PostMapping("/join")
    public ResponseEntity<GroupResponse> joinByInviteCode(
            @RequestBody GroupJoinRequest request,
            @CurrentDevice UUID devicePublicId
    ) {
        GroupResult result = groupService.joinByInviteCode(devicePublicId, request.inviteCode());
        GroupResponse body = GroupResponse.from(result);

        return ResponseEntity.created(URI.create("/api/v2/groups/" + body.groupId())).body(body);
    }

    @Override
    @PostMapping("/{groupId}/members/me")
    public ResponseEntity<GroupResponse> joinPublicGroup(
            @PathVariable UUID groupId,
            @CurrentDevice UUID devicePublicId
    ) {
        GroupResponse body = GroupResponse.from(groupService.joinPublicGroup(groupId, devicePublicId));

        return ResponseEntity.created(URI.create("/api/v2/groups/" + groupId + "/members/me")).body(body);
    }

    @Override
    @DeleteMapping("/{groupId}/members/me")
    public ResponseEntity<Void> leave(
            @PathVariable UUID groupId,
            @CurrentDevice UUID devicePublicId
    ) {
        groupService.leave(groupId, devicePublicId);

        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping("/{groupId}/members")
    public CursorResponse<GroupMemberResponse> findMembers(
            @PathVariable UUID groupId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size,
            @CurrentDevice UUID devicePublicId
    ) {
        GroupMemberListResult result = groupService.findMembers(groupId, devicePublicId, cursor, size);

        List<GroupMemberResponse> items = result.items().stream()
                .map(GroupMemberResponse::from)
                .toList();

        return CursorResponse.of(items, result.hasNext(), result.nextCursor());
    }
}
