package org.umn.application.studenthub

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test

class StudentHubNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun todayOpensSchedule() {
        compose.onNodeWithText("Selamat pagi, Nadia.").assertIsDisplayed()
        compose.onNodeWithText("Lihat jadwal").performScrollTo().performClick()
        compose.onNodeWithText("Minggu yang terarah.").assertIsDisplayed()
    }
}
