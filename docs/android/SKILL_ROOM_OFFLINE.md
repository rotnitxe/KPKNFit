# Skill: Arquitectura Offline-First con Room (Room Specialist)

Esta guía detalla los estándares de persistencia local en SQLite mediante la biblioteca **Room Database** en KPKN Fit. El principio de diseño fundamental es **Offline-First**: toda interacción del usuario se procesa localmente en la base de datos nativa antes de realizar sincronizaciones asíncronas en segundo plano.

---

## 🗄️ 1. Declaración de Esquemas y Entidades
Toda entidad de persistencia debe estar declarada en `data/db/Entities.kt` (o `WikiLabEntities.kt` para la enciclopedia local) y estar marcada con la anotación `@Entity`.

### Reglas Críticas:
1. **Inmutabilidad**: Las entidades deben declararse como `data class` usando propiedades inmutables (`val`).
2. **Llaves Primarias Robustas**: Usar IDs únicos tipo string (UUIDs generados localmente) para asegurar compatibilidad perfecta con Supabase y evitar colisiones de llaves primarias durante la sincronización offline.
3. **Conversión de Tipos**: Las clases complejas (listas de objetos, enums, fechas) que SQLite no soporta nativamente deben convertirse usando converters (`@TypeConverters`) globales definidos en `KpknDatabase.kt`.

### Ejemplo Práctico de Entidades con Relaciones:
```kotlin
@Entity(tableName = "programs")
data class ProgramEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String?,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = ProgramEntity::class,
            parentColumns = ["id"],
            childColumns = ["programId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SessionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val programId: String,
    val name: String,
    val isCompleted: Boolean = false,
    val sequenceOrder: Int
)
```

---

## 🔍 2. Consultas Asíncronas en DAOs (Data Access Objects)
Los DAOs se definen en `data/db/Daos.kt` o `WikiLabDao.kt`. 

### Reglas Críticas:
1. **Uso de Suspend / Flow**:
   - Para consultas instantáneas de escritura/lectura única, usar funciones suspendidas (`suspend fun`).
   - Para observación reactiva en tiempo real de los datos en la UI, retornar objetos `Flow<T>`. Room actualizará automáticamente el flujo cuando haya cambios en las tablas correspondientes.
2. **Relaciones Complejas (`@Transaction`)**:
   - Cuando se consultan objetos compuestos (ej. un programa con todas sus sesiones integradas), la función debe estar marcada con `@Transaction` para garantizar que la consulta múltiple se ejecute de forma atómica en SQLite.

### Ejemplo de DAO:
```kotlin
@Dao
interface WorkoutDao {
    @Query("SELECT * FROM programs ORDER BY createdAt DESC")
    fun getAllProgramsReactive(): Flow<List<ProgramEntity>>

    @Query("SELECT * FROM programs WHERE id = :programId LIMIT 1")
    suspend fun getProgramById(programId: String): ProgramEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgram(program: ProgramEntity)

    @Transaction
    @Query("SELECT * FROM programs WHERE id = :programId")
    fun getProgramWithSessions(programId: String): Flow<ProgramWithSessions>
}

// Clase contenedora de relación una-a-muchas
data class ProgramWithSessions(
    @Embedded val program: ProgramEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "programId"
    )
    val sessions: List<SessionEntity>
)
```

---

## 🔄 3. El Patrón Repository en Clean Architecture
Los repositorios (`data/repository/`) son la única puerta de entrada que los ViewModels de las pantallas deben llamar. Aíslan por completo los DAOs locales de la sincronización en la nube.

```kotlin
class ProgramRepository(
    private val workoutDao: WorkoutDao,
    private val supabaseClient: SupabaseClient // Para sincronización futura
) {
    // 1. Fuente reactiva primaria para el ViewModel
    val activePrograms: Flow<List<Program>> = workoutDao.getAllProgramsReactive().map { entities ->
        entities.map { it.toDomainModel() } // Convertir entidad de BD a modelo limpio
    }

    // 2. Operación asíncrona Offline-First
    suspend fun saveProgram(program: Program) {
        // Guardar primero en base de datos local SQLite de forma atómica
        workoutDao.insertProgram(program.toEntity())

        // 3. Cola de sincronización en background (Non-blocking para el usuario)
        try {
            // Intentar subir a Supabase. Si falla (offline), se queda localmente
            // y un worker de background intentará sincronizar después.
            supabaseClient.uploadProgram(program)
        } catch (e: Exception) {
            TelemetryHelper.logNonCriticalError("Sync fallida (Offline): ${e.message}")
        }
    }
}
```

---

## 🛡️ 4. Estrategia de Migraciones y Pre-poblado de Datos
Al lanzar la base de datos por primera vez (ej. cargando la enciclopedia WikiLab o catálogo de comidas offline), se debe ejecutar un prepoblado inicial mediante callbacks.

```kotlin
@Database(
    entities = [ProgramEntity::class, SessionEntity::class, WikiLabExerciseEntity::class],
    version = 1,
    exportSchema = false
)
abstract class KpknDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    abstract fun wikiLabDao(): WikiLabDao

    companion object {
        @Volatile
        private var INSTANCE: KpknDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): KpknDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    KpknDatabase::class.java,
                    "kpkn_database"
                )
                .addCallback(DatabasePrepulateCallback(scope)) // Insertar datos iniciales
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
```
