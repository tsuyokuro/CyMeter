# Implementation Plan - Map and Location History

Enable location history logging in a Room database and display the current session's path on a MapLibre map.

## User Review Required

> [!IMPORTANT]
> The app will use `org.maplibre.compose:maplibre-compose` for mapping.
> I will use a free map style from OpenFreeMap (`https://tiles.openfreemap.org/styles/liberty`) for the initial implementation.

## Proposed Changes

### [Persistence]

#### [NEW] [LocationPoint.kt](file:///H:/android_prj/cymeter/app/src/main/java/com/example/cymeter/data/LocationPoint.kt)
- Room Entity representing a single GPS coordinate.
- Fields: `id` (Primary Key), `sessionId` (String), `latitude` (Double), `longitude` (Double), `timestamp` (Long).

#### [NEW] [LocationDao.kt](file:///H:/android_prj/cymeter/app/src/main/java/com/example/cymeter/data/LocationDao.kt)
- DAO interface for `LocationPoint`.
- Methods: `insertLocation(LocationPoint)`, `getLocationsForSession(String): Flow<List<LocationPoint>>`.

#### [NEW] [AppDatabase.kt](file:///H:/android_prj/cymeter/app/src/main/java/com/example/cymeter/data/AppDatabase.kt)
- Room Database class holding the `LocationPoint` table.

### [Logic & Service Updates]

#### [MODIFY] [CruisingService.kt](file:///H:/android_prj/cymeter/app/src/main/java/com/example/cymeter/CruisingService.kt)
- Add `currentSessionId: String?`.
- Initialize `currentSessionId` (e.g., `UUID.randomUUID().toString()`) in `startTracking()`.
- Inject/obtain `LocationDao`.
- In `trackLocation()`, every 10 seconds (or based on a threshold), save the current `LocationPoint` to the database if `currentSessionId` is not null.
- Clear `currentSessionId` in `stopTracking()`.

#### [MODIFY] [CruisingViewModel.kt](file:///H:/android_prj/cymeter/app/src/main/java/com/example/cymeter/CruisingViewModel.kt)
- Add `currentSessionId` to the UI state.
- Expose a `Flow<List<LocationPoint>>` from the database for the current session.

### [Map Integration]

#### [MODIFY] [libs.versions.toml](file:///H:/android_prj/cymeter/gradle/libs.versions.toml)
- Add `maplibre-compose = { group = "org.maplibre.compose", name = "maplibre-compose", version = "0.13.1" }`.

#### [MODIFY] [build.gradle.kts](file:///H:/android_prj/cymeter/app/build.gradle.kts)
- Add `implementation(libs.maplibre.compose)`.

#### [NEW] [MapScreen.kt](file:///H:/android_prj/cymeter/app/src/main/java/com/example/cymeter/MapScreen.kt)
- Implement a screen using `MaplibreMap`.
- Fetch `LocationPoint` list from `CruisingViewModel`.
- Use a `LineLayer` (via `rememberGeoJsonSource`) to draw the path.
- Add a `SymbolLayer` or a custom marker for the current location.
- Implement "Follow Me" logic to auto-center the map on new location updates.

### [UI / Navigation]

#### [MODIFY] [MainActivity.kt](file:///H:/android_prj/cymeter/app/src/main/java/com/example/cymeter/MainActivity.kt)
- Define `MapRoute` in Navigation 3.
- Add "Map" item to the `NavigationSuiteScaffold`.
- Update `NavDisplay` to handle `MapRoute`.

#### [MODIFY] [DashboardScreen.kt](file:///H:/android_prj/cymeter/app/src/main/java/com/example/cymeter/DashboardScreen.kt)
- Add a "View Map" button to the dashboard that navigates to the Map screen.

## Verification Plan

### Automated Tests
- Unit test for `LocationDao` to ensure points are saved and retrieved correctly.
- Verify `CruisingService` logic for session ID generation and periodic saving.

### Manual Verification
- Start tracking and move around (or use GPS emulator).
- Switch to Map view and verify the polyline grows.
- Stop tracking, then start again; verify a new session starts with a fresh (empty) map path.
- Verify map centering and orientation behaviors.
