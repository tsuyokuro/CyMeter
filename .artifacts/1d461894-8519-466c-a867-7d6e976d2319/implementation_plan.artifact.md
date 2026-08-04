# Implementation Plan - Task 8: MapLibre and Map UI

Implement a Map screen to visualize the current cruising path using MapLibre and integrate it into the existing navigation.

## User Review Required

> [!IMPORTANT]
> - Using `org.maplibre.compose:maplibre-compose:0.13.1`.
> - Map style URL: `https://demotiles.maplibre.org/style.json`.
> - A "Map" tab will be added to the bottom navigation.

## Proposed Changes

### Dependencies

#### [MODIFY] [libs.versions.toml](file:///H:/android_prj/cymeter/gradle/libs.versions.toml)
- Add `maplibre-compose` version and library.

#### [MODIFY] [build.gradle.kts](file:///H:/android_prj/cymeter/app/build.gradle.kts)
- Add `maplibre-compose` dependency.

### Database Logic

#### [MODIFY] [LocationDao.kt](file:///H:/android_prj/cymeter/app/src/main/java/com/example/cymeter/db/LocationDao.kt)
- Add a Flow-returning query to observe points for a session.

### ViewModel

#### [MODIFY] [CruisingViewModel.kt](file:///H:/android_prj/cymeter/app/src/main/java/com/example/cymeter/CruisingViewModel.kt)
- Add `pathPoints` StateFlow.
- Observe `LocationDao` when `sessionId` changes.

### UI - Map Screen

#### [NEW] [MapScreen.kt](file:///H:/android_prj/cymeter/app/src/main/java/com/example/cymeter/MapScreen.kt)
- Implement `MapScreen` using `MaplibreMap`.
- Draw polyline using `GeoJsonSource` and `LineLayer`.
- Add a marker for current location.

### Navigation

#### [MODIFY] [MainActivity.kt](file:///H:/android_prj/cymeter/app/src/main/java/com/example/cymeter/MainActivity.kt)
- Add `MapRoute` serializable object.
- Update `NavigationSuiteScaffold` to include Map tab.
- Update `NavDisplay` to handle `MapRoute`.

## Verification Plan

### Automated Tests
- `./gradlew assembleDebug` to ensure compilation.

### Manual Verification
- Verify the "Map" tab appears in the navigation.
- Verify the map loads and displays the current location.
- Verify that when cruising, a line starts appearing on the map following the path.
- Verify smooth switching between Dashboard and Map.
