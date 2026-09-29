package com.pheeeew.groups.presentation;

import com.pheeeew.groups.application.GroupRankingService;
import com.pheeeew.groups.presentation.dto.GroupRankingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v2/groups")
@RestController
public class GroupRankingController implements GroupRankingControllerApi {

    private final GroupRankingService groupRankingService;

    @Override
    @GetMapping("/rankings")
    public GroupRankingResponse findGroupRanking(
            @RequestParam(defaultValue = "0") int weeksAgo
    ) {
        return GroupRankingResponse.from(groupRankingService.findGroupRanking(weeksAgo));
    }
}
