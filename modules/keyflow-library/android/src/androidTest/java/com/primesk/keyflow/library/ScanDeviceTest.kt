package com.primesk.keyflow.library

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScanDeviceTest {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val context = instrumentation.targetContext
  @get:Rule val migrationHelper = MigrationTestHelper(instrumentation, LibraryDatabase::class.java)

  @Test fun migrationPreservesExistingTrackAndAddsScanState() {
    val name = "library-migration-test"
    migrationHelper.createDatabase(name, 1).apply {
      execSQL("INSERT INTO tracks VALUES ('stable-id','primary:Music/test.wav','test.wav','Test','Artist','Album','Artist',1000,'audio/wav',100,100,100,'primary:Music/','Music',NULL,1)")
      close()
    }
    migrationHelper.runMigrationsAndValidate(name, 2, true, LibraryDatabase.MIGRATION_1_2).apply {
      query("SELECT id,title FROM tracks").use { assertTrue(it.moveToFirst()); assertEquals("stable-id", it.getString(0)); assertEquals("Test", it.getString(1)) }
      execSQL("INSERT INTO scan_state VALUES (1,'running',1,1,NULL,NULL)")
      close()
    }
    context.deleteDatabase(name)
  }

  @Test fun migrationToThreePreservesSourcesLocationsAndExistingFolderRules() {
    val name = "library-migration-3-test"
    migrationHelper.createDatabase(name, 2).apply {
      execSQL("INSERT INTO tracks VALUES ('stable-id','primary:Music/test.wav','test.wav','Test','Artist','Album','Artist',1000,'audio/wav',100,100,100,'primary:Music/','Music',NULL,1)")
      execSQL("INSERT INTO sources VALUES ('device','device','external_primary','Device',1,'available',NULL,100,NULL)")
      execSQL("INSERT INTO locations VALUES ('device','content://test/1','stable-id','scan','excluded')")
      execSQL("INSERT INTO folder_rules VALUES ('device','primary:Music/',0)")
      close()
    }
    migrationHelper.runMigrationsAndValidate(name, 3, true, LibraryDatabase.MIGRATION_2_3).apply {
      query("SELECT id FROM tracks").use { assertTrue(it.moveToFirst()); assertEquals("stable-id", it.getString(0)) }
      query("SELECT included FROM folder_rules").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
      query("SELECT COUNT(*) FROM locations").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
      execSQL("INSERT INTO library_folder_rules VALUES ('primary:Music/',0)")
      close()
    }
    context.deleteDatabase(name)
  }

  @Test fun realDeviceScanRescanCancelAndPermissionLossAreSafe() {
    val name = "library-scan-test.db"
    context.deleteDatabase(name)
    val db = Room.databaseBuilder(context, LibraryDatabase::class.java, name).addMigrations(LibraryDatabase.MIGRATION_1_2).build()
    val repository = LibraryRepository(context, db)
    val engine = ScanEngine(repository, resume = false)
    try {
      if (!engine.hasPermission()) {
        val activity = instrumentation.startActivitySync(android.content.Intent(context, PermissionProbeActivity::class.java)
          .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        val permissionDeadline = System.currentTimeMillis() + 120_000
        while (!engine.hasPermission() && System.currentTimeMillis() < permissionDeadline) Thread.sleep(100)
        instrumentation.runOnMainSync { activity.finish() }
        assertTrue("Allow audio access in the test app's permission dialog", engine.hasPermission())
      }
      engine.addDeviceSources()
      engine.start(); waitForScan(engine)
      assertEquals("completed", engine.state()["status"])
      val first = db.dao().tracks(LibraryQueries.tracks(emptyMap()))
      // Phone collection is real; this assertion also establishes usable MediaStore access.
      assertTrue("Expected the owner's existing local collection", first.isNotEmpty())
      val count = db.dao().count(LibraryQueries.count(emptyMap()))
      val firstId = first.first().id
      engine.start(); waitForScan(engine)
      assertEquals(count, db.dao().count(LibraryQueries.count(emptyMap())))
      assertNotNull(db.dao().track(firstId))
      engine.start(); engine.cancel(); waitForScan(engine)
      assertEquals("cancelled", engine.state()["status"])
      assertEquals(count, db.dao().count(LibraryQueries.count(emptyMap())))
      // Model the persisted denied state. Revoking this test APK's own permission would
      // terminate instrumentation; OS permission revocation is tested on the real app separately.
      for (source in db.dao().sources()) db.dao().putSource(source.copy(access = "permission"))
      assertEquals("permission", repository.availability(firstId))
      assertNotNull(db.dao().track(firstId))
      engine.refreshAvailability()
      assertEquals("available", repository.availability(firstId))
    } finally { engine.shutdown(); db.close(); context.deleteDatabase(name) }
  }

  private fun waitForScan(engine: ScanEngine) {
    val deadline = System.currentTimeMillis() + 180_000
    while (engine.state()["status"] == "running" && System.currentTimeMillis() < deadline) Thread.sleep(50)
    assertNotEquals("Scan timed out", "running", engine.state()["status"])
    // Allow the final coroutine cleanup before another command or closing the test DB.
    Thread.sleep(100)
  }

  @Test fun automaticScanningIsOptInAndPersistsWithoutStartingARefresh() {
    val name = "library-scan-setting-test.db"
    context.deleteDatabase(name)
    val preferences = context.getSharedPreferences("keyflow-library-settings", android.content.Context.MODE_PRIVATE)
    val wasPresent = preferences.contains("automatic-scanning")
    val previous = preferences.getBoolean("automatic-scanning", false)
    preferences.edit().remove("automatic-scanning").commit()
    val db = Room.databaseBuilder(context, LibraryDatabase::class.java, name).build()
    val engine = ScanEngine(LibraryRepository(context, db), resume = false)
    val reopened = ScanEngine(LibraryRepository(context, db), resume = false)
    try {
      db.dao().putSource(Source("device:external_primary", "device", "external_primary", "Device"))
      assertFalse(engine.automaticScanning())
      engine.requestRefresh()
      Thread.sleep(1800)
      assertEquals("idle", engine.state()["status"])
      engine.setAutomaticScanning(true)
      assertTrue(reopened.automaticScanning())
      engine.requestRefresh()
      engine.setAutomaticScanning(false)
      Thread.sleep(1800)
      assertEquals("idle", engine.state()["status"])
      assertFalse(reopened.automaticScanning())
    } finally {
      engine.shutdown(); reopened.shutdown(); db.close(); context.deleteDatabase(name)
      val editor = preferences.edit()
      if (wasPresent) editor.putBoolean("automatic-scanning", previous) else editor.remove("automatic-scanning")
      editor.commit()
    }
  }

  @Test fun persistedInterruptedScanResumesAndKeepsTrackIds() {
    val name = "library-interrupted-test.db"
    context.deleteDatabase(name)
    val db = Room.databaseBuilder(context, LibraryDatabase::class.java, name).build()
    var engine = ScanEngine(LibraryRepository(context, db), resume = false)
    try {
      assertTrue("Audio permission must be granted for the test package", engine.hasPermission())
      engine.addDeviceSources(); engine.start(); waitForScan(engine)
      val before = db.dao().tracks(LibraryQueries.tracks(emptyMap())).map { it.id }
      engine.shutdown()
      db.dao().putScan(SavedScan(status = "running", discovered = 25, processed = 25))
      for (source in db.dao().sources()) db.dao().putSource(source.copy(checkpoint = "interrupted-token"))
      engine = ScanEngine(LibraryRepository(context, db), resume = true)
      val deadline = System.currentTimeMillis() + 180_000
      while (engine.state()["status"] != "completed" && System.currentTimeMillis() < deadline) Thread.sleep(50)
      assertEquals("completed", engine.state()["status"])
      assertEquals(before, db.dao().tracks(LibraryQueries.tracks(emptyMap())).map { it.id })
      assertTrue(db.dao().sources().all { it.checkpoint == null })
      Thread.sleep(100)
    } finally { engine.shutdown(); db.close(); context.deleteDatabase(name) }
  }
}
