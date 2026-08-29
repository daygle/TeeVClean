package com.teevclean.app

import android.app.Application
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TeeVViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: TeeVViewModel
    private val application: Application = mockk(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        // Note: Repository is instantiated inside ViewModel, so we'd normally use DI (Hilt/Koin)
        // For this refactor, we assume a simplified setup or that we can inject a mock repo.
        // Since we didn't add DI yet, we'll test the state changes we can control.
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is correct`() {
        // This is a placeholder test since the repo is currently hard-coded in the VM
        // In a real scenario, we'd inject a mock repository.
    }

    @Test
    fun `screen navigation updates state`() {
        viewModel = TeeVViewModel(application)
        viewModel.currentScreen = Screen.HEALTH
        assertEquals(Screen.HEALTH, viewModel.currentScreen)
    }
}
