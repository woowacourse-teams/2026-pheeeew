package com.pheeeew.sigh.experiment.e001;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class E001ScenarioTest {

    @Test
    void largest_remainder는_ID_정렬과_나머지_동률_순서를_고정한다() {
        // given
        Map<String, Double> weights = Map.of("c", 1.0, "a", 1.0, "b", 1.0);

        // when
        Map<String, Long> counts = E001Allocation.distribute(weights, 4);

        // then
        assertThat(counts.keySet()).containsExactly("a", "b", "c");
        assertThat(counts.values()).containsExactly(2L, 1L, 1L);
        assertThat(E001Allocation.distribute(weights, 0).values()).containsOnly(0L);
        assertThatThrownBy(() -> E001Allocation.distribute(Map.of("a", Double.NaN), 4))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 불균형_3x3은_사전등록된_중심별_개수를_다섯_seed에_나눈다() {
        // when
        E001Scenario.Plan plan = E001Scenario.TUNING_GRID.plan();

        // then
        assertThat(plan.requestedCount()).isEqualTo(4_500L);
        assertThat(plan.centers()).extracting(E001Scenario.Center::perSeedCount)
                .containsExactly(55L, 85L, 40L, 75L, 240L, 110L, 30L, 95L, 170L);
        assertThat(plan.centers().getFirst()).isEqualTo(E001Scenario.Center.of("r00c00", -1, 1, 55));
        assertThat(plan.centers().getLast()).isEqualTo(E001Scenario.Center.of("r02c02", 1, -1, 170));
        assertThat(plan.requested()).hasSize(45);
    }

    @ParameterizedTest
    @CsvSource({"TUNING_SINGLE, 1, 500, 300", "CONFIRMATION_SINGLE_500, 1, 500, 300",
            "CONFIRMATION_SINGLE_5000, 1, 5000, 300", "CONFIRMATION_GRID_500, 9, 4500, 600",
            "CONFIRMATION_GRID_5000, 9, 45000, 600", "SPECTRAL_EQUAL, 121, 60500, 1350",
            "SPECTRAL_IMBALANCED, 121, 60500, 1350"})
    void 시나리오는_중심_수와_전체_요청_수와_평가_창을_고정한다(
            E001Scenario scenario, int centers, long requested, int halfWidth
    ) {
        // when
        E001Scenario.Plan plan = scenario.plan();

        // then
        assertThat(plan.centers()).hasSize(centers);
        assertThat(plan.requestedCount()).isEqualTo(requested);
        assertThat(plan.requested().values().stream().mapToLong(Long::longValue).sum()).isEqualTo(requested);
        assertThat(scenario.halfWidth()).isEqualTo(halfWidth);
        assertThat(scenario.originX() % 300.0).isEqualTo(150.0);
        assertThat(scenario.originY() % 300.0).isEqualTo(150.0);
        if (centers == 1) {
            assertThat(plan.centers().getFirst().id()).isEqualTo("single");
        }
    }

    @ParameterizedTest
    @CsvSource({"-5, 5, 3fe9b03c28995730", "0, 0, 3ffe4876632f1a43", "5, -5, 3fffe4bdbc0d4cf4"})
    void spectral_가중치는_부호와_고정_profile_seed를_포함한_SHA256을_사용한다(int i, int j, String expectedBits) {
        // when
        double weight = E001Scenario.spectralWeight(i, j);

        // then
        assertThat(HexFormat.of().toHexDigits(Double.doubleToRawLongBits(weight))).isEqualTo(expectedBits);
    }

    @Test
    void spectral_121개_중심의_배분은_독립_Python_계산값과_일치한다() throws Exception {
        // given
        E001Scenario.Plan plan = E001Scenario.SPECTRAL_IMBALANCED.plan();
        StringBuilder allocation = new StringBuilder();
        plan.centers().forEach(center -> allocation.append(center.id()).append(':').append(center.perSeedCount()).append('\n'));

        // when
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(allocation.toString().getBytes(UTF_8)));

        // then
        assertThat(hash).isEqualTo("ec420001df73682e684e29f6dec66b9c609db20be3a593a6784774719065e3cf");
        assertThat(plan.centers().stream().mapToLong(E001Scenario.Center::perSeedCount).sum()).isEqualTo(12_100L);
    }

    @Test
    void E_후보는_동일_sigma의_36개_Cartesian_product이며_ID가_정렬되어_있다() {
        // when
        List<E001Parameters> parameters = E001Parameters.fieldCandidates(120);

        // then
        assertThat(parameters).hasSize(36).doesNotHaveDuplicates();
        assertThat(parameters).allSatisfy(candidate -> assertThat(candidate.sigma()).isEqualTo(120));
        assertThat(parameters).extracting(E001Parameters::parameterSetId).isSorted().doesNotHaveDuplicates();
        assertThat(parameters.getFirst().parameterSetId()).isEqualTo("e-s120-gradient-p1-b040-rejection128");
        assertThat(parameters.getLast().parameterSetId()).isEqualTo("e-s120-value-p2-b100-sir32");
        assertThatThrownBy(() -> E001Parameters.fieldCandidates(110)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(E001Scenario.class)
    void 시나리오_ID는_기존_선택_엔진과_같다(E001Scenario scenario) {
        // given
        List<String> ids = List.of(E001Selection.TUNING_SINGLE, E001Selection.TUNING_GRID,
                E001Selection.CONFIRMATION_SINGLE.getFirst(), E001Selection.CONFIRMATION_SINGLE.getLast(),
                E001Selection.CONFIRMATION_GRID.getFirst(), E001Selection.CONFIRMATION_GRID.getLast(),
                E001Selection.SPECTRAL.getFirst(), E001Selection.SPECTRAL.getLast());

        // when & then
        assertThat(ids).contains(scenario.id());
    }
}
