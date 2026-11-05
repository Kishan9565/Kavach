package androidx.work
import android.content.Context
import java.util.concurrent.TimeUnit
class Data { fun getLong(k: String, d: Long): Long = d; fun getString(k: String): String? = null }
fun workDataOf(vararg p: Pair<String, Any?>): Data = Data()
class WorkerParameters
abstract class ListenableWorker(val applicationContext: Context, p: WorkerParameters) {
    val inputData: Data = Data()
    sealed class Result { class Success : Result(); class Failure : Result()
        companion object { fun success(): Result = Success(); fun failure(): Result = Failure() } }
}
abstract class CoroutineWorker(c: Context, p: WorkerParameters) : ListenableWorker(c, p) { abstract suspend fun doWork(): Result }
abstract class WorkRequest
class OneTimeWorkRequest : WorkRequest() { class Builder { fun setInitialDelay(d: Long, u: TimeUnit) = this; fun setInputData(d: Data) = this; fun build() = OneTimeWorkRequest() } }
class PeriodicWorkRequest : WorkRequest() { class Builder { fun setInputData(d: Data) = this; fun build() = PeriodicWorkRequest() } }
inline fun <reified W : ListenableWorker> OneTimeWorkRequestBuilder() = OneTimeWorkRequest.Builder()
inline fun <reified W : ListenableWorker> PeriodicWorkRequestBuilder(i: Long, u: TimeUnit) = PeriodicWorkRequest.Builder()
enum class ExistingWorkPolicy { REPLACE }
enum class ExistingPeriodicWorkPolicy { UPDATE }
class WorkManager { companion object { fun getInstance(c: Context) = WorkManager() }
    fun enqueueUniqueWork(n: String, p: ExistingWorkPolicy, r: OneTimeWorkRequest) {}
    fun enqueueUniquePeriodicWork(n: String, p: ExistingPeriodicWorkPolicy, r: PeriodicWorkRequest) {}
    fun cancelUniqueWork(n: String) {} }
