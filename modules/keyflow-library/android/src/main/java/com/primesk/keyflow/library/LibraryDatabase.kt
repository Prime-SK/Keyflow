package com.primesk.keyflow.library

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import java.util.UUID

@Entity(tableName = "tracks", indices = [Index(value = ["identity"], unique = true), Index("title"), Index("artist"), Index("album"), Index("folderKey")])
data class Track(
  @PrimaryKey val id: String = UUID.randomUUID().toString(),
  val identity: String, val fileName: String, val title: String,
  val artist: String = "Unknown artist", val album: String = "Unknown album",
  val albumArtist: String = "Unknown artist", val durationMs: Long = 0,
  val mimeType: String? = null, val size: Long = 0, val modified: Long = 0,
  val dateAdded: Long = System.currentTimeMillis(), val folderKey: String,
  val folderName: String, val artworkUri: String? = null, val metadataComplete: Boolean = false
)

@Entity(tableName = "sources")
data class Source(
  @PrimaryKey val id: String, val kind: String, val locator: String, val name: String,
  val enabled: Boolean = true, val access: String = "available", val checkpoint: String? = null,
  val lastCompleted: Long = 0, val error: String? = null
)

@Entity(tableName = "locations", primaryKeys = ["sourceId", "uri"],
  foreignKeys = [ForeignKey(entity = Track::class, parentColumns = ["id"], childColumns = ["trackId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = Source::class, parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.CASCADE)],
  indices = [Index("trackId"), Index("sourceId")])
data class Location(val sourceId: String, val uri: String, val trackId: String,
  val seenToken: String, val availability: String = "available")

@Entity(tableName = "folder_rules", primaryKeys = ["sourceId", "path"],
  foreignKeys = [ForeignKey(entity = Source::class, parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.CASCADE)],
  indices = [Index("sourceId")])
data class FolderRule(val sourceId: String, val path: String, val included: Boolean)

@Entity(tableName = "library_folder_rules")
data class LibraryFolderRule(@PrimaryKey val path: String, val included: Boolean)

@Fts4(contentEntity = Track::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "track_search")
data class TrackSearch(val title: String, val artist: String, val album: String, val fileName: String)

@Entity(tableName = "scan_state")
data class SavedScan(@PrimaryKey val id: Int = 1, val status: String = "idle",
  val discovered: Long = 0, val processed: Long = 0, val sourceName: String? = null, val error: String? = null)

@Dao
interface LibraryDao {
  @Upsert fun putScan(scan: SavedScan)
  @Query("SELECT * FROM scan_state WHERE id = 1") fun scan(): SavedScan?
  @Upsert fun putTrack(track: Track)
  @Upsert fun putSource(source: Source)
  @Upsert fun putLocation(location: Location)
  @Upsert fun putRule(rule: FolderRule)
  @Upsert fun putLibraryRule(rule: LibraryFolderRule)
  @Query("SELECT * FROM library_folder_rules ORDER BY LENGTH(path), path") fun libraryRules(): List<LibraryFolderRule>
  @Query("DELETE FROM library_folder_rules WHERE path = :path") fun removeLibraryRule(path: String)
  @Query("SELECT * FROM tracks WHERE identity = :identity LIMIT 1") fun byIdentity(identity: String): Track?
  @Query("SELECT * FROM tracks WHERE id = :id") fun track(id: String): Track?
  @Query("SELECT * FROM sources ORDER BY name COLLATE NOCASE, id") fun sources(): List<Source>
  @Query("SELECT * FROM sources WHERE id = :id") fun source(id: String): Source?
  @Query("SELECT * FROM locations WHERE trackId = :id") fun locations(id: String): List<Location>
  @Query("SELECT * FROM folder_rules WHERE sourceId = :id ORDER BY LENGTH(path)") fun rules(id: String): List<FolderRule>
  @Query("DELETE FROM sources WHERE id = :id") fun removeSource(id: String)
  @Query("DELETE FROM folder_rules WHERE sourceId = :id AND path = :path") fun removeRule(id: String, path: String)
  @Query("UPDATE locations SET availability = 'missing' WHERE sourceId = :id AND seenToken != :token")
  fun reconcile(id: String, token: String)
  @Query("UPDATE locations SET availability = :availability WHERE sourceId = :id")
  fun setSourceLocations(id: String, availability: String)
  @RawQuery fun tracks(query: SupportSQLiteQuery): List<Track>
  @RawQuery fun groups(query: SupportSQLiteQuery): List<LibraryGroup>
  @RawQuery fun count(query: SupportSQLiteQuery): Long
}

data class LibraryGroup(val key: String, val title: String, val subtitle: String, val count: Long)

@Database(entities = [Track::class, Source::class, Location::class, FolderRule::class, LibraryFolderRule::class, TrackSearch::class, SavedScan::class], version = 3, exportSchema = true)
abstract class LibraryDatabase : RoomDatabase() {
  abstract fun dao(): LibraryDao
  companion object {
    val MIGRATION_1_2 = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS scan_state (id INTEGER NOT NULL, status TEXT NOT NULL, discovered INTEGER NOT NULL, processed INTEGER NOT NULL, sourceName TEXT, error TEXT, PRIMARY KEY(id))")
      }
    }
    val MIGRATION_2_3 = object : Migration(2, 3) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS library_folder_rules (path TEXT NOT NULL, included INTEGER NOT NULL, PRIMARY KEY(path))")
      }
    }
    @Volatile private var instance: LibraryDatabase? = null
    fun get(context: Context): LibraryDatabase = instance ?: synchronized(this) {
      instance ?: Room.databaseBuilder(context.applicationContext, LibraryDatabase::class.java, "keyflow-library.db")
        // No destructive fallback: future versions must supply reviewed migrations.
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
    }
  }
}
