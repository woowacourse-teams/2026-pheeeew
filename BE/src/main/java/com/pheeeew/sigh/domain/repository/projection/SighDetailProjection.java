package com.pheeeew.sigh.domain.repository.projection;

import com.pheeeew.sigh.domain.Sigh;

public interface SighDetailProjection {

    Sigh getSigh();

    boolean getLiked();
}
