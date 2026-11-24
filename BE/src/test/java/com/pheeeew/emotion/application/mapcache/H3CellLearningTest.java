package com.pheeeew.emotion.application.mapcache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.uber.h3core.H3Core;
import com.uber.h3core.util.LatLng;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/** h3-java 네이티브 라이브러리가 실행 환경에서 로드되는지와 셀 계층 동작을 확인한다. */
class H3CellLearningTest {

    private static final double LATITUDE = 37.4023;
    private static final double LONGITUDE = 127.1085;

    @Test
    void 좌표를_셀로_바꾸면_요청한_해상도의_셀이_나온다() throws IOException {
        // given
        H3Core h3 = H3Core.newInstance();

        // when
        long cell = h3.latLngToCell(LATITUDE, LONGITUDE, 9);

        // then
        assertThat(h3.isValidCell(cell)).isTrue();
        assertThat(h3.getResolution(cell)).isEqualTo(9);
    }

    @Test
    void 셀의_중심은_원래_좌표에서_셀_크기_안에_있다() throws IOException {
        // given
        H3Core h3 = H3Core.newInstance();
        long cell = h3.latLngToCell(LATITUDE, LONGITUDE, 9);

        // when
        LatLng center = h3.cellToLatLng(cell);

        // then: 해상도 9의 변 길이는 약 0.2km라 0.01도 안에 들어온다
        assertThat(center.lat).isCloseTo(LATITUDE, within(0.01));
        assertThat(center.lng).isCloseTo(LONGITUDE, within(0.01));
    }

    @Test
    void 셀은_상위_해상도마다_부모가_하나이고_그_부모의_자식에_포함된다() throws IOException {
        // given
        H3Core h3 = H3Core.newInstance();
        long cell = h3.latLngToCell(LATITUDE, LONGITUDE, 9);

        // when
        long parent = h3.cellToParent(cell, 7);

        // then: 육각형은 부모 안에 정확히 포개지지 않아 latLngToCell(…, 7)과 다를 수 있다.
        // 줌 단계 합산은 좌표 재변환이 아니라 cellToParent로 해야 개수가 맞는다.
        assertThat(h3.getResolution(parent)).isEqualTo(7);
        assertThat(h3.cellToChildren(parent, 9)).contains(cell).hasSize(49);
    }
}
