package com.example.app.ui.home

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.app.ui.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tappingButtonUpdatesCount() {
        val viewModel = HomeViewModel()
        composeRule.setContent {
            AppTheme { HomeRoute(viewModel = viewModel) }
        }

        composeRule.onNodeWithTag(HomeTestTags.INCREMENT).performClick()

        composeRule.onNodeWithTag(HomeTestTags.COUNT).assertTextEquals("Tapped 1 time")
    }
}
