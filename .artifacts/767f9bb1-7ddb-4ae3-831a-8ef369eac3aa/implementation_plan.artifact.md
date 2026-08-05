# Improved Map Screen: Blue Dot & Smart Camera

Enhance the MapScreen with a Google Maps-style location marker and intelligent camera management that respects user zoom and pan adjustments.

## Proposed Changes

### [MapScreen.kt](file:///H:/android_prj/cymeter/app/src/main/java/com/example/cymeter/MapScreen.kt)

#### [MODIFY] [MapScreen.kt](file:///H:/android_prj/cymeter/app/src/main/java/com/example/cymeter/MapScreen.kt)
- **Location Marker**:
    - Remove the manual `marker-source` and `marker-layer` from `setupStyle` and `updateMapData`.
    - Remove `markerDataJson` and its related `remember` block.
    - Inside `getMapAsync`, initialize MapLibre's `LocationComponent`.
    - Configure `LocationComponentOptions` with `pulseEnabled(true)` and `accuracyAlpha(0.2f)`.
    - Set `renderMode = RenderMode.COMPASS` to show direction.
    - Ensure `LocationComponent` is activated only when not in `isViewingHistory` mode.
- **Smart Camera Management**:
    - Introduce `var isAutoFollowEnabled by remember { mutableStateOf(true) }`.
    - Add `map.addOnCameraMoveStartedListener` to the `MaplibreMap` (via `getMapAsync`).
    - If the move reason is `REASON_API_GESTURE`, set `isAutoFollowEnabled = false`.
    - Update the `LaunchedEffect` (observing `pathPoints`):
        - Only call `animateCamera` if `isAutoFollowEnabled` is true AND `!isViewingHistory`.
        - Use `CameraUpdateFactory.newLatLng(lastLatLng)` to preserve the user's current zoom level.
- **UI Improvements**:
    - Add a floating "Recenter" button using an `SmallFloatingActionButton` or `IconButton` with `Icons.Default.MyLocation`.
    - This button should only be visible when `isAutoFollowEnabled` is false and `!isViewingHistory`.
    - Clicking "Recenter" sets `isAutoFollowEnabled = true` and triggers a camera animation to the current location.

## Verification Plan

### Manual Verification
1. **Pulsing Blue Dot**: Start tracking and verify the blue dot with a pulsing effect appears at your location.
2. **Directionality**: Rotate the phone and verify the blue dot's direction cone updates (COMPASS mode).
3. **Smart Zoom**: Manually zoom in or out while moving; verify the map stays at your chosen zoom level while following.
4. **Auto-Follow Interruption**: Manually pan the map; verify that auto-centering stops (`isAutoFollowEnabled` becomes false) and the "Recenter" button appears.
5. **Recenter**: Click the "Recenter" button and verify the camera snaps back to follow the user and the button disappears.
6. **History Mode**: Switch to viewing a historical session; verify the blue dot and auto-follow are disabled, and the full path is shown.
