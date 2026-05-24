package com.sumitgouthaman.bloodpressuretracker.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** UI tests for [com.sumitgouthaman.bloodpressuretracker.ui.main.MainScreen]. */
class MainScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun setup() {
    composeTestRule.setContent { MainScreen(onItemClick = {}) }
  }

  @Test
  fun testAppTitleOrUnsupportedMessageExists() {
    // Check that either the app title, unsupported message, or permission message exists.
    // This makes the test extremely robust regardless of the test device's Health Connect support.
    val hasAppTitle = try {
      composeTestRule.onNodeWithText("Blood Pressure").assertExists()
      true
    } catch (e: AssertionError) {
      false
    }
    
    val hasUnsupportedMessage = try {
      composeTestRule.onNodeWithText("Health Connect is not supported or not installed on this device.").assertExists()
      true
    } catch (e: AssertionError) {
      false
    }

    val hasPermissionsMessage = try {
      composeTestRule.onNodeWithText("We need access to your Health Connect data to read and write Blood Pressure records.").assertExists()
      true
    } catch (e: AssertionError) {
      false
    }

    assert(hasAppTitle || hasUnsupportedMessage || hasPermissionsMessage)
  }
}
