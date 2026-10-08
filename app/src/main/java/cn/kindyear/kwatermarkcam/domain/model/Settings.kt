package cn.kindyear.kwatermarkcam.domain.model

/** Stored settings are validated on read, so older/unknown enum values have safe defaults. */
data class AppSettings(
    val theme: String = "system", val dynamicColor: Boolean = true,
    val frontCamera: Boolean = false, val wideAspect: Boolean = false,
    val flash: String = "off", val grid: Boolean = false,
    val watermark: Boolean = true, val location: Boolean = false,
    val templateId: String = "construction-default", val presetId: String = "",
)
data class PhotoRecord(val id: String, val uri: String, val createdAt: Long, val templateId: String, val presetId: String, val width: Int, val height: Int)
