package cn.kindyear.kwatermarkcam.core.database

import cn.kindyear.kwatermarkcam.domain.model.CustomWatermarkField
import org.json.JSONArray
import org.json.JSONObject

/** Versioned field document, preserving stable IDs. Unknown future schemas fail without overwriting data. */
object PresetJson {
    fun encode(values: Map<String, String>, hidden: Set<String>, customFields: List<CustomWatermarkField> = emptyList()): String = JSONObject().apply {
        put("schemaVersion", if (customFields.isEmpty()) 1 else 2)
        put("values", JSONObject(values))
        put("hidden", JSONArray(hidden.sorted()))
        if (customFields.isNotEmpty()) put("customFields", JSONArray().apply {
            customFields.forEach { field -> put(JSONObject().apply {
                put("id", field.id); put("name", field.name); put("value", field.value); put("visible", field.visible)
            }) }
        })
    }.toString()
    fun customFields(json: String): List<CustomWatermarkField> {
        val document = JSONObject(json)
        require(document.getInt("schemaVersion") in 1..2) { "Unsupported preset schema" }
        if (document.getInt("schemaVersion") == 1) return emptyList()
        val array = document.getJSONArray("customFields")
        return (0 until array.length()).map { i -> array.getJSONObject(i).let {
            CustomWatermarkField(it.getString("id"), it.getString("name"), it.getString("value"), it.getBoolean("visible"))
        } }.also { fields -> require(fields.map { it.id }.toSet().size == fields.size) }
    }
    fun decode(json: String): Pair<Map<String, String>, Set<String>> {
        val document = JSONObject(json)
        require(document.getInt("schemaVersion") in 1..2) { "Unsupported preset schema" }
        val fields = document.getJSONObject("values")
        val values = fields.keys().asSequence().associateWith { fields.getString(it) }
        val hidden = document.optJSONArray("hidden") ?: JSONArray()
        return values to (0 until hidden.length()).map { hidden.getString(it) }.toSet()
    }
}
