package com.primesk.keyflow.library

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
class LibraryDatabaseDeviceTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private fun open(name: String) = Room.databaseBuilder(context, LibraryDatabase::class.java, name).build()
  private fun track(i: Int, identity: String = "primary:Music/$i.mp3") = Track(identity = identity,
    fileName = "$i.mp3", title = "Song ${i.toString().padStart(5, '0')}", artist = "Artist ${i % 20}",
    album = "Album ${i % 100}", albumArtist = "Artist ${i % 20}", folderKey = "primary:Music/", folderName = "Music")

  @Test fun persistenceDeduplicationPaginationAndSearch() {
    val name = "library-device-test.db"
    context.deleteDatabase(name)
    var db = open(name)
    try {
      val dao = db.dao()
      dao.putSource(Source("device:primary", "device", "primary", "Device"))
      dao.putSource(Source("folder:music", "folder", "tree", "Music folder"))
      db.runInTransaction {
        for (i in 0 until 125) {
          val t = track(i); dao.putTrack(t)
          dao.putLocation(Location("device:primary", "content://test/$i", t.id, "scan1"))
        }
        val first = dao.byIdentity("primary:Music/0.mp3")!!
        dao.putLocation(Location("folder:music", "content://documents/0", first.id, "scan2"))
      }
      val stableId = dao.byIdentity("primary:Music/0.mp3")!!.id
      db.close(); db = open(name)
      val repository = LibraryRepository(context, db)
      assertEquals(stableId, db.dao().byIdentity("primary:Music/0.mp3")!!.id)
      assertEquals(125.0, repository.tracks(emptyMap())["total"])
      val page1 = db.dao().tracks(LibraryQueries.tracks(mapOf("offset" to 0)))
      val page2 = db.dao().tracks(LibraryQueries.tracks(mapOf("offset" to 50)))
      assertEquals(50, page1.size); assertEquals(50, page2.size)
      assertTrue(page1.map { it.id }.intersect(page2.map { it.id }.toSet()).isEmpty())
      assertEquals("single-term prefix", 50, db.dao().tracks(LibraryQueries.tracks(mapOf("search" to "So"))).size)
      assertEquals("multi-term prefix", 1, db.dao().tracks(LibraryQueries.tracks(mapOf("search" to "Song 00073"))).size)
      assertEquals(0, db.dao().tracks(LibraryQueries.tracks(mapOf("search" to "\" OR nonexistent"))).size)
      assertEquals(20, db.dao().groups(LibraryQueries.groups("artists", emptyMap())).size)
      // Only successful source reconciliation marks old locations missing. Another alias stays usable.
      db.dao().reconcile("device:primary", "scan3")
      assertEquals("available", repository.availability(stableId))
      db.dao().removeSource("folder:music")
      assertEquals("missing", repository.availability(stableId))
      assertEquals(stableId, db.dao().track(stableId)!!.id)
    } finally { db.close(); context.deleteDatabase(name) }
  }

  @Test fun tenThousandTracksQueryInBoundedPages() {
    val name = "library-benchmark.db"
    context.deleteDatabase(name)
    val db = open(name)
    try {
      val dao = db.dao()
      dao.putSource(Source("device:primary", "device", "primary", "Device"))
      val insertion = measureTimeMillis { db.runInTransaction {
        for (i in 0 until 10_000) {
          val t = track(i); dao.putTrack(t)
          dao.putLocation(Location("device:primary", "content://test/$i", t.id, "scan"))
        }
      } }
      val repository = LibraryRepository(context, db)
      var page: Map<String, Any> = emptyMap()
      val queryTime = measureTimeMillis { page = repository.tracks(mapOf("offset" to 9950)) }
      assertEquals(10_000.0, page["total"])
      assertEquals(50, (page["items"] as List<*>).size)
      assertEquals(false, page["hasMore"])
      assertTrue("50-item query took ${queryTime}ms", queryTime < 1500)
      android.util.Log.i("KeyflowLibraryBenchmark", "10000 insert=${insertion}ms finalPage=${queryTime}ms")
    } finally { db.close(); context.deleteDatabase(name) }
  }

  @Test fun resolvesRealFileAndRejectsLostAccessWithoutReplacingPlayback() {
    val name = "library-resolution.db"
    context.deleteDatabase(name)
    val db = open(name)
    val fixture = java.io.File(context.cacheDir, "library-tone.wav")
    InstrumentationRegistry.getInstrumentation().context.assets.open("audio/tone.wav").use { input ->
      fixture.outputStream().use { output -> input.copyTo(output) }
    }
    try {
      val repository = LibraryRepository(context, db)
      val t = track(0)
      db.dao().putSource(Source("folder:test", "folder", "test", "Test"))
      db.dao().putTrack(t)
      db.dao().putLocation(Location("folder:test", android.net.Uri.fromFile(fixture).toString(), t.id, "scan"))
      val resolved = repository.resolve(t.id)
      assertEquals(android.net.Uri.fromFile(fixture).toString(), resolved["uri"])
      assertEquals(t.title, resolved["title"])
      fixture.delete()
      try { repository.resolve(t.id); fail("Missing audio must not resolve") }
      catch (_: IllegalStateException) { }
      assertEquals("missing", repository.availability(t.id))
    } finally { db.close(); fixture.delete(); context.deleteDatabase(name) }
  }

  @Test fun sortingGroupingAndDisabledSourcesPreserveReferences() {
    val name = "library-rules-test.db"
    context.deleteDatabase(name)
    val db = open(name)
    try {
      val dao = db.dao()
      val source = Source("source", "folder", "tree", "Folder")
      dao.putSource(source)
      val first = track(1).copy(title = "Same", album = "Same album", albumArtist = "Artist A", durationMs = 1000, dateAdded = 2, folderKey = "primary:Music/Exclude/")
      val second = track(2).copy(title = "Same", album = "Same album", albumArtist = "Artist B", durationMs = 500, dateAdded = 1)
      for (t in listOf(first, second)) { dao.putTrack(t); dao.putLocation(Location(source.id, "content://test/${t.id}", t.id, "scan")) }
      assertEquals(2, dao.groups(LibraryQueries.groups("albums", emptyMap())).size)
      assertEquals(second.id, dao.tracks(LibraryQueries.tracks(mapOf("sort" to "duration"))).first().id)
      assertEquals(first.id, dao.tracks(LibraryQueries.tracks(mapOf("sort" to "dateAdded", "descending" to true))).first().id)
      val order = dao.tracks(LibraryQueries.tracks(emptyMap())).map { it.id }
      assertEquals(order.sorted(), order)
      dao.putRule(FolderRule(source.id, first.folderKey, false))
      assertEquals(1L, dao.count(LibraryQueries.count(emptyMap())))
      assertEquals(2L, dao.count(LibraryQueries.count(mapOf("sourceId" to source.id, "includeExcluded" to true))))
      dao.putSource(source.copy(enabled = false))
      assertEquals(0L, dao.count(LibraryQueries.count(emptyMap())))
      assertNotNull(dao.track(first.id)); assertNotNull(dao.track(second.id))
    } finally { db.close(); context.deleteDatabase(name) }
  }

  @Test fun exclusionsUpdateImmediatelyAcrossOverlappingSourcesAndMissingFiles() {
    val name = "library-global-exclusions.db"
    context.deleteDatabase(name)
    var db = open(name)
    try {
      val parent = track(1).copy(folderKey = "primary:Music/", folderName = "Music")
      val child = track(2).copy(folderKey = "primary:Music/Keep/", folderName = "Keep")
      val other = track(3).copy(folderKey = "primary:MusicElse/", folderName = "MusicElse")
      val dao = db.dao()
      for (id in listOf("device", "folder")) {
        dao.putSource(Source(id, "folder", id, id))
        for (t in listOf(parent, child, other)) { dao.putTrack(t); dao.putLocation(Location(id, "content://$id/${t.id}", t.id, "scan")) }
      }
      dao.putRule(FolderRule("device", parent.folderKey, false))
      assertEquals(3L, dao.count(LibraryQueries.count(emptyMap()))) // The other source still includes these files.
      dao.putLibraryRule(LibraryFolderRule(parent.folderKey, false))
      assertEquals(listOf(other.id), dao.tracks(LibraryQueries.tracks(emptyMap())).map { it.id })
      assertEquals("excluded", LibraryRepository(context, db).availability(parent.id))
      assertEquals(3, dao.groups(LibraryQueries.groups("folders", mapOf("includeExcluded" to true))).size)
      assertEquals(1, dao.groups(LibraryQueries.groups("folders", mapOf("includeExcluded" to true, "folderSearch" to "Keep"))).size)
      dao.putLibraryRule(LibraryFolderRule(child.folderKey, true))
      assertEquals(2L, dao.count(LibraryQueries.count(emptyMap())))
      dao.setSourceLocations("folder", "missing")
      dao.removeRule("device", parent.folderKey)
      assertEquals(2L, dao.count(LibraryQueries.count(emptyMap())))
      db.close(); db = open(name)
      assertEquals(2, db.dao().libraryRules().size)
      db.dao().removeLibraryRule(parent.folderKey)
      assertEquals(3L, db.dao().count(LibraryQueries.count(emptyMap())))
      assertNotNull(db.dao().track(parent.id))
    } finally { db.close(); context.deleteDatabase(name) }
  }
}
