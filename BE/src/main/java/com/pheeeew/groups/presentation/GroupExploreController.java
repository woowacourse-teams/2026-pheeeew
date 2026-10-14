package com.pheeeew.groups.presentation;

import com.pheeeew.auth.presentation.annotation.CurrentDevice;
import com.pheeeew.common.presentation.dto.CursorResponse;
import com.pheeeew.groups.application.GroupExploreService;
import com.pheeeew.groups.application.dto.GroupExploreListResult;
import com.pheeeew.groups.presentation.dto.GroupExploreResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v2/groups")
@RestController
public class GroupExploreController implements GroupExploreControllerApi {

    private final GroupExploreService groupExploreService;

    @Override
    @GetMapping("/explore")
    public CursorResponse<GroupExploreResponse> findAll(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size,
            @CurrentDevice UUID devicePublicId
    ) {
        GroupExploreListResult result = groupExploreService.findAll(devicePublicId, keyword, cursor, size);

        List<GroupExploreResponse> items = result.items().stream()
                .map(GroupExploreResponse::from)
                .toList();

        return CursorResponse.of(items, result.hasNext(), result.nextCursor());
    }
}
