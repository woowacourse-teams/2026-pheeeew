package com.pheeeew.common.logging;

// 요청 값이 아닌 코드에 정의한 enum 상수로 계측 단계를 구분한다.
public interface RequestTimingStage {

    String name();
}
