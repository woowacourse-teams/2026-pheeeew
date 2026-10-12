package com.pheeeew.groups.domain.repository.projection;

import java.time.Instant;
import java.util.UUID;

public interface GroupExploreProjection {

    UUID getGroupPublicId();

    String getName();

    String getDescription();

    String getJoinType();

    Instant getCreatedAt();

    long getId();

    long getMemberCount();

    boolean getJoined();

    String getStampText();

    String getStampTextColor();

    String getStampBackgroundColor();

    String getStampFrame();
}
