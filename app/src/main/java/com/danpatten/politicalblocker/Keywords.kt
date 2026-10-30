package com.danpatten.politicalblocker

/**
 * Phrases common in US campaign / PAC fundraising and get-out-the-vote texts, and in commercial
 * promo texts. Weights are added to each model's log-odds: strong markers (legal disclaimers and
 * the fundraising platforms nearly every campaign link goes through) count far more than words like
 * "vote" or "survey" that also appear in ordinary texts.
 */
object Keywords {

    data class Keyword(val phrase: String, val weight: Double)

    private const val STRONG = 3.0
    private const val MEDIUM = 2.0
    private const val WEAK = 1.0

    val political: List<Keyword> = listOf(
        // Disclaimers, fundraising platforms, and the opt-out wording political texting platforms use.
        STRONG to listOf(
            "paid for by", "pd for by", "pd by", "not authorized by any candidate",
            "authorized by", "actblue", "winred", "anedot", "fec", "super pac",
            "political action committee", "secure ad", "official ballot", "stop2end", "stop2quit",
            "txt stop2end",
            // Voter-record pressure and turnout-score texts.
            "whether you vote", "public record", "public records", "voting record", "you voted in",
            "likelihood of you voting", "didthey vote",
        ),
        // Fundraising pressure, parties, offices and well-known names.
        MEDIUM to listOf(
            "chip in", "donate", "donation", "donations", "contribute", "contribution",
            "5x match", "6x match", "4x match", "3x match", "triple match", "matched",
            "matching", "end of quarter", "fundraising deadline", "pac", "campaign",
            "candidate", "election", "elections", "ballot", "ballots", "polling place",
            "polling location", "polling site", "cast your ballot", "vote early",
            "early voting", "early vote", "absentee", "mail in ballot", "register to vote",
            "registered to vote", "voter registration", "voters", "midterm", "midterms", "primary election",
            "congress", "congressman", "congresswoman", "senate", "senator", "house majority",
            "senate majority", "flip the house", "flip the senate", "democrat", "democrats",
            "democratic", "republican", "republicans", "gop", "dnc", "rnc", "dccc", "nrcc",
            "dscc", "nrsc", "maga", "trump", "biden", "harris", "vance", "obama", "pelosi",
            "schumer", "mcconnell", "jeffries", "desantis", "newsom", "aoc", "radical left",
            "far left", "liberals", "conservatives", "impeach", "impeachment", "supreme court",
            "white house", "president", "presidential", "governor", "gubernatorial",
            "attorney general", "state rep", "state sen", "city council", "patriots", "endorsed",
            "endorsement", "petition", "town hall", "swing state", "battleground",
            "vote yes", "vote no", "count on your vote", "commit to voting", "your vote",
            // Get-out-the-vote pushes, often sent casually with no link or opt-out footer.
            "go vote", "come vote", "get out and vote", "get out the vote", "and vote for", "out and vote",
            "ready to vote", "plan to vote",
            // Volunteer asks and fake party memberships.
            "knock doors", "knocking doors", "phone bank", "yard sign", "poll watchers", "party membership",
            "membership card", "member status", "supporter card",
            // Issue / attack ads, which often have no donation ask or party name.
            "supported the", "voted to", "voted for", "voted against", "voting for", "voting against",
            "keeps voting", "your senator", "your congressman", "your congresswoman", "your representative",
            "lawmakers", "politicians", "legislature", "legislation", "endless wars",
            "tariffs", "tax cuts", "tax hike", "tax increase", "billionaires", "special interests",
            // Political pollsters: opinion surveys about issues, races or approval ratings.
            "opinion research", "current issues", "important issues", "issues facing", "approval",
            // Pollster multiple choice ("Reply with 1 through 6").
            "reply with 1",
        ),
        // Common in political texts but also in ordinary conversation.
        WEAK to listOf(
            "vote", "voting", "voted", "vote for", "vote on tuesday", "vote tuesday", "vote this tuesday",
            "poll", "polls", "survey", "majority", "primary",
            "volunteer", "district", "patriot", "deadline", "deficit", "border", "abortion",
            "second amendment", "2nd amendment", "taxpayer", "democracy", "constitution",
            "$5", "$10", "$25", "rush", "urgent", "war", "wars", "gas prices", "price increases",
            "inflation", "utility bills", "per household", "families", "seniors", "deserve better",
            "social security", "medicare", "medicaid", "snap", "immigration", "israel",
        ),
        MEDIUM to POLITICIANS,
    ).flatMap { (weight, phrases) -> phrases.map { Keyword(it, weight) } }

    /** Sales and promo wording. Transactional texts (orders, bills, appointments) use none of it. */
    val spam: List<Keyword> = listOf(
        STRONG to listOf(
            "% off", "shop now", "use code", "promo code", "coupon", "free shipping", "bogo",
            "buy one get one", "flash sale", "pre approved", "extended warranty",
            "you ve won", "you have won", "claim your", "cash offer", "debt relief", "lower your rate",
        ),
        MEDIUM to listOf(
            "sale", "deals", "deal", "discount", "limited time", "today only", "this weekend only",
            "ends tonight", "ends at midnight", "last chance", "biggest sale", "save up to", "order now",
            "apply now", "act now", "exclusive offer", "special offer", "rewards", "bonus points",
            "lowest price", "free gift", "new arrivals", "clearance", "get a quote", "free quote",
            "are you still interested", "selling your home", "job opportunities", "0% apr",
        ),
        WEAK to listOf("offer", "free", "save", "shop", "renew now", "upgrade", "limited"),
    ).flatMap { (weight, phrases) -> phrases.map { Keyword(it, weight) } }

    /**
     * Opt-out footers. Every bulk text has one, political or commercial, so they only say "this was
     * mass-sent" and count once no matter how many variants a footer hits ("Reply STOP to end").
     */
    val optOut: List<String> = listOf(
        "reply stop", "text stop", "txt stop", "stop to end", "stop to quit", "stop to opt out",
        "stop 2 end", "stop end", "stop to unsubscribe", "to opt out", "opt out", "unsubscribe",
    )

    /** One-time codes and similar security texts: never block these, whatever else they say. */
    val transactional: List<String> = listOf(
        "verification code", "security code", "login code", "passcode", "one time code", "one time password",
        "your code is", "do not share", "don t share", "never share",
    )

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
            // 2026 Nebraska races, from texts users reported.
            "dan osborn", "osborn", "don bacon", "brinker harding", "denise powell", "von gillern",
            // Distinctive last names, as ads often use them alone
            "tuberville", "murkowski", "gallego", "hickenlooper", "blumenthal", "ossoff", "warnock", "grassley",
            "mcconnell", "alsobrooks", "slotkin", "klobuchar", "hawley", "ricketts", "cortez masto", "shaheen",
            "gillibrand", "tillis", "lankford", "mullin", "wyden", "merkley", "fetterman", "whitehouse", "thune",
            "blackburn", "hagerty", "cornyn", "barrasso", "lummis", "capito", "pritzker", "whitmer",
            "hochul", "youngkin", "spanberger", "beshear", "gianforte", "dewine", "mcmaster", "lujan grisham",
        )

    fun matchPolitical(text: String): List<Keyword> = match(political, text) + voteBrands(text)

    /**
     * How friends and family write ("can you give me a ride to vote", "i told you not to vote for him lol").
     * Campaign texts talk at "you" and rarely use these, so they mark a text as a personal conversation.
     */
    private val personal = listOf(
        // Not "we're" or "us": campaigns say "we're asking" and "help us" all the time.
        "i", "i m", "im", "i ll", "i d", "i ve", "me", "my", "mine", "lol", "lmao", "haha", "hahaha",
        "lmk", "honestly", "ugh", "omg", "tbh", "idk", "mom", "dad", "grandma", "grandpa", "aunt", "uncle", "sister",
        "brother", "ride", "pick you up", "pick up", "meet us", "come with", "borrow", "cover for me", "argued",
        "i told you", "said they", "said she", "said he", "we should", "together", "want to come", "off work",
        "after work", "say hi", "grabbing", "errands", "your car", "skip the line", "want to join",
    )

    fun matchPersonal(text: String): List<String> = personal.filter { contains(text, it) }

    /**
     * Org names and domains built from "vote" or "ballot" (NextVote, OurBallot, VoteRise, votercx.qualtrics.com).
     * Turnout groups and pollsters sign off with these; stores and people don't. Plain words ("voted") don't count.
     */
    private fun voteBrands(text: String): List<Keyword> =
        PoliticalClassifier.normalize(text).split(' ')
            .filter { VOTE_BRAND.containsMatchIn(it) && it !in VOTE_WORDS && !it.startsWith("devot") }
            .distinct()
            .map { Keyword(it, MEDIUM) }

    private val VOTE_BRAND = Regex("vote|ballot")
    private val VOTE_WORDS = setOf("vote", "votes", "voted", "voter", "voters", "ballot", "ballots")

    fun matchSpam(text: String): List<Keyword> = match(spam, text)

    fun matchOptOut(text: String): String? = optOut.firstOrNull { contains(text, it) }

    fun isTransactional(text: String): Boolean = transactional.any { contains(text, it) }

    private fun match(list: List<Keyword>, text: String): List<Keyword> {
        val padded = padded(text)
        return list.filter { " ${PoliticalClassifier.normalize(it.phrase)} " in padded }
    }

    private fun contains(text: String, phrase: String) = " ${PoliticalClassifier.normalize(phrase)} " in padded(text)

    private fun padded(text: String) = " ${PoliticalClassifier.normalize(text)} "
}
