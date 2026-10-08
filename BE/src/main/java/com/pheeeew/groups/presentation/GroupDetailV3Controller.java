package com.pheeeew.groups.presentation;

import com.pheeeew.auth.presentation.annotation.CurrentDevice;
import com.pheeeew.groups.application.GroupService;
import com.pheeeew.groups.presentation.dto.GroupDetailV3Response;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v3/groups")
@RestController
public class GroupDetailV3Controller implements GroupDetailV3ControllerApi {

    private final GroupService groupService;

    @Override
    @GetMapping("/{groupId}")
    public GroupDetailV3Response findOne(
            @PathVariable UUID groupId,
            @CurrentDevice UUID devicePublicId
    ) {
        return GroupDetailV3Response.from(groupService.findDetailV3(groupId, devicePublicId));
    }
}
