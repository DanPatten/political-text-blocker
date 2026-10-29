package com.danpatten.politicalblocker

import kotlin.math.exp
import kotlin.math.ln

/**
 * Small on-device text model: a binarized Naive Bayes trained at startup on the bundled
 * example corpus (plus any user corrections), blended with a weighted political keyword list.
 * Pure Kotlin with no Android dependencies so it can be unit-tested on the JVM.
 */
class PoliticalClassifier(examples: List<Example>) {

    data class Example(val political: Boolean, val text: String)

    data class Result(val probability: Double, val matchedKeywords: List<String>) {
        val isPolitical: Boolean get() = probability >= THRESHOLD
    }

    private val politicalCounts = HashMap<String, Int>()
    private val otherCounts = HashMap<String, Int>()
    private val politicalDocs: Int
    private val otherDocs: Int
    private val vocabSize: Int

    init {
        var pol = 0
        var oth = 0
        for (ex in examples) {
            val counts = if (ex.political) politicalCounts else otherCounts
            if (ex.political) pol++ else oth++
            for (token in tokenize(ex.text).toSet()) counts[token] = (counts[token] ?: 0) + 1
        }
        politicalDocs = pol
        otherDocs = oth
        vocabSize = (politicalCounts.keys + otherCounts.keys).size
    }

    fun classify(text: String): Result {
        val matched = Keywords.match(text)
        val keywordScore = matched.sumOf { it.weight }.coerceAtMost(KEYWORD_CAP)
        val logit = NB_WEIGHT * naiveBayesLogOdds(text) + keywordScore + BIAS
        return Result(1.0 / (1.0 + exp(-logit)), matched.map { it.phrase })
    }

    /** Log odds that [text] is political, from the trained Naive Bayes alone. */
    fun naiveBayesLogOdds(text: String): Double {
        if (politicalDocs == 0 || otherDocs == 0) return 0.0
        var logOdds = ln(politicalDocs.toDouble() / otherDocs)
        for (token in tokenize(text).toSet()) {
            val p = politicalCounts[token] ?: 0
            val o = otherCounts[token] ?: 0
            if (p == 0 && o == 0) continue
            // Laplace-smoothed document frequency per class.
            val pGivenPol = (p + 1.0) / (politicalDocs + 2.0)
            val pGivenOth = (o + 1.0) / (otherDocs + 2.0)
            logOdds += ln(pGivenPol / pGivenOth)
        }
        return logOdds.coerceIn(-NB_CLAMP, NB_CLAMP)
    }

    companion object {
        const val THRESHOLD = 0.75
        private const val NB_WEIGHT = 0.6
        private const val NB_CLAMP = 10.0
        private const val KEYWORD_CAP = 9.0
        private const val BIAS = -1.5

        /** Lowercased words plus adjacent-word bigrams; URLs collapse to their domain words. */
        fun tokenize(text: String): List<String> {
            val words = normalize(text).split(' ').filter { it.length > 1 }
            return words + words.zipWithNext { a, b -> "$a $b" }
        }

        fun normalize(text: String): String =
            text.lowercase().replace(Regex("[^a-z0-9$%]+"), " ").trim()

        /** Parses the bundled `label<TAB>text` corpus, where label is `political` or `other`. */
        fun parseCorpus(tsv: String): List<Example> =
            tsv.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") && '\t' in it }
                .map { line ->
                    val (label, text) = line.split('\t', limit = 2)
                    Example(label.trim() == "political", text)
                }
                .toList()
    }
}
