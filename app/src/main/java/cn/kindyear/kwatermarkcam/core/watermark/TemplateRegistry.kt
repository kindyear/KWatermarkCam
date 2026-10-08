package cn.kindyear.kwatermarkcam.core.watermark

import android.content.Context
import cn.kindyear.kwatermarkcam.R
import cn.kindyear.kwatermarkcam.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.time.Instant
import java.time.format.DateTimeFormatter

/** Add templates here. Forms, formatting, and rendering consume this schema. */
@Singleton
class TemplateRegistry @Inject constructor(@param:ApplicationContext private val context: Context) {
    val templates = listOf(WatermarkTemplate("construction-default", R.string.template_construction, listOf(
        WatermarkField("time", R.string.field_time, FieldType.DATE_TIME, editable = false, order = 0),
        WatermarkField("project", R.string.field_project, FieldType.TEXT, context.getString(R.string.default_project), order = 1),
        WatermarkField("company", R.string.field_company, FieldType.TEXT, context.getString(R.string.default_company), order = 2),
        WatermarkField("address", R.string.field_address, FieldType.ADDRESS, order = 3),
        WatermarkField("photographer", R.string.field_photographer, FieldType.TEXT, order = 4),
        WatermarkField("note", R.string.field_note, FieldType.TEXT, context.getString(R.string.default_note), order = 5),
    )))
    fun template(id: String) = templates.firstOrNull { it.id == id } ?: templates.first()
    fun defaults(id: String) = template(id).fields.associate { it.id to it.defaultValue }
    fun snapshot(preset: WatermarkPreset, context: WatermarkContext, visible: Boolean): WatermarkSnapshot {
        val template = template(preset.templateId)
        return WatermarkSnapshot(template.id, preset.id, this.context.getString(template.nameRes),
            template.fields.sortedBy { it.order }.filterNot { it.id in preset.hiddenFields }.map { field ->
                var value = FieldFormatter.resolve(field, preset, context, this.context.getString(R.string.position_unavailable))
                val dynamicLocation = field.type == FieldType.COORDINATES || field.type == FieldType.ADDRESS && preset.fieldValues[field.id].isNullOrBlank()
                if (dynamicLocation && context.location.status == LocationStatus.CACHED && context.location.measuredAt != null) {
                    val measured = Instant.ofEpochMilli(context.location.measuredAt).atZone(context.zone).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
                    value = this.context.getString(R.string.watermark_cached_location, value, measured)
                }
                WatermarkRow(this.context.getString(field.labelRes), value)
            }.filter { it.value.isNotBlank() }, visible, context.timestamp, template.layout)
    }
}
