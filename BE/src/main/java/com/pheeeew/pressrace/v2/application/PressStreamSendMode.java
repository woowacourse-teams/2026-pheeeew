package com.pheeeew.pressrace.v2.application;

/**
 * 실시간 프레스 SSE 의 전송 방식. 같은 조건에서 설정만 바꿔 비교한다.
 */
public enum PressStreamSendMode {
    /** 발행기 스레드에서 구독마다 차례로 보낸다. 수신이 늦은 연결이 같은 주기의 다른 전송과 다음 주기를 늦출 수 있다. */
    SEQUENTIAL,
    /** 발행기는 구독별 대기에 최신 상태만 넘기고, 전송은 상한이 있는 별도 실행기에서 구독마다 한 번에 하나씩 한다. */
    ISOLATED
}
