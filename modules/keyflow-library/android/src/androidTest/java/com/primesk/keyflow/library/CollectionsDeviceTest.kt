package com.primesk.keyflow.library

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
class CollectionsDeviceTest {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val context = instrumentation.targetContext
  @get:Rule val helper = MigrationTestHelper(instrumentation, LibraryDatabase::class.java)
  private val audio = mapOf<String, Any?>("uri" to "file:///local/song.mp3", "title" to "Song", "artist" to "Artist", "fileName" to "song.mp3")
  @Test fun migrationPreservesLibraryAndRecovery() {
    val name = "collections-migration.db"; context.deleteDatabase(name)
    helper.createDatabase(name, 4).apply {
      execSQL("INSERT INTO playback_recovery (id,snapshot) VALUES (1,'preserve-me')")
      execSQL("INSERT INTO library_folder_rules (path,included) VALUES ('primary:Music/',0)")
      close()
    }
    helper.runMigrationsAndValidate(name, 5, true, LibraryDatabase.MIGRATION_4_5).apply {
      query("SELECT snapshot FROM playback_recovery").use { assertTrue(it.moveToFirst()); assertEquals("preserve-me", it.getString(0)) }
      query("SELECT included FROM library_folder_rules").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
      close()
    }
    context.deleteDatabase(name)
  }
  @Test fun duplicateOrderEditingFavoritesAndHistorySurviveReopen() {
    val name = "collections-persistence.db"; context.deleteDatabase(name)
    var db = Room.databaseBuilder(context, LibraryDatabase::class.java, name).build()
    try {
      var repo = CollectionsRepository(context, db)
      val saved = repo.saveQueue("  Practice  ", listOf(audio to "track", (audio + ("title" to "Second")) to "other", audio to "track"))
      var rows = repo.dao.entries(saved.id, 0, 50)
      assertEquals("Practice", saved.name); assertEquals(3, rows.map { it.id }.distinct().size)
      assertEquals(listOf("track", "other", "track"), rows.map { it.trackId })
      val moved = rows[2].id; repo.move(moved, -1)
      rows = repo.dao.entries(saved.id, 0, 50); assertEquals(moved, rows[1].id)
      repo.remove(rows[0].id); assertEquals(listOf(0, 1), repo.dao.entries(saved.id, 0, 50).map { it.position })
      repo.add(saved.id, audio, "track"); repo.rename(saved.id, "Rehearsal")
      repo.favorite(audio, "track", true); repo.favorite(audio, "track", true); assertEquals(1L, repo.dao.favoriteCount())
      repo.record(audio, "track"); repo.record(audio, "other"); repo.record(audio, "track"); assertEquals(2L, repo.dao.recentCount())
      db.close(); db = Room.databaseBuilder(context, LibraryDatabase::class.java, name).build(); repo = CollectionsRepository(context, db)
      assertEquals("Rehearsal", repo.dao.playlist(saved.id)!!.name); assertEquals(3, repo.dao.entryCount(saved.id))
      assertTrue(repo.dao.isFavorite("track:track")); assertEquals(2L, repo.dao.recentCount())
      repo.favorite(audio, "track", false); assertFalse(repo.dao.isFavorite("track:track"))
      repo.dao.deletePlaylist(saved.id); assertEquals(0, repo.dao.entryCount(saved.id))
      assertEquals(2L, repo.dao.recentCount()); repo.dao.clearRecent(); assertEquals(0L, repo.dao.recentCount())
      assertThrows(IllegalArgumentException::class.java) { repo.create(" ") }
      assertThrows(IllegalArgumentException::class.java) { repo.favorite(audio + ("uri" to "https://example.com/song"), null, true) }
    } finally { db.close(); context.deleteDatabase(name) }
  }
  @Test fun largePlaylistQueriesRemainPagedAndHistoryIsBounded() {
    val name = "collections-pages.db"; context.deleteDatabase(name)
    val db = Room.databaseBuilder(context, LibraryDatabase::class.java, name).build()
    try {
      val repo = CollectionsRepository(context, db)
      val saved = repo.saveQueue("Large", (0 until 10000).map { (audio + ("title" to "Song $it")) to null })
      val elapsed = measureTimeMillis {
        val page = repo.entries("playlist", saved.id, mapOf("offset" to 9950))
        assertEquals(10000.0, page["total"]); assertEquals(50, (page["items"] as List<*>).size); assertEquals(false, page["hasMore"])
      }
      assertTrue("Paged query ${elapsed}ms", elapsed < 1500)
      db.runInTransaction { (0..550).forEach { repo.dao.putRecent(RecentPlay("track:$it", null, repo.audioJson(audio), it.toLong())) }; repo.dao.pruneRecent() }
      assertEquals(500L, repo.dao.recentCount()); assertEquals("track:550", repo.dao.recent(0, 50).first().key)
    } finally { db.close(); context.deleteDatabase(name) }
  }
  @Test fun favoritesSearchFindsEntriesBeyondFirstPageAndEscapesWildcards() {
    val name = "favorites-search.db"; context.deleteDatabase(name)
    val db = Room.databaseBuilder(context, LibraryDatabase::class.java, name).build()
    try {
      val repo = CollectionsRepository(context, db)
      db.runInTransaction {
        (0 until 60).forEach { index ->
          val song = audio + ("title" to if (index == 0) "Hidden 100%_ song" else "Ordinary $index")
          repo.dao.putFavorite(Favorite("uri:$index", null, repo.audioJson(song), index.toLong()))
        }
      }
      val first = repo.entries("favorites", null, emptyMap())
      assertEquals(50, (first["items"] as List<*>).size); assertEquals(60.0, first["total"])
      val result = repo.entries("favorites", null, mapOf("search" to "100%_"))
      assertEquals(1.0, result["total"]); assertEquals(false, result["hasMore"])
      assertEquals("uri:0", ((result["items"] as List<*>).first() as Map<*, *>)["id"])
      assertEquals(0.0, repo.entries("favorites", null, mapOf("search" to "absent"))["total"])
    } finally { db.close(); context.deleteDatabase(name) }
  }
}
