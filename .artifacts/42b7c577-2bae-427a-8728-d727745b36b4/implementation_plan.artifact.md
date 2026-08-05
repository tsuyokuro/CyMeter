# Implementation Plan - Slide Animations for Navigation

Change the screen transition animation in CyMeter to a horizontal slide to provide a more dynamic and intuitive user experience when switching between tabs or navigating through the app.

## Proposed Changes

### [MainActivity.kt](file:///H:/android_prj/cymeter/app/src/main/java/com/example/cymeter/MainActivity.kt)

- **Add Imports**: Include the necessary Compose animation and Navigation 3 classes.
- **Update `NavDisplay`**:
    - Implement `transitionSpec` to handle forward and backward horizontal slides based on tab index comparison. This ensures that switching from a left tab to a right tab slides in from the right, and vice versa.
    - Implement `popTransitionSpec` to provide a consistent backward slide for stack pop operations.
- **Animation Details**:
    - **Forward**: `slideInHorizontally { it }` + `slideOutHorizontally { -it }`
    - **Backward**: `slideInHorizontally { -it }` + `slideOutHorizontally { it }`
    - Use `tween(300)` for a smooth transition duration.

## Verification Plan

### Manual Verification
1. **Build and Run**: Deploy the app to an emulator or physical device.
2. **Tab Switching**:
    - Click on **Map** from **Dashboard**: Observe a right-to-left slide.
    - Click on **History** from **Map**: Observe a right-to-left slide.
    - Click on **Dashboard** from **History**: Observe a left-to-right slide.
    - Click on **Map** from **History**: Observe a left-to-right slide.
3. **History Selection**:
    - In **History**, select a session: Observe the transition back to **Dashboard** with a left-to-right slide (since Dashboard is at index 0).
4. **Visual Polish**: Ensure the animation is smooth and doesn't cause any flickering or UI glitches.
