package fr.vueconfort.app.nativevision

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject

internal class AndroidLabSettingsPort(context: Context) : LabSettingsPort {
    private val app = context.applicationContext
    override fun canWrite() = app.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
    override fun read(key: LabSecureKey): String? = Settings.Secure.getString(app.contentResolver, key.settingName)
    override fun put(key: LabSecureKey, value: String): Boolean {
        if (!canWrite()) throw SecurityException("Lab permission is not granted")
        return Settings.Secure.putString(app.contentResolver, key.settingName, value)
    }
    override fun delete(key: LabSecureKey): Boolean {
        if (!canWrite()) throw SecurityException("Lab permission is not granted")
        app.contentResolver.delete(Settings.Secure.getUriFor(key.settingName), null, null)
        return read(key) == null
    }
}

/** This rollback log contains only the twelve explicitly supported display settings. */
internal class AndroidLabJournal(context: Context) : LabJournal {
    private val preferences = context.applicationContext.getSharedPreferences("native_vision_lab_rollback_v1", Context.MODE_PRIVATE)
    override fun load(): Map<LabSecureKey, LabRestoreRecord> {
        val serialized = preferences.getString("journal", null) ?: return emptyMap()
        val root = JSONObject(serialized)
        require(root.getInt("version") == 1) { "Unsupported rollback journal" }
        val entries = root.getJSONArray("entries")
        require(entries.length() <= LabSecureKey.entries.size) { "Invalid rollback journal size" }
        return linkedMapOf<LabSecureKey, LabRestoreRecord>().apply {
            for (index in 0 until entries.length()) {
                val item = entries.getJSONObject(index)
                val key = LabSecureKey.valueOf(item.getString("key"))
                require(!containsKey(key)) { "Duplicate rollback setting" }
                put(key, LabRestoreRecord(
                    key = key,
                    previous = item.nullableValue("previous"),
                    lastWritten = item.nullableValue("lastWritten"),
                    hasPendingWrite = item.getBoolean("hasPendingWrite"),
                    pendingValue = item.nullableValue("pendingValue"),
                    profileRevision = item.getLong("profileRevision").also { require(it >= 0) },
                ))
            }
        }
    }

    override fun save(records: Map<LabSecureKey, LabRestoreRecord>): Boolean {
        val entries = JSONArray()
        records.values.forEach { record ->
            entries.put(JSONObject().apply {
                put("key", record.key.name)
                put("previous", record.previous ?: JSONObject.NULL)
                put("lastWritten", record.lastWritten ?: JSONObject.NULL)
                put("hasPendingWrite", record.hasPendingWrite)
                put("pendingValue", record.pendingValue ?: JSONObject.NULL)
                put("profileRevision", record.profileRevision)
            })
        }
        return preferences.edit().putString("journal", JSONObject().put("version", 1).put("entries", entries).toString()).commit()
    }

    private fun JSONObject.nullableValue(name: String): String? {
        require(has(name)) { "Incomplete rollback record" }
        return if (isNull(name)) null else getString(name).also { require(it.length <= 200) }
    }
}
