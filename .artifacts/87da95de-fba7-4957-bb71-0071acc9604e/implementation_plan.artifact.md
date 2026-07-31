# Distance Tracking Implementation Plan

Add total distance tracking to CyMeter. The distance will be calculated by accumulating the distance between locations sampled every 10 seconds while tracking is active.

## Proposed Changes

### [CruisingService.kt](file:///H:/android_prj/cymeter/app/src/main/java/com/example/cymeter/CruisingService.kt)

#### [MODIFY] [CruisingService.kt](file:///H:/android_prj/cymeter/app/src/main/java/com/example/cymeter/CruisingService.kt)
- Add tracking properties:
    - `private var lastDistanceLocation: android.location.Location? = null`
    - `private var lastDistanceUpdateTime: Long = 0L`
    - `private var totalDistanceMeters: Double = 0.0`
- Update `CruisingState` data class:
    - Add `val distanceKm: Double = 0.0`
- In `onCreate` -> `locationCallback.onLocationResult`:
    - Implement the 10-second sampling logic.
    - Calculate distance using `lastDistanceLocation.distanceTo(currentLocation)`.
    - Update `totalDistanceMeters` and the state flow.
- In `resetData()`:
    - Reset `lastDistanceLocation`, `lastDistanceUpdateTime`, and `totalDistanceMeters`.

---

### [DashboardScreen.kt](file:///H:/android_prj/cymeter/app/src/main/java/com/example/cymeter/DashboardScreen.kt)

#### [MODIFY] [DashboardScreen.kt](file:///H:/android_prj/cymeter/app/src/main/java/com/example/cymeter/DashboardScreen.kt)
- Add a new `StatCard` to the `LazyVerticalGrid` in `DashboardContent`.
- Display "Total Distance" formatted to 2 decimal places.
- Use `Icons.Rounded.Route` for the icon.

---

### [CruisingViewModel.kt](file:///H:/android_prj/cymeter/app/src/main/java/com/example/cymeter/CruisingViewModel.kt)

#### [MODIFY] [CruisingViewModel.kt](file:///H:/android_prj/cymeter/app/src/main/java/com/example/cymeter/CruisingViewModel.kt)
- Ensure the `resetData` method correctly handles the updated state if necessary (though it likely just uses the default constructor of `CruisingState`).

## Verification Plan

### Manual Verification
- **Start Tracking**: Open the app, grant permissions, and start tracking.
- **Movement Simulation**: Either move physically or use the emulator's "Extended Controls" to simulate movement between different coordinates.
- **10s Accumulation**: Verify that "Total Distance" updates approximately every 10 seconds when moving.
- **Accuracy**: Cross-check the distance with a known route if possible.
- **Stop/Reset**:
    - Verify distance stops increasing when tracking is stopped.
    - Verify "Reset" clears the "Total Distance" to 0.00 km.
