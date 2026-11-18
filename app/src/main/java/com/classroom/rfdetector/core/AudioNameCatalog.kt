package com.classroom.rfdetector.core

import java.text.Normalizer

/** Name-derived categories describe a candidate, never an authenticated product. */
enum class AudioKind {
    EARPHONES, HEADPHONES, AUDIO,
}

data class AudioNameMatch(val brand: String, val kind: AudioKind)

object AudioNameCatalog {
    private data class Rule(val brand: String, val kind: AudioKind, val expression: String) {
        val pattern = Regex("(?:(?<![\\p{L}\\p{N}])|(?<=의))(?:$expression)(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
    }
    private val generation = "(?:\\d+(?:\\s*(?:(?:st|nd|rd|th)?\\s*(?:generation|gen)|세대))?|Gen\\s*\\d+)"
    private val generationSuffix = "(?:\\s*$generation|\\s*\\(\\s*$generation\\s*\\))?"
    private val rules = listOf(
        Rule("Apple", AudioKind.HEADPHONES, "AirPods\\s+Max$generationSuffix|에어팟\\s*맥스$generationSuffix"),
        Rule("Apple", AudioKind.EARPHONES, "AirPods(?:\\s+Pro)?$generationSuffix|에어팟(?:\\s*프로)?$generationSuffix"),
        Rule("Samsung", AudioKind.EARPHONES, "Galaxy\\s+Buds\\s*\\d*(?:\\s*(?:Pro|Live|FE|Core|Plus|\\+))?|갤럭시\\s*버즈\\s*\\d*(?:\\s*(?:프로|라이브|플러스|FE))?"),
        Rule("Sony", AudioKind.EARPHONES, "(?:WF|WI)-[A-Z0-9]{3,16}"),
        Rule("Sony", AudioKind.HEADPHONES, "WH-[A-Z0-9]{3,16}"),
        Rule("Bose", AudioKind.EARPHONES, "(?:Bose\\s+)?QuietComfort(?:\\s+Ultra)?\\s+Earbuds(?:\\s+(?:II|2))?|(?:Bose\\s+)?SoundSport(?:\\s+(?:Wireless|Free))?"),
        Rule("Bose", AudioKind.HEADPHONES, "(?:Bose\\s+)?QuietComfort\\s+(?:Ultra\\s+Headphones|35(?:\\s+II)?|45)"),
        Rule("Bose", AudioKind.AUDIO, "(?:Bose\\s+)?QuietComfort(?:\\s+Ultra)?"),
        Rule("Google", AudioKind.EARPHONES, "Pixel\\s+Buds(?:\\s+(?:Pro(?:\\s*\\d+)?|A-Series|\\d+a?))?|픽셀\\s*버즈(?:\\s*프로)?(?:\\s*\\d+)?"),
        Rule("Jabra", AudioKind.EARPHONES, "Jabra\\s+Elite\\s+(?:Active\\s+)?\\d+[A-Z]?(?:\\s+(?:Active|Gen\\s*2))?"),
        Rule("Beats", AudioKind.EARPHONES, "Beats\\s+(?:Fit\\s+Pro|Studio\\s+Buds(?:\\s*\\+)?|Solo\\s+Buds)|Powerbeats(?:\\s+Pro)?(?:\\s+\\d+)?"),
        Rule("Beats", AudioKind.HEADPHONES, "Beats\\s+(?:Solo\\s*\\d+|Studio(?:\\s*\\d+|\\s+Pro))"),
        Rule("Nothing", AudioKind.EARPHONES, "Nothing\\s+Ear(?:\\s*\\((?:a|\\d+)\\))?"),
        Rule("soundcore", AudioKind.EARPHONES, "soundcore\\s+(?:Liberty\\s+\\d+(?:\\s+(?:Pro|NC))?|Space\\s+A40)"),
        Rule("Sennheiser", AudioKind.EARPHONES, "(?:Sennheiser\\s+)?MOMENTUM\\s+True\\s+Wireless(?:\\s+\\d+)?"),
    )

    fun match(name: String?): AudioNameMatch? {
        if (name.isNullOrBlank()) return null
        val normalized = Normalizer.normalize(name, Normalizer.Form.NFKC)
        return rules.firstNotNullOfOrNull { rule -> rule.pattern.find(normalized)?.let {
            AudioNameMatch(rule.brand, rule.kind)
        } }
    }
}
