# Max Speed Tracking and Configurable Speed Threshold

Add Max Speed tracking per session and allow users to configure the cruising speed threshold using DataStore.

## User Review Required

> [!IMPORTANT]
> The database version will be incremented to 5. Destructive migration is used, which will clear existing history sessions.

## Proposed Changes

### [Persistence]

#### [MODIFY] [Session.kt](file:///H:/android_prj/cymeter/app/src/main/java/com/example/cymeter/db/Session.kt)
- Add `maxSpeed: Float = 0f` to the `Session` entity.

#### [MODIFY] [AppDatabase.kt](file:///H:/android_prj/cymeter/app/src/main/java/com/example/cymeter/db/AppDatabase.kt)
- Increment `version` to 5.
- Ensure `fallbackToDestructiveMigration` is active (it is currently).

#### [NEW] [SettingsRepository.kt](file:///H:/android_prj/cymeter/app/src/main/java/com/example/cymeter/SettingsRepository.kt)
- Create a class to handle user preferences using `androidx.datastore.preferences`.
- Key: `cruising_speed_threshold` (Float, km/h).
- Default: 5.0 km/h.
- Provide a `Flow<Float>` for the threshold and a `suspend fun updateThreshold(Float)`.

---

### [Logic]

#### [MODIFY] [CruisingService.kt](file:///H:/android_prj/cymeter/app/src/main/java/com/example/cymeter/CruisingService.kt)
- Update `CruisingState` data class to include `maxSpeed: Float`.
- Add a private `var maxSpeed: Float = 0f` to track the maximum speed during the session.
- Initialize `SettingsRepository` in `CruisingService`.
- Collect the threshold flow from `SettingsRepository` and store it in a local variable (converting km/h to m/s).
- In `trackLocation`:
    - Update `maxSpeed = maxOf(maxSpeed, speed)`.
    - Use the dynamic threshold instead of the `SPEED_THRESHOLD_MPS` constant.
- In `writeLocationLog` and `stopTracking`:
    - Save the `maxSpeed` to the `Session` record.
- In `resetData`:
    - Reset the local `maxSpeed` to 0.

#### [MODIFY] [CruisingViewModel.kt](file:///H:/android_prj/cymeter/app/src/main/java/com/example/cymeter/CruisingViewModel.kt)
- Add `SettingsRepository` as a parameter to the constructor.
- Expose `speedThreshold` as a `StateFlow<Float>` (in km/h).
- Add `updateSpeedThreshold(Float)` function.
- Update `loadLastSession` and `selectHistoricalSession` to populate `maxSpeed` in the UI state.

#### [MODIFY] [MainActivity.kt](file:///H:/android_prj/cymeter/app/src/main/java/com/example/cymeter/MainActivity.kt)
- Instantiate `SettingsRepository` and pass it to `CruisingViewModel`.

---

### [UI]

#### [MODIFY] [DashboardScreen.kt](file:///H:/android_prj/cymeter/app/src/main/java/com/example/cymeter/DashboardScreen.kt)
- Add a `StatCard` for "Max Speed" in `DashboardContent`.
- Wrap the dashboard content in a `Scaffold` to add a `TopAppBar`.
- Add a "Settings" `IconButton` to the `TopAppBar`.
- Implement a `ThresholdSettingsDialog` that opens when the settings button is clicked, allowing users to input a new threshold value.

#### [MODIFY] [HistoryScreen.kt](file:///H:/android_prj/cymeter/app/src/main/java/com/example/cymeter/HistoryScreen.kt)
- Add a "Max Speed" `InfoChip` to the `SessionItem`.

---

## Verification Plan

### Manual Verification
1. **Max Speed Tracking**:
    - Start tracking in the Dashboard.
    - Simulate or move at varying speeds.
    - Verify that the "Max Speed" card updates correctly in real-time.
    - Stop tracking and verify the "Max Speed" is saved in History.
2. **Configurable Threshold**:
    - Open the Settings dialog from the Dashboard.
    - Change the threshold (e.g., from 5 km/h to 10 km/h).
    - Verify that "Avg Speed" only factors in speeds above the new threshold.
    - Restart the app and verify the threshold setting persists.
3. **Database Migration**:
    - Verify the app doesn't crash on startup after the update (confirming destructive migration works or handling the version bump).
