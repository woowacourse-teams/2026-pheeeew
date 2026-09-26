package com.pheeeew.groups.presentation;

import com.pheeeew.auth.presentation.annotation.CurrentDevice;
import com.pheeeew.groups.application.GroupService;
import com.pheeeew.groups.application.dto.GroupResult;
import com.pheeeew.groups.presentation.dto.GroupDetailResponse;
import com.pheeeew.groups.application.dto.GroupStampCommand;
import com.pheeeew.groups.presentation.dto.GroupCreateRequest;
import com.pheeeew.groups.presentation.dto.GroupPressCountResponse;
import com.pheeeew.groups.presentation.dto.GroupPressRequest;
import com.pheeeew.groups.presentation.dto.GroupResponse;
import com.pheeeew.groups.presentation.dto.GroupStampRequest;
import com.pheeeew.groups.presentation.dto.GroupUpdateRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v2/groups")
@RestController
public class GroupController implements GroupControllerApi {

    private final GroupService groupService;

    @Override
    @PostMapping
    public ResponseEntity<GroupResponse> save(
            @Valid @RequestBody GroupCreateRequest request,
            @CurrentDevice UUID devicePublicId
    ) {
        GroupStampRequest stamp = request.stamp();
        GroupResult result = groupService.save(
                devicePublicId,
                request.name(),
                request.description(),
                GroupStampCommand.of(stamp.text(), stamp.textColor(), stamp.backgroundColor(), stamp.frame())
        );

        return ResponseEntity.created(URI.create("/api/v2/groups/" + result.publicId()))
                .body(GroupResponse.from(result));
    }

    @Override
    @GetMapping
    public List<GroupResponse> findMine(
            @CurrentDevice UUID devicePublicId
    ) {
        return groupService.findMine(devicePublicId).stream()
                .map(GroupResponse::from)
                .toList();
    }

    @Override
    @GetMapping("/{groupId}")
    public GroupDetailResponse findOne(
            @PathVariable UUID groupId,
            @CurrentDevice UUID devicePublicId
    ) {
        return GroupDetailResponse.from(groupService.findOne(groupId, devicePublicId));
    }

    @Override
    @PutMapping("/{groupId}")
    public GroupResponse update(
            @PathVariable UUID groupId,
            @Valid @RequestBody GroupUpdateRequest request,
            @CurrentDevice UUID devicePublicId
    ) {
        GroupStampRequest stamp = request.stamp();
        GroupResult result = groupService.update(
                groupId,
                devicePublicId,
                request.name(),
                request.description(),
                GroupStampCommand.of(stamp.text(), stamp.textColor(), stamp.backgroundColor(), stamp.frame())
        );

        return GroupResponse.from(result);
    }

    @Override
    @PostMapping("/{groupId}/presses")
    public GroupPressCountResponse press(
            @PathVariable UUID groupId,
            @Valid @RequestBody GroupPressRequest request,
            @CurrentDevice UUID devicePublicId
    ) {
        return GroupPressCountResponse.from(
                groupService.press(groupId, devicePublicId, request.state())
        );
    }

    @Override
    @PostMapping("/{groupId}/invite-code")
    public GroupResponse reissueInviteCode(
            @PathVariable UUID groupId,
            @CurrentDevice UUID devicePublicId
    ) {
        return GroupResponse.from(groupService.reissueInviteCode(groupId, devicePublicId));
    }

    @Override
    @DeleteMapping("/{groupId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID groupId,
            @CurrentDevice UUID devicePublicId
    ) {
        groupService.delete(groupId, devicePublicId);

        return ResponseEntity.noContent().build();
    }
}
