package com.pheeeew.sigh.experiment.e001;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class E001SpectralMetricsTest {

    @Test
    void raster는_중앙_9x9_창을_남서쪽부터_half_open_15미터_cell로_나눈다() {
        // given
        double originX = 953_850.0;
        double originY = 1_951_950.0;
        List<E001Sample> samples = List.of(point(0, originX - 1_350.0, originY - 1_350.0),
                point(1, originX - 1_335.0, originY - 1_350.0),
                point(2, originX + 1_349.0, originY + 1_349.0),
                point(3, originX + 1_350.0, originY), point(4, originX, originY + 1_350.0),
                point(5, originX - 1_351.0, originY));

        // when
        long[][] raster = E001SpectralMetrics.raster(samples, originX, originY);

        // then
        assertThat(raster[0][0]).isEqualTo(1L);
        assertThat(raster[0][1]).isEqualTo(1L);
        assertThat(raster[179][179]).isEqualTo(1L);
        assertThat(Arrays.stream(raster).flatMapToLong(Arrays::stream).sum()).isEqualTo(3L);
    }

    @Test
    void spectral_분모는_축_15도를_제외한_144개_bin이다() {
        // when
        List<E001SpectralMetrics.Bin> bins = E001SpectralMetrics.denominatorBins();

        // then
        assertThat(bins).hasSize(144).doesNotHaveDuplicates();
        assertThat(bins).doesNotContain(E001SpectralMetrics.Bin.of(9, 0), E001SpectralMetrics.Bin.of(0, 9));
        assertThat(bins).contains(E001SpectralMetrics.Bin.of(6, 6), E001SpectralMetrics.Bin.of(-6, -6));
    }

    @Test
    void 직접_DFT는_독립적인_분리형_DFT의_고정_fixture_결과와_일치한다() {
        // given
        long[][] raster = new long[180][180];
        for (int y = 0; y < 180; y++) {
            for (int x = 0; x < 180; x++) {
                raster[y][x] = (x * 17 + y * 31 + x * y * 7) % 23
                        + (x % 20 == 0 ? 30 : 0) + (y % 20 == 0 ? 10 : 0);
            }
        }

        // when
        double actual = E001SpectralMetrics.grid300(raster);

        // then
        // Python cmath로 x축 DFT 후 y축 DFT를 적용한 독립 계산값이에요. 실험 표본은 아니에요.
        assertThat(actual).isCloseTo(5_959_800.821051917, within(0.0001));
    }

    @Test
    void 비어있거나_일정한_raster는_정의되지_않은_spectral_값이다() {
        // given
        long[][] empty = new long[180][180];
        long[][] constant = new long[180][180];
        for (long[] row : constant) {
            Arrays.fill(row, 1L);
        }

        // when & then
        assertThat(E001SpectralMetrics.grid300(empty)).isNaN();
        assertThat(E001SpectralMetrics.grid300(constant)).isNaN();
        assertThat(E001SpectralMetrics.grid300(List.of(point(0, Double.NaN, 0.0)), 0.0, 0.0)).isNaN();
    }

    private static E001Sample point(long index, double x, double y) {
        return E001Sample.of(1L, "0:0", index, x, y, E001Offset.of(0.0, 0.0, 0.0));
    }
}
