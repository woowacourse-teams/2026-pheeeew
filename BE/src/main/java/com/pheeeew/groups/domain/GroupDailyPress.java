package com.pheeeew.groups.domain;

import com.pheeeew.common.domain.BaseEntity;
import com.pheeeew.emotion.domain.EmotionState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "group_daily_presses")
@Entity
public class GroupDailyPress extends BaseEntity {

    private static final int MAX_STATE_LENGTH = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false, updatable = false)
    private Long groupId;

    @Column(name = "press_date", nullable = false, updatable = false)
    private LocalDate pressDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = MAX_STATE_LENGTH)
    private EmotionState state;

    @Column(name = "press_count", nullable = false)
    private long pressCount;
}
