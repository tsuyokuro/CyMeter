package io.github.tsuyokuro.cymeter

import android.os.Environment
import android.util.Log
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.tsuyokuro.cymeter.db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Instrumental test that simulates a ride using data from the actual database on the device.
 * It feeds the points of the last recorded session into CruisingLogicManager and logs the results.
 */
@RunWith(AndroidJUnit4::class)
class CruisingLogicManagerSimulationTest {

    private val TAG = "SimulationTest"

    @Test
    fun simulateLastSession() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext

        val internalDbFile = context.getDatabasePath("simulation_temp.db")
        
        val success = copyBackupToInternal(
            instrumentation = instrumentation,
            backupFileName = "cymeter_backup.db",
            targetFile = internalDbFile
        )

        if (!success) {
            Log.e(TAG, "Simulation aborted due to database copy failure.")
            return@runBlocking
        }

        Log.i(TAG, "Opening temporary database (${internalDbFile.length()} bytes)...")
        
        // Open the temporary file as a Room database
        val database = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            internalDbFile.absolutePath
        )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
            
        val sessionDao = database.sessionDao()
        val locationDao = database.locationDao()

        // 1. Get the latest session
        val lastSession = sessionDao.getLatestSession()
        if (lastSession == null) {
            Log.e(TAG, "No sessions found in database.")
            return@runBlocking
        }

        Log.i(TAG, "Simulating Session ID: ${lastSession.id} started at ${lastSession.startTime}")

        // 2. Get all points for this session
        val points = locationDao.getPointsBySessionId(lastSession.id)
        if (points.isEmpty()) {
            Log.e(TAG, "No location points found for session ${lastSession.id}.")
            return@runBlocking
        }

        Log.i(TAG, "Number of points to process: ${points.size}")

        // 3. Initialize LogicManager (using 5 km/h threshold as default)
        val thresholdMps = 5.0f / 3.6f
        val logicManager = CruisingLogicManager(speedThresholdMps = thresholdMps)

        // 4. Feed points one by one
        var prevDistance = 0f
        points.forEachIndexed { index, point ->
            val distanceIncrement = if (index == 0) 0f else (point.totalDistanceMeters - prevDistance).coerceAtLeast(0f)
            prevDistance = point.totalDistanceMeters

            val result = logicManager.onLocationUpdate(
                currentTime = point.timestamp,
                speed = point.speed,
                distanceIncrement = distanceIncrement
            )

            // Log every 50 points or so to avoid flooding, but log changes in key metrics
            if (index % 50 == 0 || index == points.lastIndex) {
                Log.d(TAG, "[#$index] Dist: %.2f km, Speed: %.1f km/h, Rolling: %.1f km/h, Avg Cruising: %.1f km/h, Best Seg: %.1f km/h".format(
                    result.totalDistanceMeters / 1000f,
                    result.currentSpeed * 3.6f,
                    result.rollingSpeed * 3.6f,
                    result.representativeCruisingSpeed * 3.6f,
                    result.bestSegmentSpeed * 3.6f
                ))
            }
        }

        // 5. Get final summary
        val finalResult = logicManager.stop(System.currentTimeMillis()) // timestamp doesn't matter much here if already at end
        
        Log.i(TAG, "=== SIMULATION FINAL SUMMARY ===")
        Log.i(TAG, "Total Distance: %.3f km".format(finalResult.totalDistanceMeters / 1000f))
        Log.i(TAG, "Total Avg Speed: %.2f km/h".format(finalResult.avgSpeed * 3.6f))
        Log.i(TAG, "Representative Cruising Speed: %.2f km/h".format(finalResult.representativeCruisingSpeed * 3.6f))
        Log.i(TAG, "Best Segment Speed: %.2f km/h".format(finalResult.bestSegmentSpeed * 3.6f))
        Log.i(TAG, "Best Segment Range: %.2f km - %.2f km (%.2f km)".format(
            finalResult.bestSegmentStartKm,
            finalResult.bestSegmentEndKm,
            finalResult.bestSegmentDistance / 1000f
        ))
    // 6. Close database
        database.close()
    }

    private fun copyBackupToInternal(
        instrumentation: android.app.Instrumentation,
        backupFileName: String,
        targetFile: File
    ): Boolean {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val backupFile = File(downloadDir, backupFileName)
        val backupFilePath = backupFile.absolutePath

        targetFile.parentFile?.mkdirs()

        Log.i(TAG, "Diagnosing backup file visibility: $backupFilePath")
        val lsResult = runShellCommand(instrumentation, "ls -l $backupFilePath")
        Log.i(TAG, "Shell 'ls' result: $lsResult")

        if (lsResult.contains("No such file")) {
            Log.e(TAG, "Backup file NOT found at: $backupFilePath")
            return false
        }

        Log.i(TAG, "Copying backup database using streaming shell command...")
        try {
            val pfd = instrumentation.uiAutomation.executeShellCommand("cat $backupFilePath")
            FileInputStream(pfd.fileDescriptor).use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            pfd.close()
        } catch (e: Exception) {
            Log.e(TAG, "Copy failed: ${e.message}")
            return false
        }

        return if (targetFile.exists() && targetFile.length() > 0L) {
            Log.i(TAG, "Database copy successful: ${targetFile.length()} bytes")
            true
        } else {
            Log.e(TAG, "Database copy failed: target file empty or not found")
            false
        }
    }

    private fun runShellCommand(instrumentation: android.app.Instrumentation, cmd: String): String {
        val pfd = instrumentation.uiAutomation.executeShellCommand(cmd)
        val result = FileInputStream(pfd.fileDescriptor).bufferedReader().readText()
        pfd.close()
        return result
    }
}
