package cn.kindyear.kwatermarkcam.core.watermark

import android.content.Context
import cn.kindyear.kwatermarkcam.R
import cn.kindyear.kwatermarkcam.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.time.Instant
import java.time.format.DateTimeFormatter

/** Stable template/field IDs keep existing presets compatible as the catalog grows. */
@Singleton
class TemplateRegistry @Inject constructor(@param:ApplicationContext private val context: Context) {
    private fun time(order: Int = 0) = WatermarkField("time", R.string.field_time, FieldType.DATE_TIME, editable = false, order = order)
    private fun address(order: Int) = WatermarkField("address", R.string.field_address, FieldType.ADDRESS, order = order)
    private fun text(id: String, label: Int, order: Int, default: String = "") = WatermarkField(id, label, FieldType.TEXT, default, order = order)
    val templates = listOf(
        WatermarkTemplate("construction-default", R.string.template_construction, listOf(
            time(), text("project", R.string.field_project, 1, context.getString(R.string.default_project)),
            text("company", R.string.field_company, 2, context.getString(R.string.default_company)), address(3),
            text("photographer", R.string.field_photographer, 4), text("note", R.string.field_note, 5, context.getString(R.string.default_note)),
        ), descriptionRes = R.string.description_construction),
        WatermarkTemplate("attendance-clock", R.string.template_attendance, listOf(
            time(), text("photographer", R.string.field_employee, 1), text("team", R.string.field_team, 2), address(3), text("note", R.string.field_note, 4),
        ), WatermarkLayoutSpec(widthFraction = .65f, maxWidthOn1080 = 710f, paddingOn1080 = 30f, fontOn1080 = 34f, style = WatermarkStyle.ATTENDANCE), R.string.description_attendance),
        WatermarkTemplate("inspection-record", R.string.template_inspection, listOf(
            text("asset", R.string.field_asset, 0), time(1), address(2), text("status", R.string.field_status, 3), text("photographer", R.string.field_inspector, 4), text("note", R.string.field_note, 5),
        ), WatermarkLayoutSpec(widthFraction = .72f, maxWidthOn1080 = 790f, paddingOn1080 = 28f, style = WatermarkStyle.INSPECTION), R.string.description_inspection),
        WatermarkTemplate("work-log", R.string.template_work, listOf(
            text("project", R.string.field_task, 0), time(1), text("company", R.string.field_team, 2), address(3), text("photographer", R.string.field_photographer, 4), text("note", R.string.field_progress, 5),
        ), WatermarkLayoutSpec(widthFraction = .74f, maxWidthOn1080 = 810f, style = WatermarkStyle.WORK_LOG), R.string.description_work),
        WatermarkTemplate("travel-postcard", R.string.template_travel, listOf(
            text("place", R.string.field_destination, 0), time(1), address(2), text("note", R.string.field_caption, 3),
        ), WatermarkLayoutSpec(widthFraction = .68f, maxWidthOn1080 = 740f, fontOn1080 = 34f, style = WatermarkStyle.TRAVEL), R.string.description_travel),
    )
    fun template(id: String) = templates.firstOrNull { it.id == id } ?: templates.first()
    fun defaults(id: String) = template(id).fields.associate { it.id to it.defaultValue }
    fun snapshot(preset: WatermarkPreset, context: WatermarkContext, visible: Boolean): WatermarkSnapshot {
        val template = template(preset.templateId)
        return WatermarkSnapshot(template.id, preset.id, if (template.showTitle) this.context.getString(template.nameRes) else "",
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
