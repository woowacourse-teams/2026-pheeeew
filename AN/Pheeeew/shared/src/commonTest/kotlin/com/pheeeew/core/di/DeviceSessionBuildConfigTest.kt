package com.pheeeew.core.di

import com.pheeeew.data.remote.device.DeviceAttestationDto
import com.pheeeew.data.remote.device.DeviceAttestationPolicy
import com.pheeeew.data.remote.device.DeviceProofProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class DeviceSessionBuildConfigTest {
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
