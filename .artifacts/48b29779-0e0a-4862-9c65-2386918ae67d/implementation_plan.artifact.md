# CyMeter Dashboard Refinement Implementation Plan

Refine the CyMeter UI into a professional Dashboard using Material 3, Navigation 3, and Adaptive components.

## Proposed Changes

### [Component Name] Data Layer

#### [MODIFY] [CruisingService.kt](file:///H:/android_prj/cymeter/app/src/main/java/com/example/cymeter/CruisingService.kt)
- Add `resetData()` method to clear session statistics (total moving time, avg speed, etc.).

### [Component Name] UI Layer

#### [NEW] [DashboardScreen.kt](file:///H:/android_prj/cymeter/app/src/main/java/com/example/cymeter/DashboardScreen.kt)
- Create a professional dashboard using `Card` components for stats.
- Use expressive M3 design.
- Include Start/Stop and Reset controls.
- Handle service unbound state gracefully.

#### [MODIFY] [MainActivity.kt](file:///H:/android_prj/cymeter/app/src/main/java/com/example/cymeter/MainActivity.kt)
- Refactor to use `Navigation 3` for screen management.
- Integrate `NavigationSuiteScaffold` for adaptive navigation (phone/tablet).
- Move permission handling logic into the navigation flow or a wrapper.

### [Component Name] Build Configuration

#### [MODIFY] [libs.versions.toml](file:///H:/android_prj/cymeter/gradle/libs.versions.toml)
- Add `androidx-compose-material3-adaptive-navigation-suite` dependency.

#### [MODIFY] [build.gradle.kts](file:///H:/android_prj/cymeter/app/build.gradle.kts)
- Add the new dependency.

## Verification Plan

### Automated Tests
- Build the project using `./gradlew assembleDebug`.

### Manual Verification
- Verify the adaptive layout by resizing the window (simulated in preview).
- Verify Start/Stop/Reset functionality.
- Verify status colors (Green for Moving, Red/Amber for Stopped).
