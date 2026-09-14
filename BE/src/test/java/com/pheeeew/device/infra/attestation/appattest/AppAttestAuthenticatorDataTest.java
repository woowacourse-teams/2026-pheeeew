package com.pheeeew.device.infra.attestation.appattest;

import static com.pheeeew.device.fixture.AppAttestFixture.애플_표본_aaguid;
import static com.pheeeew.device.fixture.AppAttestFixture.애플_표본_authData_바이트_수;
import static com.pheeeew.device.fixture.AppAttestFixture.애플_표본_rpIdHash;
import static com.pheeeew.device.fixture.AppAttestFixture.애플_표본_객체;
import static com.pheeeew.device.fixture.AppAttestFixture.애플_표본_키_식별자;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class AppAttestAuthenticatorDataTest {

    private static final int 최소_길이 = 87;
    private static final int FLAGS_위치 = 32;
    private static final int 키_식별자_길이_위치 = 54;

    private final byte[] 애플_표본_authData = 애플_표본_객체().authenticatorData();

    @Test
    void 애플_표본의_authData_를_고정_오프셋으로_분해한다() {
        // given / when
        AppAttestAuthenticatorData result = AppAttestAuthenticatorData.from(애플_표본_authData);

        // then
        assertThat(result.rpIdHash()).isEqualTo(Base64.getDecoder().decode(애플_표본_rpIdHash));
        assertThat(result.counter()).isZero();
        assertThat(HexFormat.of().formatHex(result.aaguid())).isEqualTo(애플_표본_aaguid);
        assertThat(result.credentialId()).isEqualTo(Base64.getDecoder().decode(애플_표본_키_식별자));
    }

    @Test
    void 확장_데이터가_있는데도_flags_의_ED_비트는_0이라_flags_로_분기하면_틀린다() {
        // given
        byte flags = 애플_표본_authData[FLAGS_위치];

        // when
        AppAttestAuthenticatorData result = AppAttestAuthenticatorData.from(애플_표본_authData);

        // then
        assertThat(flags).isEqualTo((byte) 0x40);
        assertThat(flags & 0x80).isZero();
        assertThat(애플_표본_authData).hasSize(애플_표본_authData_바이트_수);
        assertThat(애플_표본_authData_바이트_수).isGreaterThan(최소_길이);
        assertThat(result.credentialId()).isEqualTo(Base64.getDecoder().decode(애플_표본_키_식별자));
    }

    @Test
    void 최소_길이보다_짧으면_거절한다() {
        // given
        byte[] 잘린_authData = Arrays.copyOf(애플_표본_authData, 최소_길이 - 1);

        // when / then
        assertThatThrownBy(() -> AppAttestAuthenticatorData.from(잘린_authData))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 키_식별자_길이가_32가_아니면_거절한다() {
        // given
        byte[] 길이가_조작된_authData = Arrays.copyOf(애플_표본_authData, 애플_표본_authData.length);
        길이가_조작된_authData[키_식별자_길이_위치] = 0x10;

        // when / then
        assertThatThrownBy(() -> AppAttestAuthenticatorData.from(길이가_조작된_authData))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
