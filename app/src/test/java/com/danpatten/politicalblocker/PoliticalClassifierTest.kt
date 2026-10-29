package com.danpatten.politicalblocker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PoliticalClassifierTest {

    private val classifier = PoliticalClassifier(
        PoliticalClassifier.parseCorpus(File("src/main/assets/training.tsv").readText())
    )

    // Held-out examples: none of these are in the training corpus.
    private val political = listOf(
        "Hi, it's Amy with the Elect Rivera campaign. Will you vote for Rivera on Tuesday? Reply STOP to opt out",
        "EMERGENCY: Our 7X match expires at midnight. Chip in $3 to stop the GOP takeover: actblue.com/e",
        "President Trump personally asked for you. Donate now to save our country: winred.com/p",
        "Your vote matters! Find your early voting location at vote.gov. Pd for by Citizens United PAC",
        "Senator Warren here. Will you stand with Democrats and give $5 before the deadline?",
        "Take the official 2026 Conservative Voter Survey now: gop.com/s Txt STOP2END",
    )

    private val other = listOf(
        "Your verification code is 991230",
        "Hey, did you leave your jacket at my place?",
        "Huge sale this weekend at Kohl's! 40% off all shoes. Reply STOP to opt out",
        "Your package from Amazon will arrive tomorrow by 8pm",
        "Reminder: your dental cleaning is Monday at 9am. Reply Y to confirm",
        "Dinner at 7? I booked the Italian place",
        "Wells Fargo: Did you make a $200 purchase at Target? Reply YES or NO",
        "The HOA board will vote on the new fence policy next week",
    )

    @Test
    fun blocksHeldOutPoliticalTexts() {
        for (text in political) {
            val r = classifier.classify(text)
            assertTrue("expected political (p=${r.probability}): $text", r.isPolitical)
        }
    }

    @Test
    fun allowsHeldOutOrdinaryTexts() {
        for (text in other) {
            val r = classifier.classify(text)
            assertFalse("expected not political (p=${r.probability}, ${r.matchedKeywords}): $text", r.isPolitical)
        }
    }

    @Test
    fun keywordsMatchWholeWordsOnly() {
        // "pac" must not match inside "package" / "space".
        assertTrue(Keywords.match("Your package is in the space bay").none { it.phrase == "pac" })
    }
}
