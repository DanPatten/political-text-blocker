package com.danpatten.politicalblocker

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
        // Issue / attack ads: no donation ask, no party, no "paid for by". Reported by a user (v1.2.0).
        "Endless wars = endless price increases in Nebraska. Pete Ricketts supported the iron war that has already driven up gas prices in NE by \$582 per household - Building Together Stop to End",
        "Rick Scott voted to cut Social Security and Medicare for Florida seniors. Tell him to keep his hands off your benefits. Stop to end",
        "Your grocery bill is up 20% thanks to reckless tariffs. Ask Senator Tillis why he keeps voting for higher prices. Text STOP to opt out",
        "Gov. Hochul raised your utility bills again. New Yorkers deserve better. Reply STOP to end",
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
        // Ordinary texts using words the issue-ad keywords added in v1.2.1 look for.
        "Star Wars marathon at my place tonight? Bring snacks",
        "Gas prices are insane, glad I finally got the EV lol",
        "Your new Medicare card is in the mail and should arrive in 7-10 days",
        "Did mom's social security deposit come through yet?",
        "Costco membership price increase starts September 1. Renew now to lock in your rate",
    )

    /** Fails listing every misclassified text with its score, not just the first. */
    private fun assertAll(texts: List<String>, political: Boolean) {
        val wrong = texts.map { it to classifier.classify(it) }.filter { (_, r) -> r.isPolitical != political }
        assertTrue(
            wrong.joinToString("\n", "expected political=$political:\n") { (t, r) ->
                "  p=${"%.2f".format(r.probability)} nb=${"%.2f".format(classifier.naiveBayesLogOdds(t))} ${r.matchedKeywords}: $t"
            },
            wrong.isEmpty(),
        )
    }

    @Test
    fun blocksHeldOutPoliticalTexts() = assertAll(political, political = true)

    @Test
    fun allowsHeldOutOrdinaryTexts() = assertAll(other, political = false)

    @Test
    fun blocksEverySampleSentByTheTestButton() {
        val strings = File("src/main/res/values/strings.xml").readText()
        val samples = Regex("<item>(.*?)</item>").findAll(strings).map { it.groupValues[1].replace("\\'", "'") }.toList()
        assertTrue(samples.isNotEmpty())
        for (text in samples) {
            val r = classifier.classify(text)
            assertTrue("expected political (p=${r.probability}): $text", r.isPolitical)
        }
    }

    @Test
    fun keywordsMatchWholeWordsOnly() {
        // "pac" must not match inside "package" / "space".
        assertTrue(Keywords.match("Your package is in the space bay").none { it.phrase == "pac" })
    }
}
