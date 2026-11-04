package com.pheeeew.pressrace.v2.application;

import com.pheeeew.pressrace.v2.domain.PressScreen;
import java.io.IOException;

/** 구독 하나의 전송 통로. 전송 실패는 예외로 알리고, 발행기가 구독을 정리한다. */
public interface PressStreamListener {

    void sendSnapshot(PressScreen screen) throws IOException;

    void sendHeartbeat() throws IOException;

    void close();
}
