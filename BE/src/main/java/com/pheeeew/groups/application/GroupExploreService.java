package com.pheeeew.groups.application;

import com.pheeeew.groups.application.dto.GroupCursor;
import com.pheeeew.groups.application.dto.GroupExploreResult;
import com.pheeeew.groups.application.dto.GroupExploreListResult;
import com.pheeeew.groups.domain.repository.GroupRepository;
import com.pheeeew.groups.domain.repository.projection.GroupExploreProjection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class GroupExploreService {

    private final GroupRepository groupRepository;

    public GroupExploreListResult findAll(UUID devicePublicId, String keyword, String encodedCursor, int size) {
        GroupCursor cursor = GroupCursorCodec.decodeExploreOrInitial(encodedCursor);

        List<GroupExploreProjection> projections = groupRepository.findExplorePage(
                devicePublicId,
                searchable(keyword),
                cursor.lastCreatedAt(),
                cursor.lastId(),
                size + 1
        );

        boolean hasNext = projections.size() > size;
        if (hasNext) {
            projections = projections.subList(0, size);
        }

        List<GroupExploreResult> items = projections.stream()
                .map(GroupExploreResult::from)
                .toList();

        return GroupExploreListResult.of(items, hasNext, nextCursor(items, hasNext));
    }

    private String searchable(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        return keyword.strip();
    }

    private String nextCursor(List<GroupExploreResult> items, boolean hasNext) {
        if (!hasNext) {
            return null;
        }
        GroupExploreResult last = items.getLast();

        return GroupCursorCodec.encodeExplore(last.createdAt(), last.id());
    }
}
