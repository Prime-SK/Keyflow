package com.primesk.keyflow.library

import org.junit.Assert.*
import org.junit.Test

class LibraryIdentityTest {
  @Test fun mediaAndFolderIdentityAgreeWithoutMergingCopies() {
    assertEquals(LibraryIdentity.external("external_primary", "Music/A/song.mp3"),
      LibraryIdentity.document("com.android.externalstorage.documents", "primary:Music/A/song.mp3"))
    assertEquals("primary:Music/A/song.mp3", LibraryIdentity.legacyPath("/storage/emulated/0/Music/A/song.mp3"))
    assertNotEquals(LibraryIdentity.external("primary", "Music/A/song.mp3"), LibraryIdentity.external("primary", "Music/B/song.mp3"))
    assertNotEquals(LibraryIdentity.external("primary", "Music/song.mp3"), LibraryIdentity.external("ABCD-1234", "Music/song.mp3"))
  }
  @Test fun nestedRulesAreScopedToFolderBoundaries() {
    val rules = listOf(FolderRule("test", "primary:Music/", false), FolderRule("test", "primary:Music/Keep/", true))
    assertFalse(LibraryIdentity.included("primary:Music/A/", rules))
    assertTrue(LibraryIdentity.included("primary:Music/Keep/", rules))
    assertTrue(LibraryIdentity.included("primary:MusicElse/", rules))
  }
  @Test fun formatDiscoveryIncludesShortAndUntaggedAudio() {
    assertTrue(LibraryIdentity.isAudio("test.FLAC", null))
    assertTrue(LibraryIdentity.isAudio("recording", "audio/aac"))
    assertFalse(LibraryIdentity.isAudio("picture.jpg", "image/jpeg"))
  }
}
