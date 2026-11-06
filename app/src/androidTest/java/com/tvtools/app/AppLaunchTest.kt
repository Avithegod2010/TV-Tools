package com.tvtools.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Launch smoke test.
 *
 * A fresh install shows the onboarding pager ("Skip"); once onboarding has been
 * completed the home screen ("TV Tools") is shown instead. The test accepts
 * either state so it is stable across emulator snapshots and local reruns.
 */
@RunWith(AndroidJUnit4::class)
class AppLaunchTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchesToOnboardingOrHome() {
        composeRule.waitUntil(timeoutMillis = 20_000) {
            runCatching { onboardingNodes().isNotEmpty() || homeNodes().isNotEmpty() }
                .getOrDefault(false)
        }

        val onboarding = runCatching { onboardingNodes() }.getOrDefault(emptyList())
        val home = runCatching { homeNodes() }.getOrDefault(emptyList())

        assertTrue(
            "Expected onboarding (\"Skip\") or home (\"TV Tools\") to be visible after launch",
            onboarding.isNotEmpty() || home.isNotEmpty(),
        )
    }

    private fun onboardingNodes() =
        composeRule.onAllNodesWithText("Skip").fetchSemanticsNodes()

    private fun homeNodes() =
        composeRule.onAllNodesWithText("TV Tools").fetchSemanticsNodes()
}
