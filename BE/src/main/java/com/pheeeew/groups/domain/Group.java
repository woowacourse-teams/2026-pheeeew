package com.pheeeew.groups.domain;

import static com.pheeeew.groups.exception.GroupErrorCode.GROUP_INVITE_CODE_REQUIRED;

import com.pheeeew.common.domain.BaseEntity;
import com.pheeeew.groups.exception.GroupException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "groups")
@Entity
public class Group extends BaseEntity {

    private static final int MAX_NAME_LENGTH = 10;
    private static final int MAX_DESCRIPTION_LENGTH = 100;
    private static final int INVITE_CODE_LENGTH = 6;
    private static final int MAX_JOIN_TYPE_LENGTH = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId;

    @Column(nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    @Column(length = MAX_DESCRIPTION_LENGTH)
    private String description;

    @Column(name = "invite_code", nullable = false, length = INVITE_CODE_LENGTH)
    private String inviteCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "join_type", nullable = false, length = MAX_JOIN_TYPE_LENGTH)
    private GroupJoinType joinType;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Builder
    private Group(String name, String description, String inviteCode, GroupJoinType joinType) {
        this.publicId = UUID.randomUUID();
        this.name = Objects.requireNonNull(name);
        this.description = description;
        this.inviteCode = Objects.requireNonNull(inviteCode);
        this.joinType = Objects.requireNonNull(joinType);
    }

    public void rename(String name, String description) {
        this.name = Objects.requireNonNull(name);
        this.description = description;
    }

    public void reissueInviteCode(String inviteCode) {
        this.inviteCode = Objects.requireNonNull(inviteCode);
    }

    public void changeJoinType(GroupJoinType joinType) {
        this.joinType = Objects.requireNonNull(joinType);
    }

    public void delete(Instant deletedAt) {
        this.deletedAt = Objects.requireNonNull(deletedAt);
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isPublic() {
        return joinType == GroupJoinType.PUBLIC;
    }

    public void validateCodeFreeJoin() {
        if (!isPublic()) {
            throw new GroupException(GROUP_INVITE_CODE_REQUIRED);
        }
    }
}
