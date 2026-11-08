package com.pheeeew.core.di.device

import com.pheeeew.data.local.device.LegacyCredentialPolicy
import com.pheeeew.data.remote.attestation.DeviceAttestationPolicy
import com.pheeeew.data.remote.attestation.DeviceProofProvider
import com.pheeeew.data.remote.dto.DeviceAttestationDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class DeviceSessionBuildConfigTest {
    @Test
    fun explicitBuildModesRejectMisspellingsInsteadOfSilentlyChangingInputPath() {
        assertEquals(PressInputMode.HTTP, PressInputMode.fromBuildValue("http"))
        assertEquals(PressInputMode.WEBSOCKET_DEVELOPMENT, PressInputMode.fromBuildValue("ws"))
        kotlin.test.assertFails { PressInputMode.fromBuildValue("websocket") }
    }

    @Test
    fun inputDefaultsToHttpAndAllReceiverModesRequireTheHttpPressPath() {
        val debug = DeviceSessionBuildConfig(true, "dev", DeviceSessionBuildConfig.DEV_URL, "platform_only", "1")
        assertEquals(PressInputMode.HTTP, debug.pressInputMode)
        assertFailsWith<IllegalArgumentException> {
            debug.copy(pressInputMode = PressInputMode.WEBSOCKET_DEVELOPMENT)
        }
        assertFailsWith<IllegalArgumentException> {
            DeviceSessionBuildConfig(
                false,
                "prod",
                DeviceSessionBuildConfig.PROD_URL,
                "required",
                "1",
                PressInputMode.WEBSOCKET_DEVELOPMENT,
            )
        }
    }

    @Test
    fun receiveModeSelectsOnlyKnownVariantsAndComparisonsRequireHttpInput() {
        assertEquals(PressRaceReceiveMode.WEBSOCKET, PressRaceReceiveMode.fromBuildValue("ws"))
        assertEquals(PressRaceReceiveMode.CACHE_POLLING, PressRaceReceiveMode.fromBuildValue("cache_polling"))
        assertEquals(PressRaceReceiveMode.SSE, PressRaceReceiveMode.fromBuildValue("sse"))
        assertEquals(PressRaceReceiveMode.BASIC_POLLING, PressRaceReceiveMode.fromBuildValue("basic_polling"))
        assertFailsWith<IllegalStateException> { PressRaceReceiveMode.fromBuildValue("polling") }
        assertEquals(
            PressRaceReceiveMode.WEBSOCKET,
            DeviceSessionBuildConfig(
                false,
                "prod",
                DeviceSessionBuildConfig.PROD_URL,
                "required",
                "1",
            ).pressRaceReceiveMode,
        )

        for (
            mode in
            listOf(
                PressRaceReceiveMode.WEBSOCKET,
                PressRaceReceiveMode.CACHE_POLLING,
                PressRaceReceiveMode.SSE,
                PressRaceReceiveMode.BASIC_POLLING,
            )
        ) {
            assertEquals(
                mode,
                DeviceSessionBuildConfig(
                    true,
                    "dev",
                    DeviceSessionBuildConfig.DEV_URL,
                    "platform_only",
                    "1",
                    pressRaceReceiveMode = mode,
                ).pressRaceReceiveMode,
            )
            assertFailsWith<IllegalArgumentException> {
                DeviceSessionBuildConfig(
                    true,
                    "dev",
                    DeviceSessionBuildConfig.DEV_URL,
                    "platform_only",
                    "1",
                    PressInputMode.WEBSOCKET_DEVELOPMENT,
                    mode,
                )
            }
            if (mode != PressRaceReceiveMode.WEBSOCKET) assertFailsWith<IllegalArgumentException> {
                DeviceSessionBuildConfig(
                    false,
                    "prod",
                    DeviceSessionBuildConfig.PROD_URL,
                    "required",
                    "1",
                    pressRaceReceiveMode = mode,
                )
            }
        }
    }

    @Test
    fun `only production release imports published legacy credentials`() {
        assertEquals(
            LegacyCredentialPolicy.IMPORT_CURRENT_ENVIRONMENT,
            DeviceSessionBuildConfig(
                false,
                "prod",
                DeviceSessionBuildConfig.PROD_URL,
                "required",
                "1",
            ).legacyCredentialPolicy,
        )
        assertEquals(
            LegacyCredentialPolicy.START_NEW_ENVIRONMENT_SESSION,
            DeviceSessionBuildConfig(
                true,
                "dev",
                DeviceSessionBuildConfig.DEV_URL,
                "platform_only",
                "1",
            ).legacyCredentialPolicy,
        )
    }

    @Test
    fun `debug platform only never constructs proof provider`() {
        val build = DeviceSessionBuildConfig(true, "dev", DeviceSessionBuildConfig.DEV_URL, "platform_only", "1.1.1+3")
        assertIs<DeviceAttestationPolicy.PlatformOnly>(build.policy { error("SDK must not be initialized") })
    }

    @Test
    fun `release and explicit debug proof modes construct required provider`() {
        for (debug in listOf(true, false)) {
            var created = 0
            val build =
                DeviceSessionBuildConfig(
                    debug,
                    if (debug) "dev" else "prod",
                    if (debug) DeviceSessionBuildConfig.DEV_URL else DeviceSessionBuildConfig.PROD_URL,
                    "required",
                    "1.1.1+3",
                )
            assertIs<DeviceAttestationPolicy.Required>(
                build.policy {
                    created++
                    DeviceProofProvider {
                        platform,
                        challenge,
                        ->
                        DeviceAttestationDto(platform.name, "proof", challenge)
                    }
                },
            )
            assertEquals(1, created)
        }
    }

    @Test
    fun `release rejects bypass dev url and incorrect environment`() {
        assertFailsWith<IllegalArgumentException> {
            DeviceSessionBuildConfig(false, "prod", DeviceSessionBuildConfig.PROD_URL, "platform_only", "1")
        }
        assertFailsWith<IllegalArgumentException> {
            DeviceSessionBuildConfig(false, "prod", DeviceSessionBuildConfig.DEV_URL, "required", "1")
        }
        assertFailsWith<IllegalArgumentException> {
            DeviceSessionBuildConfig(false, "dev", DeviceSessionBuildConfig.PROD_URL, "required", "1")
        }
    }

    @Test
    fun `debug rejects prod url missing or misspelled mode`() {
        assertFailsWith<IllegalArgumentException> {
            DeviceSessionBuildConfig(true, "dev", DeviceSessionBuildConfig.PROD_URL, "platform_only", "1")
        }
        for (mode in listOf("", "Required", "skip_verification", "$(DEVICE_ATTESTATION_MODE)")) {
            assertFailsWith<IllegalArgumentException> {
                DeviceSessionBuildConfig(true, "dev", DeviceSessionBuildConfig.DEV_URL, mode, "1")
            }
        }
    }
}
