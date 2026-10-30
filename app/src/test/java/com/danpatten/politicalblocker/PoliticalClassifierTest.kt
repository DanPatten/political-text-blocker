package com.danpatten.politicalblocker

import com.danpatten.politicalblocker.PoliticalClassifier.Label
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

    private fun category(r: PoliticalClassifier.Result) = when {
        r.isPolitical -> Label.POLITICAL
        r.isSpam -> Label.SPAM
        else -> Label.OTHER
    }

    /** Fails listing every misclassified text with its scores, not just the first. */
    private fun assertAll(examples: List<PoliticalClassifier.Example>) {
        val wrong = examples.map { it to classifier.classify(it.text) }.filter { (ex, r) -> category(r) != ex.label }
        assertTrue(
            wrong.joinToString("\n", "${wrong.size} of ${examples.size} misclassified:\n") { (ex, r) ->
                "  expected ${ex.label}, got ${category(r)}: political=${"%.2f".format(r.politicalProbability)} " +
                    "(nb ${"%.1f".format(classifier.naiveBayesLogOdds(ex.text))}) ${r.politicalKeywords} " +
                    "spam=${"%.2f".format(r.spamProbability)} (nb ${"%.1f".format(classifier.spamLogOdds(ex.text))}) " +
                    "${r.spamKeywords}: ${ex.text}"
            },
            wrong.isEmpty(),
        )
    }

    private fun assertAll(texts: List<String>, political: Boolean) =
        assertAll(texts.map { PoliticalClassifier.Example(if (political) Label.POLITICAL else Label.OTHER, it) })

    private fun fixture(name: String) = PoliticalClassifier.parseCorpus(File("src/test/resources/$name").readText())

    @Test
    fun blocksHeldOutPoliticalTexts() = assertAll(political, political = true)

    @Test
    fun allowsHeldOutOrdinaryTexts() = assertAll(other, political = false)

    /** Real texts users reported, so a regression on what people actually receive fails the build. */
    @Test
    fun classifiesReportedTexts() = assertAll(fixture("reported.tsv"))

    /** Both directions: political texts and spam ads caught, ordinary texts with similar wording left alone. */
    @Test
    fun classifiesHeldOutPoliticalSpamAndOrdinaryTexts() = assertAll(fixture("held_out.tsv"))

    @Test
    fun neverBlocksOneTimeCodesAsSpam() {
        val r = classifier.classify("FLASH SALE code: your verification code is 4421. Do not share it. Shop now, 50% off")
        assertTrue(!r.isSpam)
    }

    @Test
    fun politicalTextsAreNotAlsoSpam() {
        // With political blocking off, a political text must not sneak in through the spam toggle.
        for (text in political) assertTrue(text, !classifier.classify(text).isSpam)
    }

    @Test
    fun blocksTypicalCampaignTexts() = assertAll(
        listOf(
            "URGENT: Democrats are 2 seats from losing the Senate. Every gift is 5X MATCHED until midnight. Chip in \$5: secure.actblue.com/test Paid for by Test PAC. Txt STOP2END",
            "GOP ALERT: Your Official 2026 Republican Survey is still unanswered. Respond before the deadline: gop.com/survey Paid for by Test PAC. Reply STOP to quit",
            "Hi, it's Amy with the Elect Rivera for Congress campaign. Early voting starts Saturday. Can we count on your vote? Paid for by Rivera for Congress. Reply STOP to opt out",
            "The radical left is outspending us 3 to 1! Rush \$10 to fight back before the FEC deadline: winred.com/test Pd for by Test PAC",
            "Your polling place may have changed. Make a plan to vote on Election Day and find your location at vote.gov. Paid for by Test Voter Fund",
            "FINAL NOTICE: Your Democratic Party membership has expired. Renew today to keep fighting for our candidates: democrats.org/test STOP2END",
            "Patriot, your Second Amendment rights are on the ballot this November. Vote Republican up and down the ticket! Paid for by Test PAC. Txt STOP to end",
            "End of quarter fundraising deadline is TONIGHT and our campaign is \$14,000 short. Can you contribute \$15? secure.anedot.com/test",
            "Hey, it's Ben, a volunteer for the Senator's re-election campaign. The race is TIED. Can I count on your support on Tuesday? Reply STOP to quit",
            "Breaking: the Supreme Court decision changes everything. Add your name to our petition and chip in \$3 to flip the House: actblue.com/test",
        ),
        political = true,
    )

    @Test
    fun keywordsMatchWholeWordsOnly() {
        // "pac" must not match inside "package" / "space".
        assertTrue(Keywords.matchPolitical("Your package is in the space bay").none { it.phrase == "pac" })
    }
}
