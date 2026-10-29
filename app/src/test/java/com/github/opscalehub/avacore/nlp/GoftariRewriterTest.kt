package com.github.opscalehub.avacore.nlp

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Formal -> spoken Persian, against the shipped goftari.txt table. */
class GoftariRewriterTest {

    private val g = GoftariRewriter.fromStream(File("src/main/assets/tts/goftari.txt").inputStream())

    private fun check(vararg pairs: Pair<String, String>) {
        for ((formal, spoken) in pairs) assertEquals("for «$formal»", spoken, g.rewrite(formal))
    }

    @Test fun presentTenseParadigm() = check(
        "می‌خواهم" to "میخوام", "می‌خواهی" to "میخوای", "می‌خواهد" to "میخواد",
        "می‌خواهیم" to "میخوایم", "می‌خواهید" to "میخواین", "می‌خواهند" to "میخوان",
        "می‌روم" to "میرم", "می‌رود" to "میره", "می‌روند" to "میرن",
        "می‌گوید" to "میگه", "می‌شود" to "میشه", "می‌دهد" to "میده",
        "می‌توانم" to "میتونم", "می‌داند" to "میدونه", "می‌گذارم" to "میذارم",
        "می‌نشیند" to "میشینه", "می‌کند" to "میکنه", "می‌کنید" to "میکنین", "می‌کنند" to "میکنن",
        "می‌بیند" to "میبینه", "می‌گیرد" to "میگیره", "می‌آید" to "میاد", "می‌آیند" to "میان",
    )

    @Test fun subjunctiveAndNegative() = check(
        "بروم" to "برم", "بروند" to "برن", "بگویم" to "بگم", "بیاید" to "بیاد", "بیایند" to "بیآن",
        "نمی‌دانم" to "نِمیدونم", "نمی‌خواهم" to "نمیخوام", "نمی‌شود" to "نِمیشه",
        "نمی‌گوید" to "نمیگه", "نمی‌ترسد" to "نمی‌ترسه", "بخواهد" to "بخواد", "بتواند" to "بِتونه",
    )

    @Test fun wordsAndContractions() = check(
        "آن" to "اون", "آنها" to "اونا", "آن‌ها" to "اونا", "اینها" to "اینا", "ایشان" to "ایشون",
        "خانه" to "خونه", "دیگر" to "دیگه", "اگر" to "اگه", "مگر" to "مگه", "را" to "رو",
        "دارد" to "داره", "هستند" to "هستن", "آمد" to "اومد", "آمدند" to "اومدن", "نیامد" to "نیومد",
    )

    @Test fun copulaAttachesToTheWordBefore() = check(
        "خوب است" to "خوبه",
        "کتاب است." to "کتابه.",
        "ایرانی است" to "ایرانیه",
        "خسته است" to "خسته‌ست",
        "زیبا است" to "زیباست",
        "راه است" to "راهه",
        "گاو است" to "گاوه",
        "این است که" to "اینه که",
        "آن است" to "اونه",
        "خسته‌است" to "خسته‌ست",
        "رطوبت بیست درصد می‌باشد" to "رطوبت بیست درصده",
    )

    @Test fun spaceTypedInsteadOfHalfSpace_isJoinedOnlyForKnownVerbs() = check(
        "می خواهم" to "میخوام",
        "نمی دانم" to "نِمیدونم",
        "می ناب" to "می ناب",         // «می» (wine) followed by a word the table doesn't know
    )

    @Test fun sentence() = check(
        "من می‌خواهم امروز به خانه بروم، چون هوا خیلی سرد است." to
            "من میخوام امروز به خونه برم، چون هوا خیلی سرده.",
        "شما می‌توانید با من بیایید؟" to "شما میتونین با من بیاین؟",
        "نمی‌دانم آنها کجا می‌روند." to "نِمیدونم اونا کجا میرن.",
    )

    @Test fun leavesWhatItDoesNotKnowAlone() = check(
        // nouns ending in د / ند are never touched — only listed verb forms are
        "سفید" to "سفید", "امید" to "امید", "چند" to "چند", "فرزند" to "فرزند", "روند" to "روند",
        // past tense «می‌خورد» could be mixord or mixorad: left alone
        "می‌خورد" to "می‌خورد",
        // «است» inside a word, «هست»/«نیست» are not the copula
        "استاد" to "استاد", "هست" to "هست", "نیست" to "نیست", "دوستان" to "دوستان",
        // «است» at the start has nothing to attach to
        "است" to "است",
        // verse and other formal text the table doesn't cover passes through
        "توانا بود هر که دانا بود" to "توانا بود هر که دانا بود",
    )
}
