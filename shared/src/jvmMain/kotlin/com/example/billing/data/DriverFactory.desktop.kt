package com.example.billing.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.billing.BillingDatabase
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.sql.Connection
import java.sql.DriverManager
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.concurrent.thread

// Desktop:
//  * Installed app: asli database app ke install folder ke andar  <install>\data\billing.db
//  * IDE se chalane par: <home>\.billing_app\billing.db  (taake test ka data install wale se na mile)
//  * Backup: alag drive/folder me (jaise D:\BillingBackup), jo har tabdeeli par khud update hoti hai
actual class DriverFactory {
    actual fun createDriver(): SqlDriver {
        val place = DataPlace.resolve()
        val dbFile = File(place.dataFolder, "billing.db")
        val backup = DbBackup(dbFile, place)

        // 1) Asli database gayab / khali / kharab ho to backup se wapas le aao
        backup.restoreIfNeeded()

        // 2) Normal tareeqe se kholo
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        BillingDatabase.Schema.create(driver)   // IF NOT EXISTS ki wajah se dobara chalana safe hai

        // 3) Ab har tabdeeli par backup khud update hoti rahegi
        backup.startMirroring()

        // 4) Diagnostic: kaun si jagah istemal hui, ye ek chhoti file me likh do
        writeLocationNote(place, dbFile, backup)
        return driver
    }
}

/** C:\Users\<naam>\.billing_app\last_location.txt: database aur backup kahan hain, ye dekhne ke liye */
private fun writeLocationNote(place: DataPlace, dbFile: File, backup: DbBackup) {
    runCatching {
        val note = File(System.getProperty("user.home"), ".billing_app/last_location.txt")
        note.parentFile.mkdirs()
        note.writeText(
            buildString {
                appendLine("db=${dbFile.absolutePath}")
                appendLine("backup=${backup.backupPath}")
                appendLine("mode=${place.mode}")
                appendLine("jpackage.app-path=${System.getProperty("jpackage.app-path")}")
                appendLine("java.home=${System.getProperty("java.home")}")
                appendLine("user.dir=${System.getProperty("user.dir")}")
                appendLine("time=${LocalDateTime.now()}")
            }
        )
    }
}

/** Database kahan rakhna hai aur uski backup ka naam kya hoga */
private class DataPlace(
    val dataFolder: File,
    /** Purani jagah (home) ki file: sirf ek dafa data muntaqil karne ke liye */
    val legacyDb: File?,
    /** Backup folder ka naam. IDE ke test ka alag, taake asli backup me mix na ol */
    val backupName: String,
    /** Sirf diagnostic ke liye: INSTALLED_DATA_FOLDER / INSTALLED_HOME_FALLBACK / IDE */
    val mode: String,
) {
    companion object {
        fun resolve(): DataPlace {
            val home = File(System.getProperty("user.home"), ".billing_app")
            val install = installDir()

            if (install != null) {
                val data = File(install, "data")
                if (isWritable(data)) {
                    return DataPlace(data, File(home, "billing.db"), "BillingBackup", "INSTALLED_DATA_FOLDER")
                }
                // Agar waqai likhne ki ijazat nahi milti tab hi home me jayega
                home.mkdirs()
                return DataPlace(home, null, "BillingBackup", "INSTALLED_HOME_FALLBACK")
            }
            // IDE se chalane par
            home.mkdirs()
            return DataPlace(home, null, "Backup_Billing", "IDE")
        }

        /** Installed (jpackage) app ka folder, jahan .exe, app aur runtime folder hote hain. IDE me null. */
        private fun installDir(): File? {
            System.getProperty("jpackage.app-path")?.let { path ->
                File(path).parentFile?.takeIf { it.isDirectory }?.let { return it }
            }
            val javaHome = File(System.getProperty("java.home") ?: "")
            if (javaHome.name == "runtime") {
                val parent = javaHome.parentFile
                if (parent != null && File(parent, "app").isDirectory) return parent
            }
            runCatching {
                val cmd = ProcessHandle.current().info().command().orElse(null)
                val parent = if (cmd != null) File(cmd).parentFile else null
                if (parent != null && File(parent, "app").isDirectory && File(parent, "runtime").isDirectory) {
                    return parent
                }
            }
            return null
        }

        /** Behtar aur pakka check ke kya folder me file ban sakti hai ya nahi */
        private fun isWritable(dir: File): Boolean = runCatching {
            if (!dir.exists()) {
                dir.mkdirs()
            }
            if (!dir.isDirectory) return@runCatching false

            // Ek chhoti test file bana kar check karte hain
            val testFile = File(dir, "write_test.tmp")
            if (testFile.exists()) {
                testFile.delete()
            }

            val success = testFile.createNewFile()
            if (success) {
                testFile.delete()
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }
}

/**
 * Backup ka poora kaam.
 *  - billing_live.db   : asli database ki taaza copy (har tabdeeli ke kuch second baad update)
 *  - daily\billing_<tareekh>.db : roz ki ek copy, aakhri 30 din ki
 */
private class DbBackup(private val dbFile: File, private val place: DataPlace) {
    private val dataFolder: File = dbFile.parentFile
    private val backupDir: File = chooseBackupDir()
    private val liveFile = File(backupDir, "billing_live.db")
    private val dailyDir = File(backupDir, "daily")

    @Volatile
    private var lastSignature = ""

    /** Diagnostic ke liye: backup kahan ja raha hai */
    val backupPath: String get() = backupDir.absolutePath

    // ---------- 1) Wapas bahali (restore) ----------

    fun restoreIfNeeded() {
        runCatching {
            if (isHealthy(dbFile)) return

            // Asli database nahi hai ya kharab hai. Sab mumkin jagahon se sab se taaza achhi copy dhoondo.
            val fromBackups = restoreFolders()
                .flatMap { dir ->
                    listOf(File(dir, "billing_live.db")) +
                            (File(dir, "daily").listFiles()?.toList() ?: emptyList())
                }
                .filter { it.exists() && it.length() > 0L }
                .sortedByDescending { it.lastModified() }
                .firstOrNull { isHealthy(it) }

            // Backup na mile to purani jagah (home) ka data (sirf pehli dafa muntaqil karne ke liye)
            val good = fromBackups ?: place.legacyDb?.takeIf { isHealthy(it) } ?: return

            // Kharab file ho to tabahi se bachane ke liye alag naam se rakh lo
            if (dbFile.exists()) {
                val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                runCatching { dbFile.renameTo(File(dataFolder, "billing.corrupt-$stamp.db")) }
            }
            // Purani journal files naye database se takrayen na
            listOf("-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }
            good.copyTo(dbFile, overwrite = true)
        }
    }

    // ---------- 2) Chalte hue backup ----------

    fun startMirroring() {
        thread(name = "billing-backup", isDaemon = true) {
            while (true) {
                runCatching { syncIfChanged() }
                try {
                    Thread.sleep(5_000)
                } catch (e: InterruptedException) {
                    return@thread
                }
            }
        }
        // Software band hote waqt aakhri baar backup
        Runtime.getRuntime().addShutdownHook(Thread { runCatching { backupNow() } })
    }

    private fun syncIfChanged() {
        val sig = signature()
        if (sig == lastSignature) return
        if (backupNow()) lastSignature = sig
    }

    /** File badli hai ya nahi, ye pata karne ke liye (WAL file bhi shamil) */
    private fun signature(): String {
        val wal = File(dbFile.path + "-wal")
        return "${dbFile.lastModified()}:${dbFile.length()}:${wal.lastModified()}:${wal.length()}"
    }

    @Synchronized
    private fun backupNow(): Boolean {
        if (!dbFile.exists() || dbFile.length() == 0L) return false
        backupDir.mkdirs()

        val tmp = File(backupDir, "billing_live.tmp")
        if (tmp.exists()) tmp.delete()

        // VACUUM INTO = chalte hue database ki bhi poori aur sahi (consistent) copy
        val target = tmp.absolutePath.replace("'", "''")
        connect(dbFile).use { c ->
            c.createStatement().use { it.execute("VACUUM INTO '$target'") }
        }

        // Nayi copy kharab ho to purani achhi backup ko mat chhedo
        if (!isHealthy(tmp)) {
            tmp.delete()
            return false
        }
        Files.move(tmp.toPath(), liveFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        dailyCopy()
        return true
    }

    private fun dailyCopy() {
        dailyDir.mkdirs()
        val today = File(dailyDir, "billing_${LocalDate.now()}.db")
        if (!today.exists()) liveFile.copyTo(today)
        dailyDir.listFiles { f -> f.name.startsWith("billing_") && f.name.endsWith(".db") }
            ?.sortedByDescending { it.name }
            ?.drop(30)
            ?.forEach { it.delete() }
    }

    // ---------- madadgar ----------

    private fun connect(file: File): Connection {
        val c = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}")
        c.createStatement().use { it.execute("PRAGMA busy_timeout = 5000") }
        return c
    }

    /** File maujood, khali nahi, aur SQLite ke hisaab se kharab nahi */
    private fun isHealthy(file: File): Boolean = runCatching {
        if (!file.exists() || file.length() == 0L) return@runCatching false
        connect(file).use { c ->
            c.createStatement().use { st ->
                st.executeQuery("PRAGMA quick_check").use { rs -> rs.next() && rs.getString(1) == "ok" }
            }
        }
    }.getOrDefault(false)

    /**
     * Backup ki jagah:
     *  1) <data folder>\backup_dir.txt me jo folder likha ho (aap khud tay karna chahein to)
     *  2) pehli aisi drive jo Windows wali (C:) nahi, uska "BillingBackup" folder
     *  3) warna <home>\BillingBackup
     */
    private fun chooseBackupDir(): File {
        val dir = customDir()
            ?: otherDriveDir()
            ?: File(System.getProperty("user.home"), place.backupName)
        dir.mkdirs()
        return dir
    }

    private fun customDir(): File? = runCatching {
        File(dataFolder, "backup_dir.txt").takeIf { it.exists() }
            ?.readText()?.lines()?.firstOrNull { it.isNotBlank() }?.trim()
            ?.let { File(it) }
    }.getOrNull()

    private fun otherDriveDir(): File? {
        val systemDrive = (System.getenv("SystemDrive") ?: "C:").uppercase()
        val root = File.listRoots().firstOrNull { r ->
            r.path.take(2).uppercase() != systemDrive && r.totalSpace > 0L && r.canWrite()
        }
        return root?.let { File(it, place.backupName) }
    }

    /** Restore ke waqt in sab folders me dhoondte hain */
    private fun restoreFolders(): List<File> {
        val all = mutableListOf(backupDir, File(System.getProperty("user.home"), place.backupName))
        File.listRoots().forEach { r -> all += File(r, place.backupName) }
        return all.distinct().filter { it.isDirectory }
    }
}