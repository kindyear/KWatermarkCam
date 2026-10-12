package cn.kindyear.kwatermarkcam.domain.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Stable field types. IDs, rather than translated labels, are persisted. */
enum class FieldType { TEXT, DATE, TIME, DATE_TIME, ADDRESS, COORDINATES, NUMBER }
data class WatermarkField(
    val id: String, val labelRes: Int, val type: FieldType,
    val defaultValue: String = "", val editable: Boolean = true,
    val hideable: Boolean = true, val order: Int,
)
enum class WatermarkStyle { CONSTRUCTION, ATTENDANCE, INSPECTION, WORK_LOG, TRAVEL }

/** Template-owned normalized geometry; future layouts can extend this schema without changing presets. */
data class WatermarkLayoutSpec(
    val widthFraction: Float = .76f, val maxWidthOn1080: Float = 850f,
    val marginOn1080: Float = 32f, val paddingOn1080: Float = 24f,
    val fontOn1080: Float = 32f, val maxHeightFraction: Float = .48f, val maxLines: Int = 3,
    val style: WatermarkStyle = WatermarkStyle.CONSTRUCTION,
)
data class WatermarkTemplate(val id: String, val nameRes: Int, val fields: List<WatermarkField>, val layout: WatermarkLayoutSpec = WatermarkLayoutSpec(), val descriptionRes: Int = 0, val showTitle: Boolean = false)
data class WatermarkPreset(
    val id: String, val templateId: String, val name: String,
    val fieldValues: Map<String, String>, val hiddenFields: Set<String> = emptySet(),
    val isPinned: Boolean = false, val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = createdAt,
    val folderId: String? = null,
    val customFields: List<CustomWatermarkField> = emptyList(),
)
/** User fields are owned by the preset; IDs remain stable across rename, copy and sort. */
data class CustomWatermarkField(val id: String, val name: String, val value: String = "", val visible: Boolean = true) { companion object { const val MAX_COUNT = 12 } }
enum class LocationDisplay { NEARBY_NAME, COORDINATES }
/** Folders organize presets across templates; null parent represents the library root. */
data class PresetFolder(val id: String, val name: String, val parentId: String? = null, val createdAt: Long)
enum class LocationStatus { DISABLED, PERMISSION_REQUIRED, SERVICES_OFF, ACQUIRING, SUCCESS, CACHED, TIMEOUT, FAILED, GEOCODING_FAILED }
data class LocationSnapshot(
    val status: LocationStatus = LocationStatus.DISABLED,
    val latitude: Double? = null, val longitude: Double? = null,
    val address: String? = null, val measuredAt: Long? = null, val accuracy: Float? = null,
) {
    fun coordinates(): String? = latitude?.let { lat -> longitude?.let { lon -> String.format(Locale.ROOT, "%.5f, %.5f", lat, lon) } }
}
/** Immutable capture-time input. Async updates can never alter an existing snapshot. */
data class WatermarkContext(val timestamp: Long, val location: LocationSnapshot, val zone: ZoneId = ZoneId.systemDefault(), val locationDisplay: LocationDisplay = LocationDisplay.NEARBY_NAME)
data class WatermarkRow(val label: String, val value: String)
data class WatermarkSnapshot(val templateId: String, val presetId: String, val title: String, val rows: List<WatermarkRow>, val visible: Boolean, val timestamp: Long, val layout: WatermarkLayoutSpec = WatermarkLayoutSpec())

/** Deterministic formatter shared by preview and capture. */
object FieldFormatter {
    fun resolve(field: WatermarkField, preset: WatermarkPreset, context: WatermarkContext, unavailable: String): String {
        val instant = Instant.ofEpochMilli(context.timestamp).atZone(context.zone)
        val manual = preset.fieldValues[field.id].orEmpty()
        return when (field.type) {
            FieldType.DATE_TIME -> instant.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT))
            FieldType.DATE -> instant.format(DateTimeFormatter.ISO_LOCAL_DATE)
            FieldType.TIME -> instant.format(DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT))
            FieldType.ADDRESS -> manual.ifBlank { if (context.locationDisplay == LocationDisplay.COORDINATES) context.location.coordinates() ?: unavailable else context.location.address?.takeIf { it.isNotBlank() } ?: context.location.coordinates() ?: unavailable }
            FieldType.COORDINATES -> context.location.coordinates() ?: unavailable
            else -> preset.fieldValues[field.id] ?: field.defaultValue
        }
    }
}
