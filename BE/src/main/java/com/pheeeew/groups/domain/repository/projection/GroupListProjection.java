package com.pheeeew.groups.domain.repository.projection;

import com.pheeeew.groups.domain.GroupRole;
import com.pheeeew.groups.domain.StampFrame;
import java.util.UUID;

public interface GroupListProjection {

    UUID getGroupPublicId();

    String getName();

    String getDescription();

    String getInviteCode();

    GroupRole getRole();

    long getMemberCount();

    String getStampText();

    String getStampTextColor();

    String getStampBackgroundColor();

    StampFrame getStampFrame();
}
