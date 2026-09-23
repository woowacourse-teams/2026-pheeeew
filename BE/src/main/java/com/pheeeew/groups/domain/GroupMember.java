package com.pheeeew.groups.domain;

import com.pheeeew.common.domain.BaseEntity;
import com.pheeeew.device.domain.Device;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "group_members")
@Entity
public class GroupMember extends BaseEntity {

    private static final int MAX_ROLE_LENGTH = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false, updatable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false, updatable = false)
    private Device device;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = MAX_ROLE_LENGTH)
    private GroupRole role;

    @Column(name = "left_at")
    private Instant leftAt;

    @Builder
    private GroupMember(Group group, Device device, GroupRole role) {
        this.publicId = UUID.randomUUID();
        this.group = Objects.requireNonNull(group);
        this.device = Objects.requireNonNull(device);
        this.role = Objects.requireNonNull(role);
    }

    public boolean isOwner() {
        return role == GroupRole.OWNER;
    }
}
