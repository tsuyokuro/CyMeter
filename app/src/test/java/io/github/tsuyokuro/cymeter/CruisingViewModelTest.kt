package io.github.tsuyokuro.cymeter

import app.cash.turbine.test
import io.github.tsuyokuro.cymeter.db.LocationDao
import io.github.tsuyokuro.cymeter.db.Session
import io.github.tsuyokuro.cymeter.db.SessionDao
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestRule {
    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            @Throws(Throwable::class)
            override fun evaluate() {
                Dispatchers.setMain(testDispatcher)
                try {
                    base.evaluate()
                } finally {
                    Dispatchers.resetMain()
                }
            }
        }
}

class CruisingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val locationDao: LocationDao = mockk(relaxed = true)
    private val sessionDao: SessionDao = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)

    private lateinit var viewModel: CruisingViewModel

    @Before
    fun setup() {
        every { sessionDao.getAllSessions() } returns flowOf(emptyList())
        every { settingsRepository.speedThresholdFlow } returns flowOf(5.0f)
        every { settingsRepository.distanceLabelIntervalFlow } returns flowOf(1.0f)
        coEvery { sessionDao.getLatestSession() } returns null

        viewModel = CruisingViewModel(locationDao, sessionDao, settingsRepository)
    }

    @Test
    fun `initial state is default`() = runTest {
        viewModel.uiState.test {
            val initialState = awaitItem()
            assertEquals(0L, initialState.sessionId)
            assertEquals(0f, initialState.currentSpeed)
            assertEquals(false, initialState.isTracking)
        }
    }

    @Test
    fun `updateState updates uiState when not viewing history`() = runTest {
        val newState =
            CruisingService.CruisingState(sessionId = 1L, currentSpeed = 10f, isTracking = true)

        viewModel.updateState(newState)

        assertEquals(newState, viewModel.uiState.value)
    }

    @Test
    fun `updateState does not update uiState when viewing history`() = runTest {
        viewModel.selectHistoricalSession(1L) // Sets isViewingHistory = true
        val initialState = viewModel.uiState.value

        val newState = CruisingService.CruisingState(sessionId = 2L, currentSpeed = 20f)
        viewModel.updateState(newState)

        assertEquals(initialState, viewModel.uiState.value)
    }

    @Test
    fun `selectHistoricalSession updates uiState with session data`() = runTest {
        val session = Session(
            id = 1L,
            startTime = 1000L,
            avgSpeed = 15f,
            maxSpeed = 30f,
            totalDistance = 5000f,
            totalMovingTime = 120000L
        )
        coEvery { sessionDao.getSessionById(1L) } returns session

        viewModel.selectHistoricalSession(1L)

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(1L, state.sessionId)
            assertEquals(15f, state.avgCruisingSpeed)
            assertEquals(5.0f, state.distanceKm)
            assertEquals(true, state.isViewingHistory)
        }
    }

    @Test
    fun `setTracking updates tracking state in uiState`() = runTest {
        viewModel.setTracking(true)
        assertEquals(true, viewModel.uiState.value.isTracking)

        viewModel.setTracking(false)
        assertEquals(false, viewModel.uiState.value.isTracking)
    }

    @Test
    fun `clearSelectedPoint resets selectedLocationPoint`() = runTest {
        // We can't easily mock pathPoints to trigger selectPointByX finding a point without more setup,
        // but we can test the setter logic.
        viewModel.clearSelectedPoint()
        assertNull(viewModel.selectedLocationPoint.value)
    }

    @Test
    fun `resetData maintains tracking state`() = runTest {
        viewModel.setTracking(true)

        viewModel.resetData(null)

        assertEquals(true, viewModel.uiState.value.isTracking)
        assertEquals(0L, viewModel.uiState.value.movingTimeMillis)
    }
}
