package com.danpatten.politicalblocker

import kotlin.math.exp
import kotlin.math.ln

/**
 * Small on-device text model: two binarized Naive Bayes models trained at startup on the bundled
 * example corpus (plus any user corrections), each blended with a weighted keyword list. One scores
 * political texts against everything else; the other scores commercial spam ads against ordinary
 * texts. Pure Kotlin with no Android dependencies so it can be unit-tested on the JVM.
 */
class PoliticalClassifier(examples: List<Example>) {

    enum class Label { POLITICAL, SPAM, OTHER }

    data class Example(val label: Label, val text: String)

    data class Result(
        val politicalProbability: Double,
        val spamProbability: Double,
        val politicalKeywords: List<String>,
        val spamKeywords: List<String>,
    ) {
        val isPolitical: Boolean get() = politicalProbability >= THRESHOLD

        /** A commercial ad. Political texts are never also spam, so the two toggles stay independent. */
        val isSpam: Boolean get() = !isPolitical && spamProbability >= SPAM_THRESHOLD
    }

    private val politicalModel = NaiveBayes(
        examples.filter { it.label == Label.POLITICAL }.map { it.text },
        examples.filter { it.label != Label.POLITICAL }.map { it.text },
    )
    private val spamModel = NaiveBayes(
        examples.filter { it.label == Label.SPAM }.map { it.text },
        examples.filter { it.label == Label.OTHER }.map { it.text },
    )

    fun classify(text: String): Result {
        val optOut = Keywords.matchOptOut(text)
        // Campaigns and stores mass-send with an opt-out footer or a link; a text with neither is
        // most likely from a person, so it needs much stronger evidence before it's hidden.
        val bulk = if (optOut != null || URL.containsMatchIn(text)) BULK_BONUS else PERSONAL_PENALTY

        val political = Keywords.matchPolitical(text)
        val politicalLogit = NB_WEIGHT * politicalModel.logOdds(text) +
            political.sumOf { it.weight }.coerceAtMost(KEYWORD_CAP) + bulk + BIAS

        val spam = Keywords.matchSpam(text)
        val spamLogit = if (Keywords.isTransactional(text)) {
            Double.NEGATIVE_INFINITY
        } else {
            NB_WEIGHT * spamModel.logOdds(text) + spam.sumOf { it.weight }.coerceAtMost(KEYWORD_CAP) + bulk + SPAM_BIAS
        }

        return Result(
            sigmoid(politicalLogit),
            sigmoid(spamLogit),
            political.map { it.phrase } + listOfNotNull(optOut),
            spam.map { it.phrase } + listOfNotNull(optOut),
        )
    }

    /** Log odds that [text] is political, from the trained Naive Bayes alone. */
    fun naiveBayesLogOdds(text: String): Double = politicalModel.logOdds(text)

    /** Log odds that [text] is a spam ad rather than an ordinary text, from the trained Naive Bayes alone. */
    fun spamLogOdds(text: String): Double = spamModel.logOdds(text)

    private class NaiveBayes(positive: List<String>, negative: List<String>) {
        private val positiveCounts = documentFrequencies(positive)
        private val negativeCounts = documentFrequencies(negative)
        private val positiveDocs = positive.size
        private val negativeDocs = negative.size

        fun logOdds(text: String): Double {
            if (positiveDocs == 0 || negativeDocs == 0) return 0.0
            var logOdds = ln(positiveDocs.toDouble() / negativeDocs)
            for (token in tokenize(withoutOptOut(text)).toSet()) {
                val p = positiveCounts[token] ?: 0
                val n = negativeCounts[token] ?: 0
                // A word seen once tells us more about that one example than about the class, and long
                // texts would otherwise pile up dozens of these into a confident wrong answer.
                if (p + n < MIN_COUNT) continue
                // Laplace-smoothed document frequency per class.
                val pGivenPositive = (p + 1.0) / (positiveDocs + 2.0)
                val pGivenNegative = (n + 1.0) / (negativeDocs + 2.0)
                logOdds += ln(pGivenPositive / pGivenNegative)
            }
            return logOdds.coerceIn(-NB_CLAMP, NB_CLAMP)
        }

        private fun documentFrequencies(texts: List<String>): Map<String, Int> {
            val counts = HashMap<String, Int>()
            for (text in texts) for (token in tokenize(withoutOptOut(text)).toSet()) counts[token] = (counts[token] ?: 0) + 1
            return counts
        }
    }

    companion object {
        const val THRESHOLD = 0.75
        const val SPAM_THRESHOLD = 0.8
        private const val NB_WEIGHT = 0.6
        private const val NB_CLAMP = 8.0
        private const val KEYWORD_CAP = 9.0
        private const val BIAS = -2.0
        private const val SPAM_BIAS = -2.5
        private const val BULK_BONUS = 0.5
        private const val PERSONAL_PENALTY = -2.5
        private const val MIN_COUNT = 2

        private val URL = Regex("""(https?://|www\.|\b[a-z0-9-]+\.(com|org|net|io|co|ly|us|gov|vote|me|info|link)\b/?)""", RegexOption.IGNORE_CASE)

        /** Words too common to say anything about either class. */
        private val STOPWORDS = setOf(
            "the", "to", "and", "of", "in", "on", "at", "for", "is", "it", "be", "are", "this", "that", "with",
            "you", "your", "we", "our", "us", "an", "or", "by", "from", "as", "if", "so", "do", "can", "will",
            "have", "has", "was", "were", "my", "me", "i", "a", "hi", "hello", "hey", "its", "s", "re", "ll", "m",
        )

        /**
         * Opt-out footers are scored once, as the bulk-text signal. Left in, "Reply STOP to end" would also
         * become five or six NB tokens that only say "mass-sent" and swamp the actual content.
         */
        private fun withoutOptOut(text: String): String {
            var normalized = " ${normalize(text)} "
            for (phrase in Keywords.optOut) normalized = normalized.replace(" ${normalize(phrase)} ", " ")
            return normalized
        }

        private fun sigmoid(logit: Double) = 1.0 / (1.0 + exp(-logit))

        /** Lowercased words (minus stopwords) plus adjacent-word bigrams; URLs collapse to their domain words. */
        fun tokenize(text: String): List<String> {
            val words = normalize(text).split(' ').filter { it.length > 1 && it !in STOPWORDS }
            return words + words.zipWithNext { a, b -> "$a $b" }
        }

        /** Lowercases and keeps only letters, digits, `$` and `%`, with `%` as its own word ("40% off" -> "40 % off"). */
        fun normalize(text: String): String =
            text.lowercase().replace("%", " % ").replace(Regex("[^a-z0-9$%]+"), " ").trim()

        /** Parses the bundled `label<TAB>text` corpus, where label is `political`, `spam` or `other`. */
        fun parseCorpus(tsv: String): List<Example> =
            tsv.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") && '\t' in it }
                .map { line ->
                    val (label, text) = line.split('\t', limit = 2)
                    val parsed = when (label.trim()) {
                        "political" -> Label.POLITICAL
                        "spam" -> Label.SPAM
                        else -> Label.OTHER
                    }
                    Example(parsed, text)
                }
                .toList()
    }
}
