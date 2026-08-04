# Implementation Plan - Session Persistence and UI Retention

Implement robust session recovery and UI data retention to ensure users don't lose their cruising data if the app is killed or when they stop tracking.

## User Review Required

> [!IMPORTANT]
> - **Session Resumption**: Currently, starting a new tracking session via "Start Tracking" will always generate a new Session ID. If the app is killed by the OS while tracking, the service will attempt to resume the same session by loading the latest cumulative data from the database.
> - **UI Retention**: The Dashboard will continue to show the last tracked session data even after tracking has stopped, until a new session is started or data is explicitly reset.

## Proposed Changes

### Database Layer

#### [MODIFY] [LocationDao.kt](file:///H:/android_prj/cymeter/app/src/main/java/com/example/cymeter/db/LocationDao.kt)
- No changes needed if `getLatestPoint()` is sufficient. We might add a query to get the last X points for the map if session recovery needs to redraw the path.

### Service Layer

#### [MODIFY] [CruisingService.kt](file:///H:/android_prj/cymeter/app/src/main/java/com/example/cymeter/CruisingService.kt)
- **Session Recovery Logic**: Add a method to load cumulative data (distance, time, average speed) from the latest database entry upon service start if it detects an interrupted session.
- **Persistence on Stop**: Refine `stopTracking()` to ensure the final state is captured and committed to the database.
- **State Management**: Ensure `_cruisingData` flow reflects the recovered state immediately upon service start.

### ViewModel Layer

#### [MODIFY] [CruisingViewModel.kt](file:///H:/android_prj/cymeter/app/src/main/java/com/example/cymeter/CruisingViewModel.kt)
- **Initialization**: Improve `loadLastSession()` to handle edge cases where the service might already be running.
- **State Merging**: Ensure that when the service connects, it becomes the source of truth, but don't discard the DB-recovered state if the service hasn't started tracking yet.

### UI Layer

#### [MODIFY] [MainActivity.kt](file:///H:/android_prj/cymeter/app/src/main/java/com/example/cymeter/MainActivity.kt)
- Ensure the `LaunchedEffect` that collects from the service doesn't clear the ViewModel state when the service disconnects. (Already mostly correct, but will verify).

---

## Verification Plan

### Automated Tests
- **Unit Test**: `CruisingViewModelTest` to verify that it loads the last session from DAO on initialization.
- **Service Test**: (If possible) Verify `CruisingService` restores cumulative values after a simulated restart.

### Manual Verification
1. **Normal Flow**: Start tracking -> Move -> Stop tracking. Verify UI retains the summary data.
2. **Persistence Flow**: Start tracking -> Move -> Force Close App -> Restart App. Verify tracking has resumed (if service was running) or UI shows the last point.
3. **Reset Flow**: Click "Reset". Verify UI and DB (implicitly) start fresh.
4. **Edge-to-Edge**: Verify the Dashboard remains correctly padded during these states.
