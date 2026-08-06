# Fix Room Migration Crash

The application crashes on startup due to a `SQLiteException: duplicate column name: totalMovingTime` during the Room migration from version 4 to 5. This indicates that the `totalMovingTime` column already exists in the `sessions` table when the migration attempts to add it.

## User Review Required

> [!IMPORTANT]
> The fix involves making Room migrations idempotent by checking for the existence of columns before attempting to add them. This prevents crashes if a migration partially succeeded or if the schema was manually altered.

## Proposed Changes

### Database Component

#### [MODIFY] [AppDatabase.kt](file:///H:/android_prj/cymeter/app/src/main/java/com/example/cymeter/db/AppDatabase.kt)
- Add a helper function `columnExists` to check if a column exists in a table.
- Update `MIGRATION_4_5` to safely add `avgSpeed`, `totalDistance`, `maxSpeed`, and `totalMovingTime` to the `sessions` table only if they don't already exist.
- Update `MIGRATION_5_6` to use the helper function for consistency.
- Ensure `fallbackToDestructiveMigration(dropAllTables = true)` is properly configured.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:assembleDebug` to ensure the code compiles.
- Since this is a migration issue that depends on existing device state, manual verification on a device with the old database is ideal. However, making migrations idempotent is a standard safety practice.

### Manual Verification
- Deploy the app to a device/emulator and verify it no longer crashes on startup.
