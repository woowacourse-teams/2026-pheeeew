package com.pheeeew.groups.domain.repository.projection;

import com.pheeeew.groups.domain.StampFrame;
import java.util.UUID;

public interface GroupScoreProjection {

    UUID getGroupPublicId();

    String getName();

    String getStampText();

    String getStampTextColor();

    String getStampBackgroundColor();

    StampFrame getStampFrame();

    long getScore();
}
