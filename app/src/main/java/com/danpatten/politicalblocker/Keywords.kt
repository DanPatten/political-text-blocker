package com.danpatten.politicalblocker

/**
 * Phrases common in US campaign / PAC fundraising and get-out-the-vote texts.
 * Weights are added to the classifier's log-odds: strong markers (legal disclaimers and the
 * fundraising platforms nearly every campaign link goes through) count far more than words like
 * "vote" or "survey" that also appear in ordinary texts.
 */
object Keywords {

    data class Keyword(val phrase: String, val weight: Double)

    private const val STRONG = 3.0
    private const val MEDIUM = 2.0
    private const val WEAK = 1.0

    val all: List<Keyword> = listOf(
        // Disclaimers and fundraising platforms.
        STRONG to listOf(
            "paid for by", "pd for by", "pd by", "not authorized by any candidate",
            "authorized by", "actblue", "winred", "anedot", "fec", "super pac",
            "political action committee", "secure ad", "official ballot", "stop2end", "stop2quit",
            "txt stop2end",
        ),
        // Fundraising pressure, parties, offices and well-known names.
        MEDIUM to listOf(
            "chip in", "donate", "donation", "donations", "contribute", "contribution",
            "5x match", "6x match", "4x match", "3x match", "triple match", "matched",
            "matching", "end of quarter", "fundraising deadline", "pac", "campaign",
            "candidate", "election", "elections", "ballot", "ballots", "polling place",
            "early voting", "early vote", "absentee", "mail in ballot", "register to vote",
            "voter registration", "voters", "midterm", "midterms", "primary election",
            "congress", "congressman", "congresswoman", "senate", "senator", "house majority",
            "senate majority", "flip the house", "flip the senate", "democrat", "democrats",
            "democratic", "republican", "republicans", "gop", "dnc", "rnc", "dccc", "nrcc",
            "dscc", "nrsc", "maga", "trump", "biden", "harris", "vance", "obama", "pelosi",
            "schumer", "mcconnell", "jeffries", "desantis", "newsom", "aoc", "radical left",
            "far left", "liberals", "conservatives", "impeach", "impeachment", "supreme court",
            "white house", "president", "presidential", "governor", "gubernatorial",
            "attorney general", "state rep", "city council", "patriots", "endorsed",
            "endorsement", "petition", "town hall", "swing state", "battleground",
        ),
        // Common in political texts but also in ordinary conversation.
        WEAK to listOf(
            "vote", "voting", "voted", "poll", "polls", "survey", "majority", "primary",
            "volunteer", "district", "patriot", "deadline", "deficit", "border", "abortion",
            "second amendment", "2nd amendment", "taxpayer", "democracy", "constitution",
            "stop to quit", "stop to end", "stop 2 end", "reply stop", "$5", "$10", "$25",
            "rush", "urgent",
        ),
    ).flatMap { (weight, phrases) -> phrases.map { Keyword(it, weight) } }

    fun match(text: String): List<Keyword> {
        val padded = " ${PoliticalClassifier.normalize(text)} "
        return all.filter { " ${PoliticalClassifier.normalize(it.phrase)} " in padded }
    }
}
