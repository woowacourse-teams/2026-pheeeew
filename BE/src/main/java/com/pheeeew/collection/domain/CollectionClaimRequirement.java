package com.pheeeew.collection.domain;

public final class CollectionClaimRequirement {

    public static final long STAMP_TARGET = 1;
    public static final long PRESS_TARGET = 100;

    private CollectionClaimRequirement() {
    }

    public static boolean isMet(long stampCount, long pressCount) {
        return stampCount >= STAMP_TARGET && pressCount >= PRESS_TARGET;
    }
}
