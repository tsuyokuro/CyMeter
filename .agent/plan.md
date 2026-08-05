# Project Plan

CyMeter: An Android app to log bicycle cruising speed using GPS.
Key Features:
- Log cruising speed using GPS.
- Use Linear Acceleration sensor (TYPE_LINEAR_ACCELERATION) to detect stops (e.g., waiting at signals) and exclude them from cruising speed calculations.
- Data collection runs as a Foreground Service to ensure continuous logging even when the app is in the background.
- User interface to start/stop logging and view real-time/logged data.

## Project Brief

# Project Brief: CyMeter

CyMeter is a performance-oriented Android application designed for cyclists to track their actual cruising speed. By leveraging GPS data in tandem with linear acceleration sensors, the app intelligently distinguishes between active riding and stationary periods (such as waiting at traffic lights), providing a more accurate reflection of cruising performance.

## Features (MVP)

*   **Intelligent Cruising Log**: Real-time GPS-based speed tracking that automatically calculates and logs cruising metrics.
*   **Automatic Stop Detection**: Uses the `TYPE_LINEAR_ACCELERATION` sensor to detect when the bicycle is stationary, excluding those intervals from the average cruising speed to ensure data purity.
*   **Persistent Background Tracking**: A robust Foreground Service ensures that speed logging remains active and uninterrupted, even when the screen is off or the user is navigating with another app.
*   **Real-Time Adaptive Dashboard**: A responsive interface that provides immediate feedback on current speed, status (Cruising vs. Stopped), and session statistics, optimized for all screen sizes.

## High-Level Technical Stack

*   **Language**: Kotlin
*   **UI Framework**: Jetpack Compose
*   **Navigation**: **Jetpack Navigation 3** (State-driven backstack management using `NavDisplay`).
*   **Adaptive Strategy**: **Compose Material 3 Adaptive** (Utilizing `NavigationSuiteScaffold` for device-responsive navigation and `ListDetailPaneScaffold` for data visualization).
*   **Concurrency & Streams**: Kotlin Coroutines and Flow for handling high-frequency sensor data and location updates.
*   **Background Processing**: Android Foreground Service with persistent notifications.
*   **Sensor Integration**: Google Play Services Fused Location Provider (GPS) and Android `SensorManager` (Linear Acceleration).

> [!NOTE]
> This MVP focuses on real-time data processing and high-accuracy logging without local persistence, prioritizing a lightweight and responsive user experience.

## Implementation Steps
**Total Duration:** 6m 12s

### Task_11_Shared_State_and_History_Selection: Implement shared navigation state and session loading. Create or update a shared ViewModel to manage the selected history session ID and 'Viewing History' mode. Update HistoryScreen to trigger session selection and fetch session data.
- **Status:** COMPLETED
- **Updates:** Implemented historical session selection in HistoryScreen. Updated CruisingViewModel to handle viewing mode and shared state. Added 'Viewing History' indicators and 'Back to Live' logic. Build successful.
- **Acceptance Criteria:**
  - History item selection updates the shared ViewModel state
  - Selected session's summary and location points are fetched from Room
  - 'Viewing History' mode is correctly toggled when a session is selected
- **Duration:** 2m 38s

### Task_12_Dashboard_Map_Integration_and_Verification: Update Dashboard and Map views to display historical data. Add 'Viewing History' mode indicators and an exit mechanism. Perform final verification for stability and UI consistency.
- **Status:** COMPLETED
- **Updates:** Polished Dashboard and Map views for historical viewing. Implemented camera bounds for historical paths. Added 'Back to Live' functionality. Walkthrough updated. Ready for final verification.
- **Acceptance Criteria:**
  - Dashboard shows statistics for the historical session
  - Map displays the route for the historical session
  - UI includes a clear 'Viewing History' indicator and a way to exit this mode
  - build pass
  - app does not crash
  - critic_agent verifies history viewing functionality and overall app stability
- **Duration:** 1m 1s

### Task_13_Map_UI_and_Camera_Refinement: Implement MapScreen refinements: replace the location icon with a pulsing blue dot using the LocationComponent, add smart camera follow logic that pauses on user interaction (pan/zoom), and integrate a 'Recenter' button to resume tracking.
- **Status:** COMPLETED
- **Updates:** Implemented MapLibre LocationComponent for pulsing blue dot. Added smart camera follow logic with manual interaction detection. Integrated a Recenter button to resume auto-follow. Removed old manual marker code.
- **Acceptance Criteria:**
  - Map displays a pulsing blue dot at the user's current location
  - Camera follows the user's location by default
  - Camera tracking pauses when the user manually pans or zooms the map
  - A 'Recenter' button is visible when tracking is paused
  - Clicking 'Recenter' button resumes camera tracking and centers on the user
- **Duration:** 2m 33s

### Task_14_Run_and_Verify: Run the application and perform a final verification of all features, specifically focusing on the map refinements and general stability.
- **Status:** IN_PROGRESS
- **Acceptance Criteria:**
  - Map refinements (pulsing dot, smart follow, recenter) work as expected
  - build pass
  - app does not crash
  - make sure all existing tests pass
  - critic_agent verifies application stability and alignment with user requirements
- **StartTime:** 2026-08-05 20:42:16 JST

