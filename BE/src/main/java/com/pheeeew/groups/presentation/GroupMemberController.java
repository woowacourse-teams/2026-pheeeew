package com.pheeeew.groups.presentation;

import com.pheeeew.auth.presentation.annotation.CurrentDevice;
import com.pheeeew.groups.application.GroupService;
import com.pheeeew.groups.application.dto.GroupResult;
import com.pheeeew.groups.presentation.dto.GroupJoinRequest;
import com.pheeeew.groups.presentation.dto.GroupResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v2/groups")
@RestController
public class GroupMemberController implements GroupMemberControllerApi {

    private final GroupService groupService;

    @Override
    @PostMapping("/join")
    public ResponseEntity<GroupResponse> join(
            @Valid @RequestBody GroupJoinRequest request,
            @CurrentDevice UUID devicePublicId
    ) {
        GroupResult result = groupService.join(devicePublicId, request.inviteCode());
        GroupResponse body = GroupResponse.from(result);

        return ResponseEntity.created(URI.create("/api/v2/groups/" + body.groupId())).body(body);
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
}
