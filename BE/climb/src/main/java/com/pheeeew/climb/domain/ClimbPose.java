package com.pheeeew.climb.domain;

/**
 * 다른 사람에게 보여 줄 위치와 자세 하나. 저장하지 않고 순위에 쓰지 않는다.
 *
 * @param x      몸 가운데 가로 위치(px)
 * @param h      발 높이(칸, 소수)
 * @param vx     가로 속도(px/초)
 * @param vy     세로 속도(px/초, 아래가 +)
 * @param flags  자세 표시 비트. 1 땅 위, 2 힘 모으는 중, 4 왼쪽을 봄, 8 엎어짐, 16 미끄러지는 중
 * @param charge 모은 정도(0~100)
 */
public record ClimbPose(int x, double h, int vx, int vy, int flags, int charge) {

    private static final int WIDTH = 1280; // 게임 화면 너비(32칸 × 40px)
    private static final int ABOVE_TOP = 18; // 꼭대기 위 쉼터(화면 1개 높이)
    private static final int MAX_SPEED = 5000;

    public boolean within(ClimbMap map) {
        return x >= 0 && x <= WIDTH && h >= 0 && h <= map.top() + ABOVE_TOP
                && Math.abs(vx) <= MAX_SPEED && Math.abs(vy) <= MAX_SPEED
                && flags >= 0 && flags < 32 && charge >= 0 && charge <= 100;
    }
}
