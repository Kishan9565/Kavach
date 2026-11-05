package com.kishan.kavach.ai.model

sealed interface ModelState {
    data object NotLoaded : ModelState
    data object Loading : ModelState
    data class Ready(val model: String, val backend: String, val loadMs: Long) : ModelState
    data class Error(val message: String) : ModelState
}

data class ModelFile(val name: String, val path: String, val sizeBytes: Long)

enum class CompatLevel { OK, WARN, BAD }
data class Compat(val level: CompatLevel, val text: String)

data class StorageInfo(val modelsBytes: Long, val freeBytes: Long)

/** Models the user can download manually (the app itself has no INTERNET permission, so it never fetches anything). */
data class CatalogModel(val name: String, val sizeGb: Double, val minRamGb: Int, val url: String)
