package com.kishan.kavach.ai.gemma

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.StatFs
import com.kishan.kavach.ai.model.*
import com.kishan.kavach.data.preferences.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/** Import / select / delete Gemma model files (.litertlm) and judge whether this phone can run them. */
class ModelManager(private val app: Context, private val prefs: Prefs) {
    val dir: File = File(app.filesDir, "models").apply { mkdirs() }

    companion object {
        const val MIN_VALID_BYTES = 100L * 1024 * 1024
        val CATALOG = listOf(
            CatalogModel("Gemma 4 E2B (LiteRT-LM)", 2.6, 8, "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm"),
            CatalogModel("Gemma 4 E4B (LiteRT-LM)", 3.6, 10, "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm"),
        )
    }

    init {
        // migrate the Sprint 1-3 location
        val legacy = File(app.filesDir, "model.litertlm")
        if (legacy.exists() && legacy.length() >= MIN_VALID_BYTES) runCatching { legacy.renameTo(File(dir, "model.litertlm")) }
    }

    fun list(): List<ModelFile> = dir.listFiles { f -> f.isFile && f.name.endsWith(".litertlm") && f.length() >= MIN_VALID_BYTES }
        ?.sortedBy { it.name }?.map { ModelFile(it.name, it.absolutePath, it.length()) } ?: emptyList()

    fun selectedModel(): File? {
        val all = list()
        val chosen = all.firstOrNull { it.name == prefs.modelFile } ?: all.maxByOrNull { it.sizeBytes }
        return chosen?.let { File(it.path) }
    }

    fun select(name: String) { prefs.modelFile = name }

    fun delete(name: String): Boolean {
        val ok = File(dir, name).takeIf { it.parentFile == dir }?.delete() ?: false
        if (prefs.modelFile == name) prefs.modelFile = ""
        return ok
    }

    fun storage(): StorageInfo = StorageInfo(list().sumOf { it.sizeBytes }, StatFs(app.filesDir.path).availableBytes)

    fun totalRamBytes(): Long {
        val mi = ActivityManager.MemoryInfo()
        (app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        return mi.totalMem
    }

    fun compatibility(m: ModelFile): Compat {
        val ramGb = totalRamBytes() / 1_073_741_824.0
        val needGb = if (m.sizeBytes > 3_000_000_000L) 10.0 else 8.0
        return when {
            ramGb < 4.5 -> Compat(CompatLevel.BAD, "Only %.1f GB RAM: this model will likely not load.".format(ramGb))
            ramGb < needGb - 1.5 -> Compat(CompatLevel.WARN, "%.1f GB RAM; recommended %.0f GB. May be slow or get killed.".format(ramGb, needGb))
            else -> Compat(CompatLevel.OK, "%.1f GB RAM: fine for this model.".format(ramGb))
        }
    }

    /** Copies a user-picked file into app storage. Checks space first; never leaves a half-written model behind. */
    suspend fun import(uri: Uri, displayName: String?, onProgress: (Long) -> Unit): Result<File> = withContext(Dispatchers.IO) {
        val name = (displayName ?: "gemma-model.litertlm").substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
        if (!name.endsWith(".litertlm")) return@withContext Result.failure(IllegalArgumentException("Pick a .litertlm Gemma model file."))
        val tmp = File(dir, "$name.part")
        try {
            val size = app.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            if (size in 1 until MIN_VALID_BYTES) return@withContext Result.failure(IllegalArgumentException("File is too small to be a Gemma model."))
            if (size > 0 && storage().freeBytes < size + 200L * 1024 * 1024) return@withContext Result.failure(IllegalStateException("Not enough free storage (need ${size / 1_048_576} MB + margin)."))
            var copied = 0L
            app.contentResolver.openInputStream(uri)!!.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(1 shl 20)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf); if (n < 0) break
                        out.write(buf, 0, n); copied += n; onProgress(copied)
                    }
                }
            }
            if (copied < MIN_VALID_BYTES) { tmp.delete(); return@withContext Result.failure(IllegalArgumentException("File is too small to be a Gemma model.")) }
            val dest = File(dir, name)
            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) { tmp.delete(); return@withContext Result.failure(IllegalStateException("Could not save the model.")) }
            prefs.modelFile = name
            Result.success(dest)
        } catch (e: Throwable) {
            tmp.delete()
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }
}
