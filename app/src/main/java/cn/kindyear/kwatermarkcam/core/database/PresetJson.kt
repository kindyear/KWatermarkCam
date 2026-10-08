package cn.kindyear.kwatermarkcam.core.database

import org.json.JSONArray
import org.json.JSONObject

/** Versioned field document, preserving stable IDs. Unknown future schemas fail without overwriting data. */
object PresetJson {
    fun encode(values: Map<String, String>, hidden: Set<String>): String = JSONObject().apply {
        put("schemaVersion", 1)
        put("values", JSONObject(values))
        put("hidden", JSONArray(hidden.sorted()))
    }.toString()
    fun decode(json: String): Pair<Map<String, String>, Set<String>> {
        val document = JSONObject(json)
        require(document.getInt("schemaVersion") == 1) { "Unsupported preset schema" }
        val fields = document.getJSONObject("values")
        val values = fields.keys().asSequence().associateWith { fields.getString(it) }
        val hidden = document.optJSONArray("hidden") ?: JSONArray()
        return values to (0 until hidden.length()).map { hidden.getString(it) }.toSet()
    }
}
