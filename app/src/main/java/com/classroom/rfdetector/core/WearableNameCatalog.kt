package com.classroom.rfdetector.core

import java.text.Normalizer

/** A recognizable advertised name is a candidate, not proof of hardware or AI use. */
enum class WearableKind {
    SMART_GLASSES, SMARTWATCH, CAMERA,
}

data class WearableNameMatch(
    val brand: String,
    val kind: WearableKind,
)

object WearableNameCatalog {
    private data class Rule(val brand: String, val kind: WearableKind, val expression: String) {
        val pattern = Regex("(?:(?<![\\p{L}\\p{N}])|(?<=의))(?:$expression)(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
    }

    private val rules = listOf(
        Rule("Ray-Ban / Meta", WearableKind.SMART_GLASSES,
            "Meta\\s+Ray[- ]?Ban\\s+Display|Ray[- ]?Ban\\s+Meta(?:\\s+(?:Audio|Wayfarer|Headliner|Skyler))?(?:\\s*\\(?Gen\\s*\\d+\\)?)?|(?:레이밴|레이뱅)\\s*메타(?:\\s*\\d+세대)?"),
        Rule("Oakley / Meta", WearableKind.SMART_GLASSES,
            "Oakley\\s+Meta(?:\\s+(?:HSTN|Vanguard))?|오클리\\s*메타(?:\\s*(?:HSTN|뱅가드))?"),
        Rule("Apple", WearableKind.SMARTWATCH,
            "Apple\\s+Watch(?:\\s+(?:Series\\s*\\d+|Ultra(?:\\s*\\d+)?|SE(?:\\s*\\d+)?))?|애플\\s*워치(?:\\s*(?:시리즈\\s*\\d+|울트라(?:\\s*\\d+)?|SE(?:\\s*\\d+)?|\\d+))?"),
        Rule("Samsung", WearableKind.SMARTWATCH,
            "Galaxy\\s+Watch(?:\\s*(?:Ultra(?:\\s*\\d+)?|\\d+(?:\\s*(?:Classic|Pro))?|FE))?|갤럭시\\s*워치(?:\\s*(?:울트라(?:\\s*\\d+)?|\\d+(?:\\s*(?:클래식|프로))?|FE))?"),
        Rule("GoPro", WearableKind.CAMERA,
            "GoPro\\s+HERO\\s*\\d+(?:\\s+Black)?|고프로\\s*히어로\\s*\\d+(?:\\s*블랙)?"),
    )

    fun match(name: String?): WearableNameMatch? {
        if (name.isNullOrBlank()) return null
        val normalized = Normalizer.normalize(name, Normalizer.Form.NFKC)
        return rules.firstNotNullOfOrNull { rule ->
            rule.pattern.find(normalized)?.let { WearableNameMatch(rule.brand, rule.kind) }
        }
    }
}
