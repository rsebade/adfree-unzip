package com.rsebade.adfreeunzip.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeViewModelTest {
    @Test
    fun `initial state has zero count`() {
        val viewModel = HomeViewModel()

        assertEquals(0, viewModel.uiState.value.count)
    }

    @Test
    fun `onIncrement increases count by one`() {
        val viewModel = HomeViewModel()

        viewModel.onIncrement()
        viewModel.onIncrement()

        assertEquals(2, viewModel.uiState.value.count)
    }
}
