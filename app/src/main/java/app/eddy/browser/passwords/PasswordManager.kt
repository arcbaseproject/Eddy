package app.eddy.browser.passwords

import app.eddy.browser.data.database.LoginBlock
import app.eddy.browser.data.database.LoginDao
import app.eddy.browser.data.database.LoginEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.SecureRandom

/** Outcome of an import. [recognised] is false when the file has no url and password columns. */
class ImportResult(val added: Int, val updated: Int, val unchanged: Int, val skipped: Int, val recognised: Boolean = true)

/** Saved logins. Passwords are encrypted before they reach the database and decrypted on demand only. */
class PasswordManager(private val dao: LoginDao, private val scope: CoroutineScope) {
    val logins: Flow<List<LoginEntity>> = dao.all()

    suspend fun forOrigin(origin: String) = dao.forOrigin(origin)
    suspend fun find(origin: String, username: String) = dao.find(origin, username)
    suspend fun isBlocked(origin: String) = dao.isBlocked(origin)

    suspend fun decrypt(login: LoginEntity): String? = withContext(Dispatchers.Default) { PasswordVault.decrypt(login.password) }

    /** Creates the login, or replaces the password of the one with the same origin and username. */
    suspend fun save(origin: String, username: String, password: String) {
        val encrypted = withContext(Dispatchers.Default) { PasswordVault.encrypt(password) }
        val now = System.currentTimeMillis()
        val existing = dao.find(origin, username)
        dao.upsert(
            existing?.copy(password = encrypted, updatedAt = now, lastUsedAt = now)
                ?: LoginEntity(origin = origin, username = username, password = encrypted, createdAt = now, updatedAt = now, lastUsedAt = now),
        )
    }

    /**
     * Edit from the manager. The row is written before the old one goes, so a failure part-way through
     * can never leave the user with no password at all.
     */
    suspend fun replace(oldId: Long, origin: String, username: String, password: String) {
        val encrypted = withContext(Dispatchers.Default) { PasswordVault.encrypt(password) }
        val now = System.currentTimeMillis()
        val old = dao.get(oldId)
        // Writing under the row that already holds this origin+username keeps the unique index happy.
        val target = dao.find(origin, username) ?: old
        dao.upsert(
            LoginEntity(
                id = target?.id ?: 0, origin = origin, username = username, password = encrypted,
                createdAt = target?.createdAt ?: now, updatedAt = now, lastUsedAt = now,
            ),
        )
        if (target?.id != oldId) dao.delete(oldId)
    }

    /** Chrome-style CSV (name,url,username,password,note). Passwords are written in plain text, so the caller must warn the user. */
    suspend fun exportCsv(): Pair<String, Int> = withContext(Dispatchers.Default) {
        val out = StringBuilder(Csv.row(listOf("name", "url", "username", "password", "note"))).append("\r\n")
        var count = 0
        for (login in dao.snapshot()) {
            val password = PasswordVault.decrypt(login.password) ?: continue
            out.append(Csv.row(listOf(login.origin.substringAfter("://"), login.origin, login.username, password, ""))).append("\r\n")
            count++
        }
        out.toString() to count
    }

    /** Reads Chrome, Edge, Firefox or Bitwarden style CSV by header name. Rows without a web address or password are skipped. */
    suspend fun importCsv(text: String): ImportResult = withContext(Dispatchers.Default) {
        val rows = Csv.parse(text)
        if (rows.isEmpty()) return@withContext ImportResult(0, 0, 0, 0, recognised = false)
        val header = rows.first().map { it.trim().lowercase() }
        fun column(vararg names: String) = header.indexOfFirst { it in names }
        val url = column("url", "origin", "website", "login_uri", "hostname")
        val user = column("username", "user", "login", "login_username", "email")
        val pass = column("password", "pass", "login_password")
        if (url < 0 || pass < 0) return@withContext ImportResult(0, 0, 0, rows.size - 1, recognised = false)

        var added = 0; var updated = 0; var unchanged = 0; var skipped = 0
        val body = rows.drop(1)
        skipped += (body.size - MAX_IMPORT_ROWS).coerceAtLeast(0)
        for (row in body.take(MAX_IMPORT_ROWS)) {
            val origin = row.getOrNull(url)?.let(::normalizeOrigin)
            val password = row.getOrNull(pass).orEmpty()
            val username = row.getOrNull(user).orEmpty().trim()
            if (origin == null || password.isEmpty() || password.length > MAX_FIELD || username.length > MAX_FIELD) { skipped++; continue }
            val existing = dao.find(origin, username)
            when {
                existing == null -> { save(origin, username, password); added++ }
                PasswordVault.decrypt(existing.password) == password -> unchanged++
                else -> { save(origin, username, password); updated++ }
            }
        }
        ImportResult(added, updated, unchanged, skipped)
    }

    fun touch(id: Long) { scope.launch { dao.touch(id, System.currentTimeMillis()) } }
    fun delete(id: Long) { scope.launch { dao.delete(id) } }
    fun deleteAll() { scope.launch { dao.clear() } }
    fun neverSave(origin: String) { scope.launch { dao.block(LoginBlock(origin)) } }
    fun resetNeverSave() { scope.launch { dao.clearBlocklist() } }

    companion object {
        private val originRegex = Regex("^(https?://[^/?#\\s]+)", RegexOption.IGNORE_CASE)
        private const val MAX_IMPORT_ROWS = 10_000
        private const val MAX_FIELD = 256
        private const val ALPHABET = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789!@#%^*-_"

        /** "example.com/login" -> "https://example.com"; null when it is not a web address. */
        fun normalizeOrigin(input: String): String? {
            val text = input.trim()
            val withScheme = if (text.contains("://")) text else "https://$text"
            return originRegex.find(withScheme)?.groupValues?.get(1)?.lowercase()?.takeIf { it.substringAfter("://").contains('.') || it.contains("localhost") }
        }

        fun generatePassword(length: Int = 20): String {
            val random = SecureRandom()
            return String(CharArray(length) { ALPHABET[random.nextInt(ALPHABET.length)] })
        }
    }
}
