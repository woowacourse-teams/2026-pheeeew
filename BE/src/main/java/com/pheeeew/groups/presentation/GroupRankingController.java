package com.pheeeew.groups.presentation;

import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.groups.application.GroupRankingService;
import com.pheeeew.auth.presentation.annotation.CurrentDevice;
import com.pheeeew.groups.presentation.dto.GroupPressRankingResponse;
import java.util.UUID;
import com.pheeeew.groups.presentation.dto.GroupRankingResponse;
import com.pheeeew.groups.presentation.dto.GroupStatePressRankingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @Override
    @GetMapping("/press-rankings")
    public GroupPressRankingResponse findPressRanking(
            @RequestParam(defaultValue = "0") int weeksAgo,
            @CurrentDevice UUID devicePublicId
    ) {
        return GroupPressRankingResponse.from(
                groupRankingService.findPressRanking(devicePublicId, weeksAgo)
        );
    }

    @Override
    @GetMapping("/press-rankings/states/{state}")
    public GroupStatePressRankingResponse findPressRankingByState(
            @PathVariable EmotionState state,
            @RequestParam(defaultValue = "0") int weeksAgo,
            @CurrentDevice UUID devicePublicId
    ) {
        return GroupStatePressRankingResponse.from(
                groupRankingService.findPressRankingByState(devicePublicId, state, weeksAgo)
        );
    }
}
