package com.example.fitwell.data.backup

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.example.fitwell.data.local.FitLifeDatabase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * OPT-IN cloud backup. Nothing is sent unless the user enabled it in Settings, signed in, and tapped
 * "Back up now". A backup is a full snapshot (not a live two-way sync) stored under users/{uid}/backup.
 * Restore REPLACES the data on this device with the snapshot.
 */
class CloudBackup(private val db: FitLifeDatabase) {

    // Parents first (insert order). Deletion uses the reverse order.
    private val tables = listOf(
        "workouts", "exercises", "workout_sessions", "meals", "fitness_goals", "user_profile",
        "medications", "medication_reminders", "medication_intakes", "appointments", "appointment_reminders"
    )

    /** False when google-services.json was not added to the project (Firebase never initialised). */
    fun isAvailable(ctx: Context): Boolean = runCatching { FirebaseApp.getApps(ctx).isNotEmpty() }.getOrDefault(false)

    fun currentEmail(ctx: Context): String? =
        if (!isAvailable(ctx)) null else runCatching { FirebaseAuth.getInstance().currentUser?.email }.getOrNull()

    suspend fun signIn(email: String, password: String, register: Boolean) {
        val auth = FirebaseAuth.getInstance()
        if (register) auth.createUserWithEmailAndPassword(email.trim(), password).await()
        else auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    fun signOut() = FirebaseAuth.getInstance().signOut()

    private fun uid(): String = FirebaseAuth.getInstance().currentUser?.uid ?: error("Not signed in")
    private fun backupCol() =
        FirebaseFirestore.getInstance().collection("users").document(uid()).collection("backup")

    suspend fun backUp() {
        val json = withContext(Dispatchers.IO) { snapshot().toString() }
        val chunks = json.chunked(CHUNK)
        val col = backupCol()
        chunks.forEachIndexed { i, c -> col.document("part_$i").set(mapOf("data" to c)).await() }
        col.document("meta").set(mapOf("parts" to chunks.size, "createdAt" to System.currentTimeMillis(), "schema" to 2)).await()
    }

    /** @return false when no backup exists. */
    suspend fun restore(): Boolean {
        val col = backupCol()
        val meta = col.document("meta").get().await()
        if (!meta.exists()) return false
        val parts = (meta.getLong("parts") ?: return false).toInt()
        val sb = StringBuilder()
        for (i in 0 until parts) sb.append(col.document("part_$i").get().await().getString("data") ?: error("Backup is incomplete"))
        val json = JSONObject(sb.toString())
        withContext(Dispatchers.IO) { replaceAll(json) }
        return true
    }

    suspend fun deleteBackup() {
        val col = backupCol()
        val docs = col.get().await()
        docs.documents.forEach { it.reference.delete().await() }
    }

    // ---------- generic table <-> JSON ----------
    private fun snapshot(): JSONObject {
        val root = JSONObject().put("schema", 2)
        val sdb = db.openHelper.readableDatabase
        tables.forEach { t ->
            val rows = JSONArray()
            sdb.query("SELECT * FROM `$t`").use { c ->
                while (c.moveToNext()) rows.put(rowToJson(c))
            }
            root.put(t, rows)
        }
        return root
    }

    private fun rowToJson(c: Cursor): JSONObject {
        val o = JSONObject()
        for (i in 0 until c.columnCount) {
            val name = c.getColumnName(i)
            when (c.getType(i)) {
                Cursor.FIELD_TYPE_INTEGER -> o.put(name, c.getLong(i))
                Cursor.FIELD_TYPE_FLOAT -> o.put(name, c.getDouble(i))
                Cursor.FIELD_TYPE_STRING -> o.put(name, c.getString(i))
                else -> o.put(name, JSONObject.NULL)
            }
        }
        return o
    }

    private fun replaceAll(root: JSONObject) {
        val sdb = db.openHelper.writableDatabase
        db.runInTransaction(Runnable {
            tables.reversed().forEach { sdb.execSQL("DELETE FROM `$it`") }
            tables.forEach { t ->
                val rows = root.optJSONArray(t) ?: return@forEach
                for (i in 0 until rows.length()) {
                    val o = rows.getJSONObject(i)
                    val v = ContentValues()
                    o.keys().forEach { k ->
                        when (val x = o.get(k)) {
                            is Int -> v.put(k, x.toLong())
                            is Long -> v.put(k, x)
                            is Double -> v.put(k, x)
                            is String -> v.put(k, x)
                            else -> v.putNull(k)
                        }
                    }
                    sdb.insert(t, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, v)
                }
            }
        })
        db.invalidationTracker.refreshVersionsAsync()
    }

    private companion object { const val CHUNK = 400_000 }
}
