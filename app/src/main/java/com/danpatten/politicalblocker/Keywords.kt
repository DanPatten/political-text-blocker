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
            // Issue / attack ads, which often have no donation ask or party name.
            "supported the", "voted to", "voted for", "voted against", "voting for", "voting against",
            "keeps voting", "your senator", "your congressman", "your congresswoman", "your representative",
            "lawmakers", "politicians", "legislature", "endless wars",
            "tariffs", "tax cuts", "tax hike", "tax increase", "billionaires", "special interests",
        ),
        // Common in political texts but also in ordinary conversation.
        WEAK to listOf(
            "vote", "voting", "voted", "poll", "polls", "survey", "majority", "primary",
            "volunteer", "district", "patriot", "deadline", "deficit", "border", "abortion",
            "second amendment", "2nd amendment", "taxpayer", "democracy", "constitution",
            "stop to quit", "stop to end", "stop 2 end", "reply stop", "$5", "$10", "$25",
            "rush", "urgent", "gov", "war", "wars", "gas prices", "price increases", "inflation",
            "utility bills", "per household", "families", "seniors", "deserve better", "social security", "medicare",
            "medicaid",
        ),
        MEDIUM to POLITICIANS,
    ).flatMap { (weight, phrases) -> phrases.map { Keyword(it, weight) } }

    /**
     * US senators and governors (as of 2025-26), by full name plus the last names distinctive enough
     * not to show up in ordinary texts. Attack ads name the politician but often nothing else political.
     */
    private val POLITICIANS: List<String>
        get() = listOf(
            // Senators
            "tommy tuberville", "katie britt", "lisa murkowski", "dan sullivan", "mark kelly", "ruben gallego",
            "john boozman", "tom cotton", "alex padilla", "adam schiff", "michael bennet", "john hickenlooper",
            "richard blumenthal", "chris murphy", "chris coons", "lisa blunt rochester", "rick scott", "ashley moody",
            "jon ossoff", "raphael warnock", "brian schatz", "mazie hirono", "mike crapo", "jim risch",
            "dick durbin", "tammy duckworth", "todd young", "jim banks", "chuck grassley", "joni ernst",
            "jerry moran", "roger marshall", "mitch mcconnell", "rand paul", "bill cassidy", "john kennedy",
            "susan collins", "angus king", "chris van hollen", "angela alsobrooks", "elizabeth warren", "ed markey",
            "gary peters", "elissa slotkin", "amy klobuchar", "tina smith", "roger wicker", "cindy hyde smith",
            "josh hawley", "eric schmitt", "steve daines", "tim sheehy", "deb fischer", "pete ricketts",
            "catherine cortez masto", "jacky rosen", "jeanne shaheen", "maggie hassan", "cory booker", "andy kim",
            "martin heinrich", "ben ray lujan", "chuck schumer", "kirsten gillibrand", "thom tillis", "ted budd",
            "john hoeven", "kevin cramer", "bernie moreno", "jon husted", "james lankford", "markwayne mullin",
            "ron wyden", "jeff merkley", "john fetterman", "dave mccormick", "jack reed", "sheldon whitehouse",
            "lindsey graham", "tim scott", "john thune", "mike rounds", "marsha blackburn", "bill hagerty",
            "john cornyn", "ted cruz", "mike lee", "john curtis", "bernie sanders", "peter welch", "mark warner",
            "tim kaine", "patty murray", "maria cantwell", "shelley moore capito", "jim justice", "ron johnson",
            "tammy baldwin", "john barrasso", "cynthia lummis",
            // Governors
            "kay ivey", "mike dunleavy", "katie hobbs", "sarah huckabee sanders", "gavin newsom", "jared polis",
            "ned lamont", "ron desantis", "brian kemp", "jb pritzker", "mike braun", "kim reynolds",
            "andy beshear", "jeff landry", "janet mills", "wes moore", "maura healey", "gretchen whitmer",
            "tim walz", "tate reeves", "mike kehoe", "greg gianforte", "jim pillen", "joe lombardo",
            "kelly ayotte", "phil murphy", "mikie sherrill", "michelle lujan grisham", "kathy hochul", "josh stein",
            "kelly armstrong", "mike dewine", "kevin stitt", "tina kotek", "josh shapiro", "dan mckee",
            "henry mcmaster", "greg abbott", "spencer cox", "glenn youngkin", "abigail spanberger",
            "bob ferguson", "patrick morrisey", "tony evers",
            // Distinctive last names, as ads often use them alone
            "tuberville", "murkowski", "gallego", "hickenlooper", "blumenthal", "ossoff", "warnock", "grassley",
            "mcconnell", "alsobrooks", "slotkin", "klobuchar", "hawley", "ricketts", "cortez masto", "shaheen",
            "gillibrand", "tillis", "lankford", "mullin", "wyden", "merkley", "fetterman", "whitehouse", "thune",
            "blackburn", "hagerty", "cornyn", "barrasso", "lummis", "capito", "pritzker", "whitmer",
            "hochul", "youngkin", "spanberger", "beshear", "gianforte", "dewine", "mcmaster", "lujan grisham",
        )

    fun match(text: String): List<Keyword> {
        val padded = " ${PoliticalClassifier.normalize(text)} "
        return all.filter { " ${PoliticalClassifier.normalize(it.phrase)} " in padded }
    }
}
