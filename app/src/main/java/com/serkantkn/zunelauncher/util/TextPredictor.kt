package com.serkantkn.zunelauncher.util

import com.serkantkn.zunelauncher.data.model.KeyAction
import com.serkantkn.zunelauncher.data.model.KeyboardLanguage
import com.serkantkn.zunelauncher.data.model.KeyboardVariant
import com.serkantkn.zunelauncher.data.model.Suggestion
import kotlin.math.abs
import kotlin.math.min

/**
 * Word suggestions and auto-correction for the Z keyboard.
 *
 * Candidates come from a bundled frequency-ordered word list plus everything the keyboard has
 * learned while typing. A candidate is scored by how common it is, how much of it the user has
 * already typed, and how far it is from what was typed — where "far" is measured with a
 * Damerau-Levenshtein distance that charges half price for keys that sit next to each other on
 * this layout, so "hwllo" is much closer to "hello" than "hzllo" is.
 *
 * Turkish is agglutinative, so a word also counts as known when a listed root plus a plausible
 * suffix explains it ("kitabımda" from "kitap"); that keeps auto-correct from mangling inflected
 * words it has never seen.
 *
 * Everything here is pure Kotlin: the word lists arrive through [source], which the service backs
 * with assets/dict_*.txt and the tests back with a handful of words.
 */
class TextPredictor(private val source: (KeyboardLanguage) -> List<String>) {

    private val dictionaries = HashMap<KeyboardLanguage, Dictionary>()

    /** Loads a word list up front; safe to call from a background thread. */
    fun prepare(language: KeyboardLanguage) {
        dictionary(language)
    }

    /**
     * Up to [limit] entries for the strip. The first one is always what the user actually typed,
     * and at most one entry is flagged as the auto-correction.
     */
    fun suggest(
        typed: String,
        language: KeyboardLanguage,
        learned: Map<String, Int> = emptyMap(),
        limit: Int = 3,
        allowCorrection: Boolean = true
    ): List<Suggestion> {
        if (typed.isBlank() || limit <= 0) return emptyList()
        val lower = KeyboardLayouts.unshifted(typed, language)
        if (!lower.all { it.isLetter() || it == '\'' }) return emptyList()

        val dictionary = dictionary(language)
        val maxCost = if (lower.length <= 4) SINGLE_EDIT else DOUBLE_EDIT
        val scored = HashMap<String, Int>()

        fun consider(word: String, base: Int) {
            if (word == lower) {
                scored[word] = base + EXACT_BONUS
                return
            }
            if (word.startsWith(lower)) {
                scored[word] = base + COMPLETION_BONUS - (word.length - lower.length) * LENGTH_PENALTY
                return
            }
            if (!allowCorrection || lower.length < MIN_CORRECTION_LENGTH) return
            if (abs(word.length - lower.length) * EDIT_COST > maxCost) return
            if (word.first() != lower.first() && maxCost < DOUBLE_EDIT) return
            val cost = distance(lower, word, maxCost, dictionary)
            if (cost <= maxCost) scored[word] = base - cost * CORRECTION_PENALTY
        }

        dictionary.scores.forEach { (word, base) -> consider(word, base) }
        learned.forEach { (word, count) ->
            if (!dictionary.scores.containsKey(word)) consider(word, learnedScore(count))
        }

        val ranked = scored.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.length })
            .map { it.key }
            .filter { it != lower }

        val known = isKnown(lower, language, learned)
        val best = ranked.firstOrNull()
        val corrects = allowCorrection &&
            !known &&
            best != null &&
            lower.length >= MIN_CORRECTION_LENGTH &&
            (scored[best] ?: 0) >= MIN_CORRECTION_SCORE

        val result = ArrayList<Suggestion>(limit)
        result += Suggestion(typed, isTyped = true)
        if (corrects && best != null) result += Suggestion(matchCase(best, typed, language), isCorrection = true)
        for (word in ranked) {
            if (result.size >= limit) break
            if (corrects && word == best) continue
            result += Suggestion(matchCase(word, typed, language))
        }
        return result.take(limit)
    }

    /** The word auto-correct would substitute for [typed], or null to leave it alone. */
    fun correction(
        typed: String,
        language: KeyboardLanguage,
        learned: Map<String, Int> = emptyMap()
    ): String? = suggest(typed, language, learned, limit = 3)
        .firstOrNull { it.isCorrection }
        ?.word

    /** True when the word is in the list, has been learned, or is an inflected form of a root. */
    fun isKnown(word: String, language: KeyboardLanguage, learned: Map<String, Int> = emptyMap()): Boolean {
        val lower = KeyboardLayouts.unshifted(word, language)
        if (lower.length < 2) return true
        val dictionary = dictionary(language)
        if (dictionary.scores.containsKey(lower)) return true
        if ((learned[lower] ?: 0) >= LEARNED_KNOWN_COUNT) return true
        return language == KeyboardLanguage.TURKISH && hasKnownRoot(lower, dictionary)
    }

    private fun hasKnownRoot(word: String, dictionary: Dictionary): Boolean {
        val longest = min(word.length - 1, MAX_ROOT_LENGTH)
        for (length in longest downTo MIN_ROOT_LENGTH) {
            if (word.length - length > MAX_SUFFIX_LENGTH) continue
            val root = word.substring(0, length)
            if (dictionary.scores.containsKey(root)) return true
            // Final consonants soften before a vowel: kitap -> kitab(ım), ağaç -> ağac(a).
            val softened = SOFTENED[root.last()] ?: continue
            if (dictionary.scores.containsKey(root.dropLast(1) + softened)) return true
        }
        return false
    }

    private fun dictionary(language: KeyboardLanguage): Dictionary = dictionaries.getOrPut(language) {
        val words = source(language).map { it.trim() }.filter { it.isNotEmpty() }
        val scores = LinkedHashMap<String, Int>(words.size)
        val size = words.size.coerceAtLeast(1)
        words.forEachIndexed { index, word ->
            val score = MAX_SCORE - index * (MAX_SCORE - MIN_SCORE) / size
            scores.putIfAbsent(KeyboardLayouts.unshifted(word, language), score)
        }
        Dictionary(scores, proximity(language))
    }

    /** Damerau-Levenshtein where a substitution between neighbouring keys costs half. */
    private fun distance(typed: String, word: String, maxCost: Int, dictionary: Dictionary): Int {
        val n = typed.length
        val m = word.length
        var previous2 = IntArray(m + 1)
        var previous = IntArray(m + 1) { it * EDIT_COST }
        var current = IntArray(m + 1)

        for (i in 1..n) {
            current[0] = i * EDIT_COST
            var rowMin = current[0]
            for (j in 1..m) {
                val typedChar = typed[i - 1]
                val wordChar = word[j - 1]
                val substitution = when {
                    typedChar == wordChar -> 0
                    dictionary.isNeighbour(typedChar, wordChar) -> NEAR_COST
                    else -> EDIT_COST
                }
                // Missing or doubled repeated letters ("helo" for "hello") are the most common
                // slip of all, so they cost half of a normal insertion or deletion.
                val skipWordChar = if (j > 1 && wordChar == word[j - 2]) NEAR_COST else EDIT_COST
                val skipTypedChar = if (i > 1 && typedChar == typed[i - 2]) NEAR_COST else EDIT_COST
                var cost = min(
                    previous[j] + skipTypedChar,
                    min(current[j - 1] + skipWordChar, previous[j - 1] + substitution)
                )
                if (i > 1 && j > 1 && typedChar == word[j - 2] && typed[i - 2] == wordChar) {
                    cost = min(cost, previous2[j - 2] + EDIT_COST)
                }
                current[j] = cost
                if (cost < rowMin) rowMin = cost
            }
            if (rowMin > maxCost) return maxCost + 1
            val spare = previous2
            previous2 = previous
            previous = current
            current = spare
        }
        return previous[m]
    }

    /** Keys that sit next to each other on this layout, derived from the letter rows themselves. */
    private fun proximity(language: KeyboardLanguage): Map<Char, Set<Char>> {
        val rows = KeyboardLayouts.letters(language, KeyboardVariant.NORMAL).rows
            .take(3)
            .map { row -> row.keys.filter { it.action == KeyAction.CHARACTER }.map { it.label.first() } }
            .filter { it.isNotEmpty() }

        val neighbours = HashMap<Char, MutableSet<Char>>()
        fun link(a: Char, b: Char) {
            if (a == b) return
            neighbours.getOrPut(a) { mutableSetOf() }.add(b)
            neighbours.getOrPut(b) { mutableSetOf() }.add(a)
        }
        rows.forEachIndexed { rowIndex, row ->
            row.forEachIndexed { index, char ->
                if (index + 1 < row.size) link(char, row[index + 1])
                val below = rows.getOrNull(rowIndex + 1) ?: return@forEachIndexed
                val scaled = index * below.size / row.size
                for (offset in -1..1) {
                    below.getOrNull(scaled + offset)?.let { link(char, it) }
                }
            }
        }
        return neighbours
    }

    private fun learnedScore(count: Int): Int = LEARNED_BASE_SCORE + min(count, 10) * LEARNED_STEP

    /** Copies the capitalisation of what was typed onto a suggestion. */
    private fun matchCase(word: String, typed: String, language: KeyboardLanguage): String = when {
        typed.length > 1 && typed == KeyboardLayouts.shifted(typed, language) ->
            KeyboardLayouts.shifted(word, language)
        typed.firstOrNull()?.isUpperCase() == true ->
            KeyboardLayouts.shifted(word.take(1), language) + word.drop(1)
        else -> word
    }

    private class Dictionary(
        val scores: Map<String, Int>,
        private val neighbours: Map<Char, Set<Char>>
    ) {
        fun isNeighbour(a: Char, b: Char): Boolean = neighbours[a]?.contains(b) == true
    }

    private companion object {
        const val MAX_SCORE = 1000
        const val MIN_SCORE = 200

        // Distances are counted in half-edits so a neighbouring-key typo can cost less than a full one.
        const val EDIT_COST = 2
        const val NEAR_COST = 1
        const val SINGLE_EDIT = 2
        const val DOUBLE_EDIT = 4

        const val EXACT_BONUS = 900
        const val COMPLETION_BONUS = 400
        const val LENGTH_PENALTY = 12
        const val CORRECTION_PENALTY = 120
        const val MIN_CORRECTION_SCORE = 420
        const val MIN_CORRECTION_LENGTH = 3

        const val LEARNED_BASE_SCORE = 520
        const val LEARNED_STEP = 40
        const val LEARNED_KNOWN_COUNT = 2

        const val MIN_ROOT_LENGTH = 3
        const val MAX_ROOT_LENGTH = 9
        const val MAX_SUFFIX_LENGTH = 8

        val SOFTENED = mapOf('b' to 'p', 'c' to 'ç', 'd' to 't', 'ğ' to 'k', 'g' to 'k')
    }
}
