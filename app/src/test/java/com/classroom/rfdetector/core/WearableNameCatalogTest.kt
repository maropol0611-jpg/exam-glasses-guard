package com.classroom.rfdetector.core

import org.junit.Assert.*
import org.junit.Test

class WearableNameCatalogTest {
    private val resolver = DeviceIdentityResolver(emptyMap(), mapOf(76 to "Apple, Inc."))

    @Test fun glassesNamesAreRecognizedWithTheirBrand() {
        val rayBan = WearableNameCatalog.match("Lee's Ray-Ban Meta (Gen 2)")!!
        assertEquals(WearableKind.SMART_GLASSES, rayBan.kind)
        assertEquals("Ray-Ban / Meta", rayBan.brand)
        assertEquals("Ray-Ban / Meta", WearableNameCatalog.match("Ray-Ban Meta")!!.brand)
        assertEquals("Ray-Ban / Meta", WearableNameCatalog.match("Meta Ray-Ban Display")!!.brand)
        assertEquals("Oakley / Meta", WearableNameCatalog.match("Oakley Meta Vanguard")!!.brand)
    }

    @Test fun koreanAndFullWidthNamesAreRecognized() {
        assertEquals(WearableKind.SMART_GLASSES, WearableNameCatalog.match("준석의 레이밴 메타")!!.kind)
        assertEquals(WearableKind.SMARTWATCH, WearableNameCatalog.match("민수의갤럭시 워치 울트라")!!.kind)
        assertEquals(WearableKind.SMARTWATCH, WearableNameCatalog.match("Ａｐｐｌｅ Ｗａｔｃｈ Ｓｅｒｉｅｓ １０")!!.kind)
        assertEquals(WearableKind.CAMERA, WearableNameCatalog.match("GoPro HERO13 Black")!!.kind)
    }

    @Test fun genericWordsBrandOnlyAndPartialNamesDoNotIdentifyHardware() {
        listOf("Meta", "Ray-Ban", "Wayfarer", "glasses", "Smart Watch", "Watch", "HERO13", "Camera", "NotApple Watch", "Galaxy Watchdog").forEach {
            assertNull("Must not identify $it", WearableNameCatalog.match(it))
        }
        val companyOnly = resolver.resolve("DA:11:22:33:44:55", null, setOf(ScanSource.BLE), setOf(76))
        assertNull(companyOnly.wearableNameMatch)
    }

    @Test fun wifiSsidCannotBecomeGlassesAndCompanyCannotOverrideCandidate() {
        val wifi = resolver.resolve("sim", "Ray-Ban Meta", setOf(ScanSource.WIFI), emptySet())
        assertNull(wifi.wearableNameMatch)
        val ble = resolver.resolve("sim", "Ray-Ban Meta", setOf(ScanSource.BLE), setOf(76))
        assertEquals("Ray-Ban / Meta", ble.wearableNameMatch!!.brand)
        assertEquals(listOf("Apple, Inc."), ble.advertisedCompanies)
    }
}
