package com.classroom.rfdetector.core

import org.junit.Assert.*
import org.junit.Test

class AudioNameCatalogTest {
    @Test fun recognizesVariantsAndKoreanNames() {
        val cases = listOf(
            "민수의 에어팟 프로2" to Pair("Apple", AudioKind.EARPHONES),
            "민수의에어팟프로2세대" to Pair("Apple", AudioKind.EARPHONES),
            "AirPods Pro 2" to Pair("Apple", AudioKind.EARPHONES),
            "AirPods Pro (2nd generation)" to Pair("Apple", AudioKind.EARPHONES),
            "AirPods Pro 2세대" to Pair("Apple", AudioKind.EARPHONES),
            "AirPods Pro Gen 2" to Pair("Apple", AudioKind.EARPHONES),
            "AirPods Max" to Pair("Apple", AudioKind.HEADPHONES),
            "갤럭시 버즈3 프로" to Pair("Samsung", AudioKind.EARPHONES),
            "Galaxy Buds2 Pro" to Pair("Samsung", AudioKind.EARPHONES),
            "Sony WH-1000XM5" to Pair("Sony", AudioKind.HEADPHONES),
            "Sony WF-1000XM5" to Pair("Sony", AudioKind.EARPHONES),
            "Bose QuietComfort Ultra Earbuds" to Pair("Bose", AudioKind.EARPHONES),
            "Jabra Elite 10 Gen 2" to Pair("Jabra", AudioKind.EARPHONES),
            "Pixel Buds Pro 2" to Pair("Google", AudioKind.EARPHONES),
            "Beats Studio Buds +" to Pair("Beats", AudioKind.EARPHONES),
            "Nothing Ear (a)" to Pair("Nothing", AudioKind.EARPHONES),
            "soundcore Liberty 4 NC" to Pair("soundcore", AudioKind.EARPHONES),
        )
        cases.forEach { (name, expected) ->
            val match = AudioNameCatalog.match(name)
            assertNotNull(name, match)
            assertEquals(name, expected, Pair(match!!.brand, match.kind))
        }
    }

    @Test fun genericOrEmbeddedWordsAreNotProductIdentification() {
        listOf("Elite router", "Elite 10", "NotAirPods", "AirPodsProMax", "smartwatch", "Buds", "Ear", "my pixel router").forEach {
            assertNull(it, AudioNameCatalog.match(it))
        }
        assertNull(AudioNameCatalog.match(null))
    }

    @Test fun unicodeCompatibilityNamesAreNormalized() {
        assertEquals(AudioKind.EARPHONES, AudioNameCatalog.match("ＡｉｒＰｏｄｓ Ｐｒｏ ２")!!.kind)
    }

    @Test fun nameOnlyBrandIsMarkedAsCandidateAndNeverAppliedToWifiSsid() {
        val resolver = DeviceIdentityResolver(emptyMap(), emptyMap())
        val ble = resolver.resolve("random", "에어팟 프로", setOf(ScanSource.BLE), emptySet())
        assertEquals("Apple · 이름 기반 후보", ble.summary)
        assertEquals(AudioKind.EARPHONES, ble.audioNameMatch!!.kind)
        val wifi = resolver.resolve("random", "AirPods Pro", setOf(ScanSource.WIFI), emptySet())
        assertNull(wifi.audioNameMatch)
    }

    @Test fun unknownIdDoesNotHideKnownCompany() {
        val resolver = DeviceIdentityResolver(emptyMap(), mapOf(76 to "Apple, Inc."))
        val identity = resolver.resolve("random", "Galaxy Buds3 Pro", setOf(ScanSource.BLE), setOf(1, 76))
        assertTrue(identity.summary.startsWith("Apple"))
    }
}
