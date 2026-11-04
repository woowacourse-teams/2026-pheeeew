package com.pheeeew.pressrace.v2.application;

/** 연결이 끝났을 때 구독을 정리한다. 여러 번 불러도 한 번만 반납한다. */
public interface PressStreamSubscription {

    void cancel();
}
