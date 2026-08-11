# Implementation Plan - Upgrade Vico to 3.2.3

Upgrade the Vico charting library from 2.0.1 to 3.2.3. This involves updating the version in the Gradle configuration and adapting the source code to the changes in the Cartesian API introduced in Vico 3.x.

## User Review Required

> [!IMPORTANT]
> This upgrade changes some API names (e.g., `pointConnector` to `interpolator` and `lineSeries` to `lineModel`). While the functionality remains the same, the code will be updated to follow the modern Vico 3.x idiomatic style.

## Proposed Changes

### [Build Configuration]

#### [MODIFY] [libs.versions.toml](file:///H:/android_prj/cymeter/gradle/libs.versions.toml)
- Update `vico` version from `2.0.1` to `3.2.3`.

### [UI / ViewModel]

#### [MODIFY] [ChartsScreen.kt](file:///H:/android_prj/cymeter/app/src/main/java/com/example/cymeter/ui/ChartsScreen.kt)
- Update parameters in `LineCartesianLayer.rememberLine`:
    - Rename `pointConnector` to `interpolator`.
    - Update value from `LineCartesianLayer.PointConnector.cubic(...)` to `LineCartesianLayer.Interpolator.cubic(...)`.
- Verify and update imports for `com.patrykandpatrick.vico` packages if any have been relocated (though most should remain the same from 2.0.1).

#### [MODIFY] [CruisingViewModel.kt](file:///H:/android_prj/cymeter/app/src/main/java/com/example/cymeter/CruisingViewModel.kt)
- Update `CartesianChartModelProducer.runTransaction` blocks:
    - Replace deprecated `lineSeries` with `lineModel`.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:assembleDebug` to ensure the project compiles with the new library version.

### Manual Verification
- Open the "Charts" screen in the app.
- Verify that both the Speed and Altitude charts render correctly.
- Ensure that zooming and scrolling synchronized across charts still works as expected.
- Verify that "History" sessions also display charts correctly.
