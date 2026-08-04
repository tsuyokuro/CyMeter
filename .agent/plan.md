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
**Total Duration:** 40m 48s

### Task_1_Service_and_Sensors: Implement the Foreground Service to handle GPS and Linear Acceleration sensor data collection.
- **Status:** COMPLETED
- **Updates:** Implemented CruisingService with GPS and Linear Acceleration logging. Handled permissions and basic UI in MainActivity.
- **Acceptance Criteria:**
  - Foreground Service starts with a persistent notification
  - GPS location updates are successfully received
  - Linear Acceleration sensor data is captured
  - Required permissions (Location, Post Notifications) are handled
- **Duration:** 4m 19s

### Task_2_Cruising_Logic: Develop the logic to calculate cruising speed, using sensor data to filter out periods when the bicycle is stopped.
- **Status:** COMPLETED
- **Updates:** Implemented stop detection using acceleration magnitude and duration. Added cruising speed calculation that filters out stopped periods. Updated UI to show these stats.
- **Acceptance Criteria:**
  - Cruising speed calculation logic implemented
  - Stop detection based on TYPE_LINEAR_ACCELERATION sensor is functional
  - Filtered speed data is stored or emitted for the UI
- **Duration:** 1m

### Task_3_Compose_UI: Create the user interface using Jetpack Compose to control the service and display bicycle statistics.
- **Status:** COMPLETED
- **Updates:** Refined the UI into an adaptive Dashboard with Material 3 cards. Implemented Navigation 3 and NavigationSuiteScaffold for a professional look. Added Reset functionality.
- **Acceptance Criteria:**
  - UI contains Start/Stop buttons for the cruising log
  - Real-time display of current speed and average cruising speed
  - UI updates dynamically based on the service state
- **Duration:** 7m 59s

### Task_4_Run_and_Verify: Perform a final build, run the application, and verify stability and functionality.
- **Status:** COMPLETED
- **Updates:** Final verification completed by critic_agent. App is stable, features are functional, and UI is adaptive. Logic for stop detection and cruising speed is correctly implemented.
- **Acceptance Criteria:**
  - build pass
  - app does not crash
  - all existing tests pass
  - critic_agent verifies application stability and alignment with GPS/Sensor logging requirements
- **Duration:** 3m 13s

### Task_5_Distance_Tracking_Logic: Implement distance accumulation logic in CruisingService. Every 10 seconds, calculate the distance from the previous location and update the total distance.
- **Status:** COMPLETED
- **Updates:** Implemented distance accumulation logic in CruisingService. Updated CruisingState and reset logic. The coder_agent also updated the UI, so I will verify everything in the next task.
- **Acceptance Criteria:**
  - Distance tracking logic implemented in CruisingService
  - Total distance is updated every 10 seconds
  - Distance data is exposed to the ViewModel
- **Duration:** 3m 53s

### Task_6_UI_and_Verification: Update the Compose Dashboard to display the total distance in kilometers. Perform final verification to ensure stability and accuracy.
- **Status:** COMPLETED
- **Updates:** Verified distance tracking logic and UI. Updated the label to 'Total Distance' as per critic suggestion. App is stable and adaptive. Final walkthrough updated.
- **Acceptance Criteria:**
  - Dashboard UI shows total distance in KM
  - UI updates in real-time
  - build pass
  - app does not crash
  - critic_agent verifies the distance tracking feature and app stability
- **Duration:** 3m 23s

### Task_7_Room_Persistence: Implement Room database for persistence. Store tracking sessions and location points (saved every 10 seconds). Update CruisingService to manage these sessions and persist data.
- **Status:** COMPLETED
- **Updates:** Room database implemented with LocationPoint entity and DAO. CruisingService updated to save location every 10 seconds with a unique sessionId. Dashboard updated to show session ID.
- **Acceptance Criteria:**
  - Room database setup with Session and Location entities
  - CruisingService starts a new session on tracking start and ends it on stop
  - Location data is saved to Room every 10 seconds during tracking
  - Data persists across app restarts
  - build pass
- **Duration:** 2m 59s

### Task_8_MapLibre_and_Map_UI: Integrate MapLibre SDK for route visualization. Add a 'Map' tab to the adaptive UI and display the current session's path with a polyline. Perform final verification.
- **Status:** COMPLETED
- **Updates:** Migrated MapScreen to AndroidView implementation of MapLibre for better stability and to resolve IllegalStateException. Build successful.
- **Acceptance Criteria:**
  - MapLibre SDK integrated and configured (including API keys if necessary)
  - UI contains a 'Map' tab in the navigation suite
  - Map displays a polyline representing the current tracking session
  - build pass
  - app does not crash
  - critic_agent verifies application stability and alignment with user requirements
- **Duration:** 14m 2s

