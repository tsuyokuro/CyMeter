# Tasks - Session Persistence and UI Retention

- [ ] **Phase 1: Database & Data Logic**
    - [ ] Update `LocationDao` if necessary (e.g., add `getLatestPointForSession` if needed).
    - [ ] Ensure `LocationPoint` entity is robust for summary recovery.
- [ ] **Phase 2: CruisingService Enhancements**
    - [ ] Implement `loadLastSessionFromDb()` in `CruisingService`.
    - [ ] Update `onCreate`/`onStartCommand` to initialize from DB.
    - [ ] Refine `stopTracking()` and `onDestroy()` to ensure final data save.
    - [ ] Add "isTracking" persistence (SharedPreferences) to distinguish intentional stop vs OS kill.
- [ ] **Phase 3: CruisingViewModel Updates**
    - [ ] Refine `loadLastSession()` initialization.
    - [ ] Ensure `updateState` handles service connection/disconnection smoothly without data loss.
- [ ] **Phase 4: UI & Verification**
    - [ ] Verify Dashboard data retention after "Stop Tracking".
    - [ ] Verify session recovery after simulated app kill.
    - [ ] Run build and check for any regressions.
