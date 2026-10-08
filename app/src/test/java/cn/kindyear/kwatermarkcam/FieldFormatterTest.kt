package cn.kindyear.kwatermarkcam

import cn.kindyear.kwatermarkcam.domain.model.*
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class FieldFormatterTest {
    private val preset = WatermarkPreset("p", "construction-default", "工程", mapOf("project" to "测试工程"))
    private val context = WatermarkContext(Instant.parse("2026-10-08T06:30:25Z").toEpochMilli(), LocationSnapshot(), ZoneId.of("Asia/Shanghai"))
    private fun field(type: FieldType, id: String = "time", default: String = "") = WatermarkField(id, 0, type, default, order = 0)
    @Test fun dateTimeIsFrozenInRequestedZone() {
        assertEquals("2026-10-08 14:30:25", FieldFormatter.resolve(field(FieldType.DATE_TIME), preset, context, "无位置"))
        assertEquals("2026-10-08", FieldFormatter.resolve(field(FieldType.DATE), preset, context, "无位置"))
        assertEquals("14:30:25", FieldFormatter.resolve(field(FieldType.TIME), preset, context, "无位置"))
    }
    @Test fun manualAddressOverridesLocation() {
        val positioned = context.copy(location = LocationSnapshot(LocationStatus.SUCCESS, 39.0, 98.0, "定位地址"))
        assertEquals("现场手填", FieldFormatter.resolve(field(FieldType.ADDRESS, "address"), preset.copy(fieldValues = mapOf("address" to "现场手填")), positioned, "无位置"))
        assertEquals("定位地址", FieldFormatter.resolve(field(FieldType.ADDRESS, "address"), preset, positioned, "无位置"))
    }
    @Test fun coordinatesAreLocaleIndependentAndUnavailableHasFallback() {
        val loc = context.copy(location = LocationSnapshot(LocationStatus.GEOCODING_FAILED, 39.123456, 98.654321))
        assertEquals("39.12346, 98.65432", FieldFormatter.resolve(field(FieldType.ADDRESS, "address"), preset, loc, "无位置"))
        assertEquals("无位置", FieldFormatter.resolve(field(FieldType.ADDRESS, "address"), preset, context, "无位置"))
    }
    @Test fun defaultsAndLongTextRemainIntactBeforeLayout() {
        assertEquals("默认值", FieldFormatter.resolve(field(FieldType.TEXT, "empty", "默认值"), preset, context, "无位置"))
        val long = "工程施工记录".repeat(80)
        assertEquals(long, FieldFormatter.resolve(field(FieldType.TEXT, "project"), preset.copy(fieldValues = mapOf("project" to long)), context, "无位置"))
    }
    @Test fun intentionallyClearedTextDoesNotRestoreDefault() {
        val cleared = preset.copy(fieldValues = mapOf("project" to ""))
        assertEquals("", FieldFormatter.resolve(field(FieldType.TEXT, "project", "默认值"), cleared, context, "无位置"))
    }
    @Test fun frozenContextDoesNotChangeWhenNewLocationArrives() {
        val snapshot = context.copy(location = LocationSnapshot(LocationStatus.CACHED, 39.0, 98.0, "旧地址"))
        val update = snapshot.copy(location = snapshot.location.copy(address = "新地址", status = LocationStatus.SUCCESS))
        assertEquals("旧地址", FieldFormatter.resolve(field(FieldType.ADDRESS, "address"), preset, snapshot, "无位置"))
        assertEquals("新地址", FieldFormatter.resolve(field(FieldType.ADDRESS, "address"), preset, update, "无位置"))
    }
}
