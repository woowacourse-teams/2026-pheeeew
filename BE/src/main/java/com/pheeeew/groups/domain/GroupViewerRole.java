package com.pheeeew.groups.domain;

public enum GroupViewerRole {
    OWNER,
    MEMBER,
    NONE;

    public static GroupViewerRole from(GroupRole role) {
        return switch (role) {
            case OWNER -> OWNER;
            case MEMBER -> MEMBER;
        };
    }
}
