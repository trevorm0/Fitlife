package com.example.fitwell.data.auth

import android.content.Context
import android.util.Base64
import android.util.Patterns
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** A signed-in person. [id] names their private database file, so accounts never share data. */
data class Account(val id: String, val email: String, val name: String)

/** Thrown with a message that is safe to show to the user. */
class AuthException(message: String) : Exception(message)

/**
 * Accounts that live on this device. Passwords are never stored: only a random salt and a
 * PBKDF2-HMAC-SHA256 hash. There is no password recovery (nothing leaves the phone), which the
 * UI says plainly. The signed-in account id is remembered so the app opens straight to Home.
 */
class AuthRepository(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("fitlife_auth", Context.MODE_PRIVATE)

    fun currentAccount(): Account? {
        val id = sp.getString(KEY_SESSION, null) ?: return null
        val o = accounts().optJSONObject(findEmailById(id) ?: return null) ?: return null
        return o.toAccount()
    }

    fun hasAnyAccount(): Boolean = accounts().length() > 0

    fun register(name: String, email: String, password: String): Account {
        val cleanName = name.trim()
        val cleanEmail = normalize(email)
        if (cleanName.isEmpty()) throw AuthException("Enter your name")
        validateEmail(cleanEmail)
        if (password.length < MIN_PASSWORD) throw AuthException("Password must be at least $MIN_PASSWORD characters")
        val all = accounts()
        if (all.has(cleanEmail)) throw AuthException("An account with this email already exists. Sign in instead.")

        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val o = JSONObject()
            .put("id", UUID.randomUUID().toString())
            .put("email", cleanEmail)
            .put("name", cleanName)
            .put("salt", b64(salt))
            .put("hash", b64(hash(password, salt)))
        all.put(cleanEmail, o)
        save(all)
        sp.edit().putString(KEY_SESSION, o.getString("id")).commit()
        return o.toAccount()
    }

    fun login(email: String, password: String): Account {
        val cleanEmail = normalize(email)
        if (cleanEmail.isEmpty() || password.isEmpty()) throw AuthException("Enter your email and password")
        val o = accounts().optJSONObject(cleanEmail)
        // Same message for "no such account" and "wrong password" so emails can't be probed.
        val wrong = AuthException("Incorrect email or password")
        if (o == null) throw wrong
        val expected = Base64.decode(o.getString("hash"), Base64.NO_WRAP)
        val actual = hash(password, Base64.decode(o.getString("salt"), Base64.NO_WRAP))
        if (!MessageDigest.isEqual(expected, actual)) throw wrong
        sp.edit().putString(KEY_SESSION, o.getString("id")).commit()
        return o.toAccount()
    }

    fun logout() {
        sp.edit().remove(KEY_SESSION).commit()
    }

    /** Removes the credentials. The caller deletes the account's database file. */
    fun deleteAccount(accountId: String) {
        val all = accounts()
        findEmailById(accountId)?.let { all.remove(it) }
        save(all)
        logout()
    }

    fun updateName(accountId: String, name: String) {
        val all = accounts()
        val email = findEmailById(accountId) ?: return
        all.optJSONObject(email)?.put("name", name.trim())
        save(all)
    }

    // ---- internals ----
    private fun accounts(): JSONObject = runCatching { JSONObject(sp.getString(KEY_ACCOUNTS, "{}") ?: "{}") }.getOrDefault(JSONObject())
    private fun save(o: JSONObject) { sp.edit().putString(KEY_ACCOUNTS, o.toString()).commit() }

    private fun findEmailById(id: String): String? {
        val all = accounts()
        all.keys().forEach { k -> if (all.getJSONObject(k).optString("id") == id) return k }
        return null
    }

    private fun normalize(email: String) = email.trim().lowercase()
    private fun validateEmail(email: String) {
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) throw AuthException("Enter a valid email address")
    }

    private fun hash(password: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256)).encoded

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun JSONObject.toAccount() = Account(getString("id"), getString("email"), getString("name"))

    companion object {
        const val MIN_PASSWORD = 8
        private const val ITERATIONS = 120_000
        private const val KEY_ACCOUNTS = "accounts"
        private const val KEY_SESSION = "session_account_id"
    }
}
