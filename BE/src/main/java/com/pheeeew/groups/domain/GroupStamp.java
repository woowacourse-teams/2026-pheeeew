package com.pheeeew.groups.domain;

import com.pheeeew.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "group_stamps")
@Entity
public class GroupStamp extends BaseEntity {

    private static final int MAX_TEXT_LENGTH = 4;
    private static final int MAX_COLOR_LENGTH = 9;
    private static final int MAX_FRAME_LENGTH = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false, updatable = false)
    private Group group;

    @Column(nullable = false, length = MAX_TEXT_LENGTH)
    private String text;

    @Column(name = "text_color", nullable = false, length = MAX_COLOR_LENGTH)
    private String textColor;

    @Column(name = "background_color", nullable = false, length = MAX_COLOR_LENGTH)
    private String backgroundColor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = MAX_FRAME_LENGTH)
    private StampFrame frame;

    @Builder
    private GroupStamp(Group group, String text, String textColor, String backgroundColor, StampFrame frame) {
        this.group = Objects.requireNonNull(group);
        this.text = Objects.requireNonNull(text);
        this.textColor = Objects.requireNonNull(textColor);
        this.backgroundColor = Objects.requireNonNull(backgroundColor);
        this.frame = Objects.requireNonNull(frame);
    }
}
