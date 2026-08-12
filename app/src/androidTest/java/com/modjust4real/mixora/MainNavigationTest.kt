package com.modjust4real.mixora

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class MainNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeOpensMixStudio() {
        composeRule.onNodeWithTag("home_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("open_mix").performClick()
        composeRule.onNodeWithTag("mix_screen").assertIsDisplayed()
    }

    @Test
    fun homeOpensPresetCreator() {
        composeRule.onNodeWithTag("open_preset").performClick()
        composeRule.onNodeWithTag("preset_screen").assertIsDisplayed()
    }
}
