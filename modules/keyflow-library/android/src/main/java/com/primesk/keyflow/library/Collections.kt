package com.primesk.keyflow.library

import androidx.room.*
import java.util.UUID

@Entity(tableName = "playlists", indices = [Index("updatedAt")])
data class Playlist(@PrimaryKey val id: String = UUID.randomUUID().toString(), val name: String,
  val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = createdAt)

@Entity(tableName = "playlist_entries", foreignKeys = [ForeignKey(entity = Playlist::class,
  parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE)],
  indices = [Index(value = ["playlistId", "position"]), Index("trackId")])
data class PlaylistEntry(@PrimaryKey val id: String = UUID.randomUUID().toString(), val playlistId: String,
  val position: Int, val trackId: String?, val audio: String)

@Entity(tableName = "favorites", indices = [Index("trackId"), Index("addedAt")])
data class Favorite(@PrimaryKey val key: String, val trackId: String?, val audio: String,
  val addedAt: Long = System.currentTimeMillis())

@Entity(tableName = "recent_plays", indices = [Index("playedAt")])
data class RecentPlay(@PrimaryKey val key: String, val trackId: String?, val audio: String,
  val playedAt: Long = System.currentTimeMillis())

data class PlaylistSummary(@Embedded val playlist: Playlist, val entryCount: Long)

@Dao
interface CollectionsDao {
  @Upsert fun putPlaylist(value: Playlist)
  @Query("SELECT * FROM playlists WHERE id = :id") fun playlist(id: String): Playlist?
  @Query("SELECT p.*, COUNT(e.id) AS entryCount FROM playlists p LEFT JOIN playlist_entries e ON p.id = e.playlistId GROUP BY p.id ORDER BY p.updatedAt DESC, p.id LIMIT :limit OFFSET :offset")
  fun playlists(offset: Int, limit: Int): List<PlaylistSummary>
  @Query("SELECT COUNT(*) FROM playlists") fun playlistCount(): Long
  @Query("DELETE FROM playlists WHERE id = :id") fun deletePlaylist(id: String)
  @Insert fun putEntries(values: List<PlaylistEntry>)
  @Update fun updateEntry(value: PlaylistEntry)
  @Query("SELECT * FROM playlist_entries WHERE playlistId = :id ORDER BY position, id LIMIT :limit OFFSET :offset")
  fun entries(id: String, offset: Int, limit: Int): List<PlaylistEntry>
  @Query("SELECT COUNT(*) FROM playlist_entries WHERE playlistId = :id") fun entryCount(id: String): Int
  @Query("SELECT * FROM playlist_entries WHERE id = :id") fun entry(id: String): PlaylistEntry?
  @Query("SELECT * FROM playlist_entries WHERE playlistId = :id AND position = :position LIMIT 1") fun atPosition(id: String, position: Int): PlaylistEntry?
  @Query("DELETE FROM playlist_entries WHERE id = :id") fun deleteEntry(id: String)
  @Query("UPDATE playlist_entries SET position = position - 1 WHERE playlistId = :id AND position > :position") fun closeGap(id: String, position: Int)
  @Upsert fun putFavorite(value: Favorite)
  @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE `key` = :key)") fun isFavorite(key: String): Boolean
  @Query("DELETE FROM favorites WHERE `key` = :key") fun deleteFavorite(key: String)
  @Query("SELECT * FROM favorites ORDER BY addedAt DESC, `key` LIMIT :limit OFFSET :offset") fun favorites(offset: Int, limit: Int): List<Favorite>
  @Query("SELECT COUNT(*) FROM favorites") fun favoriteCount(): Long
  @Query("""SELECT f.* FROM favorites f LEFT JOIN tracks t ON t.id = f.trackId
    WHERE (CASE WHEN t.id IS NULL THEN f.audio ELSE t.title || ' ' || t.artist || ' ' || t.album || ' ' || t.fileName END) LIKE :pattern ESCAPE '\'
    ORDER BY f.addedAt DESC, f.`key` LIMIT :limit OFFSET :offset""")
  fun searchFavorites(pattern: String, offset: Int, limit: Int): List<Favorite>
  @Query("""SELECT COUNT(*) FROM favorites f LEFT JOIN tracks t ON t.id = f.trackId
    WHERE (CASE WHEN t.id IS NULL THEN f.audio ELSE t.title || ' ' || t.artist || ' ' || t.album || ' ' || t.fileName END) LIKE :pattern ESCAPE '\'""")
  fun searchFavoriteCount(pattern: String): Long
  @Upsert fun putRecent(value: RecentPlay)
  @Query("SELECT * FROM recent_plays ORDER BY playedAt DESC, `key` LIMIT :limit OFFSET :offset") fun recent(offset: Int, limit: Int): List<RecentPlay>
  @Query("SELECT COUNT(*) FROM recent_plays") fun recentCount(): Long
  @Query("DELETE FROM recent_plays WHERE `key` NOT IN (SELECT `key` FROM recent_plays ORDER BY playedAt DESC, `key` LIMIT 500)") fun pruneRecent()
  @Query("DELETE FROM recent_plays") fun clearRecent()
}
