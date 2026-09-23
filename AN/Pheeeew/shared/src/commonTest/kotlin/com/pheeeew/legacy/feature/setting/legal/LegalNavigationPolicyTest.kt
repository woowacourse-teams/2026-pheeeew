package com.pheeeew.legacy.feature.setting.legal

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LegalNavigationPolicyTest {
    @Test
    fun `each document allows only its own path`() {
        assertTrue(
            isAllowedLegalNavigation(
                LegalDocument.PrivacyPolicy,
                target(path = LegalDocument.PrivacyPolicy.path),
            ),
        )
        assertTrue(
            isAllowedLegalNavigation(
                LegalDocument.OpenSourceLicenses,
                target(path = LegalDocument.OpenSourceLicenses.path),
            ),
        )
    }

    @Test
    fun `one legal document cannot navigate to the other legal document`() {
        assertFalse(
            isAllowedLegalNavigation(
                LegalDocument.PrivacyPolicy,
                target(path = LegalDocument.OpenSourceLicenses.path),
            ),
        )
        assertFalse(
            isAllowedLegalNavigation(
                LegalDocument.OpenSourceLicenses,
                target(path = LegalDocument.PrivacyPolicy.path),
            ),
        )
    }

    @Test
    fun `legal home and license text paths are blocked`() {
        assertFalse(
            isAllowedLegalNavigation(
                LegalDocument.PrivacyPolicy,
                target(path = "/2026-pheeeew/index.html"),
            ),
        )
        assertFalse(
            isAllowedLegalNavigation(
                LegalDocument.OpenSourceLicenses,
                target(path = "/2026-pheeeew/licenses/apache-2.0.txt"),
            ),
        )
    }

    @Test
    fun `https with no explicit port or port 443 is allowed`() {
        assertTrue(isAllowedLegalNavigation(LegalDocument.PrivacyPolicy, target(port = null)))
        assertTrue(isAllowedLegalNavigation(LegalDocument.PrivacyPolicy, target(port = 443)))
        assertFalse(isAllowedLegalNavigation(LegalDocument.PrivacyPolicy, target(port = 8443)))
    }

    @Test
    fun `query strings and user info are blocked`() {
        assertFalse(
            isAllowedLegalNavigation(
                LegalDocument.PrivacyPolicy,
                target(query = "next=https://example.com"),
            ),
        )
        assertFalse(
            isAllowedLegalNavigation(
                LegalDocument.PrivacyPolicy,
                target(hasUserInfo = true),
            ),
        )
    }

    @Test
    fun `external and disguised hosts are blocked`() {
        assertFalse(
            isAllowedLegalNavigation(
                LegalDocument.PrivacyPolicy,
                target(host = "example.com"),
            ),
        )
        assertFalse(
            isAllowedLegalNavigation(
                LegalDocument.PrivacyPolicy,
                target(host = "woowacourse-teams.github.io.evil.example"),
            ),
        )
    }

    @Test
    fun `unsafe and downgraded schemes are blocked`() {
        listOf("http", "javascript", "file", "content", "data", "intent").forEach { scheme ->
            assertFalse(
                isAllowedLegalNavigation(
                    LegalDocument.PrivacyPolicy,
                    target(scheme = scheme),
                ),
            )
        }
    }

    @Test
    fun `missing URL components are blocked`() {
        assertFalse(
            isAllowedLegalNavigation(
                LegalDocument.PrivacyPolicy,
                target(scheme = null),
            ),
        )
        assertFalse(
            isAllowedLegalNavigation(
                LegalDocument.PrivacyPolicy,
                target(host = null),
            ),
        )
    }

    private fun target(
        scheme: String? = "https",
        host: String? = "woowacourse-teams.github.io",
        port: Int? = null,
        hasUserInfo: Boolean = false,
        path: String = LegalDocument.PrivacyPolicy.path,
        query: String? = null,
    ) = LegalNavigationTarget(
        scheme = scheme,
        host = host,
        port = port,
        hasUserInfo = hasUserInfo,
        path = path,
        query = query,
    )
}
