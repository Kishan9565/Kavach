// Minimal API stubs so compile_check.sh can type-check the Room/WorkManager glue offline. Real builds use the real libraries.
package androidx.room
import kotlin.reflect.KClass
annotation class Entity(val tableName: String = "", val indices: Array<Index> = [])
annotation class Index(vararg val value: String)
annotation class PrimaryKey(val autoGenerate: Boolean = false)
annotation class Dao
annotation class Query(val value: String)
annotation class Insert(val onConflict: Int = 3)
annotation class Update
annotation class Database(val entities: Array<KClass<*>>, val version: Int, val exportSchema: Boolean = true)
object OnConflictStrategy { const val REPLACE = 1 }
abstract class RoomDatabase { class Builder<T : RoomDatabase> { fun build(): T = throw NotImplementedError() } }
object Room { fun <T : RoomDatabase> databaseBuilder(c: android.content.Context, k: Class<T>, name: String): RoomDatabase.Builder<T> = RoomDatabase.Builder() }
