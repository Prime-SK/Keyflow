package com.primesk.keyflow.library

internal object LibraryIdentity {
  fun external(volume: String, path: String): String {
    val normalizedVolume = if (volume == "external_primary" || volume == "primary") "primary" else volume.lowercase()
    val cleanPath = path.replace('\\', '/').trimStart('/').split('/').filter { it.isNotEmpty() && it != "." }
    require(cleanPath.none { it == ".." }) { "Invalid folder path." }
    return "$normalizedVolume:${cleanPath.joinToString("/")}"
  }
  fun document(authority: String?, id: String): String =
    if (authority == "com.android.externalstorage.documents" && ':' in id) external(id.substringBefore(':'), id.substringAfter(':'))
    else "document:$authority:$id"
  fun legacyPath(path: String): String? = when {
    path.startsWith("/storage/emulated/0/") -> external("primary", path.removePrefix("/storage/emulated/0/"))
    path.startsWith("/storage/") -> path.removePrefix("/storage/").let { external(it.substringBefore('/'), it.substringAfter('/')) }
    else -> null
  }
  fun folder(identity: String): String = identity.substringBeforeLast('/', identity.substringBefore(':') + ":") + "/"
  fun included(folder: String, rules: List<FolderRule>): Boolean = rules.filter { folder.startsWith(it.path.trimEnd('/') + "/") }
    .maxByOrNull { it.path.length }?.included ?: true
  fun libraryIncluded(folder: String, rules: List<LibraryFolderRule>): Boolean = rules.filter { folder.startsWith(it.path.trimEnd('/') + "/") }
    .maxByOrNull { it.path.length }?.included ?: true
  fun isAudio(name: String, mime: String?): Boolean = mime?.startsWith("audio/") == true ||
    name.substringAfterLast('.', "").lowercase() in setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "amr", "aiff", "aif")
}
