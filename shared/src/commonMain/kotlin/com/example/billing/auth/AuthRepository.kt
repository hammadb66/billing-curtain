package com.example.billing.auth

import com.example.billing.BillingDatabase
import com.example.billing.utils.nowIso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock

data class AppUser(val id: Long, val username: String, val isActive: Boolean)

sealed interface LoginResult {
    data class Success(val user: AppUser) : LoginResult
    data object Wrong : LoginResult
    data object Disabled : LoginResult
    data class Locked(val seconds: Int) : LoginResult
}

/** Is laptop ke users (login) ka hisaab kitaab. */
class AuthRepository(database: BillingDatabase, private val settings: AppSettings) {
    private val q = database.authQueries

    private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    suspend fun login(username: String, password: String): LoginResult = withContext(Dispatchers.Default) {
        val name = username.trim().lowercase()
        val now = nowMillis()

        // Bohat ghalat koshishen: kuch der ke liye lock (guess karke password todne se bachao)
        val lockUntil = settings.get("lock_$name")?.toLongOrNull() ?: 0L
        if (now < lockUntil) {
            return@withContext LoginResult.Locked(((lockUntil - now) / 1000 + 1).toInt())
        }

        val row = q.selectUserByName(name).executeAsOneOrNull()
        val passwordOk = row != null && hashPassword(password, row.salt) == row.passwordHash
        if (!passwordOk || row == null) {
            registerFailure(name, now)
            return@withContext LoginResult.Wrong
        }

        settings.put("fail_$name", "0")
        settings.put("lock_$name", "0")
        if (row.isActive == 0L) return@withContext LoginResult.Disabled
        LoginResult.Success(AppUser(row.id, row.username, row.isActive != 0L))
    }

    /** 5 ghalat koshishon ke baad 30 sec, phir 60, 120 ... (zyada se zyada ~16 minute) */
    private suspend fun registerFailure(name: String, now: Long) {
        val fails = (settings.get("fail_$name")?.toIntOrNull() ?: 0) + 1
        settings.put("fail_$name", fails.toString())
        if (fails >= 5) {
            val extra = minOf(fails - 5, 5)
            val seconds = 30L * (1L shl extra)
            settings.put("lock_$name", (now + seconds * 1000L).toString())
        }
    }

    suspend fun listUsers(): List<AppUser> = withContext(Dispatchers.Default) {
        q.selectAllUsers().executeAsList().map { AppUser(it.id, it.username, it.isActive != 0L) }
    }

    /** null = kamyab, warna error ka text */
    suspend fun addUser(username: String, password: String): String? = withContext(Dispatchers.Default) {
        val name = username.trim().lowercase()
        val problem = checkName(name) ?: checkPassword(password)
        if (problem != null) return@withContext problem
        if (q.selectUserByName(name).executeAsOneOrNull() != null) {
            return@withContext "Ye username pehle se maujood hai"
        }
        val salt = randomSaltHex()
        q.insertUser(name, salt, hashPassword(password, salt), nowIso())
        null
    }

    /** null = kamyab, warna error ka text */
    suspend fun changePassword(id: Long, newPassword: String): String? = withContext(Dispatchers.Default) {
        val problem = checkPassword(newPassword)
        if (problem != null) return@withContext problem
        val salt = randomSaltHex()
        q.updateUserPassword(salt, hashPassword(newPassword, salt), id)
        null
    }

    suspend fun setActive(id: Long, active: Boolean) {
        withContext(Dispatchers.Default) { q.setUserActive(if (active) 1L else 0L, id) }
    }

    suspend fun deleteUser(id: Long) {
        withContext(Dispatchers.Default) { q.deleteUser(id) }
    }

    private fun checkName(name: String): String? = when {
        name.length < 3 -> "Username kam az kam 3 harf ka ho"
        !name.all { it.isLetterOrDigit() || it == '_' || it == '.' } ->
            "Username me sirf harf, number, _ ya . ho sakta hai"
        else -> null
    }

    private fun checkPassword(password: String): String? =
        if (password.length < 4) "Password kam az kam 4 harf ka ho" else null
}
