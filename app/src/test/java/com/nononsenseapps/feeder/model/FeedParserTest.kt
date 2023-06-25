package com.nononsenseapps.feeder.model

import com.nononsenseapps.feeder.di.networkModule
import com.nononsenseapps.jsonfeed.Author
import com.nononsenseapps.jsonfeed.cachingHttpClient
import java.io.BufferedReader
import java.net.URL
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.intellij.lang.annotations.Language
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.singleton

class FeedParserTest : DIAware {
    @Rule
    @JvmField
    var tempFolder = TemporaryFolder()

    private val feedParser: FeedParser by instance()

    override val di by DI.lazy {
        bind<OkHttpClient>() with singleton { cachingHttpClient() }
        import(networkModule)
    }

    @Test
    @Ignore
    fun getAlternateLinksHandlesYoutube() {
        // I want this to be an Online test to make sure that I notice if/when Youtube changes something which breaks it
        runBlocking {
            val feeds =
                feedParser.getSiteMetaData(URL("https://www.youtube.com/watch?v=-m5I_5Vnh6A"))!!.alternateFeedLinks.first()
            assertEquals(
                AlternateLink(
                    URL("https://www.youtube.com/feeds/videos.xml?channel_id=UCG1h-Wqjtwz7uUANw6gazRw"),
                    "atom",
                ),
                feeds,
            )
        }
    }

    @Test
    fun dcCreatorEndsUpAsAuthor() = runBlocking {
        readResource("openstreetmap.xml") {
            val feed = feedParser.parseFeedResponse(
                URL("http://https://www.openstreetmap.org/diary/rss"),
                it,
                null,
            )
            val item = feed.items!!.first()

            assertEquals(Author(name = "0235"), item.author)
        }
    }

    @Test
    @Throws(Exception::class)
    fun htmlAtomContentGetsUnescaped() {
        readResource("atom_hnapp.xml") {
            val feed = feedParser.parseFeedResponse(
                URL("http://hnapp.com/rss?q=type%3Astory%20score%3E36%20-bitcoin%20-ethereum%20-cryptocurrency%20-blockchain%20-snowden%20-hiring%20-ask"),
                it,
                null,
            )

            val item = feed.items!![0]
            assertEquals(
                "37 – Spectre Mitigations in Microsoft's C/C++ Compiler",
                item.title,
            )
            assertEquals(
                "37 points, 1 comment",
                item.content_text,
            )
            assertEquals(
                "<p>37 points, <a href=\"https://news.ycombinator.com/item?id=16381978\">1 comment</a></p>",
                item.content_html,
            )
        }
    }

    @Test
    @Throws(Exception::class)
    fun enclosedImageIsUsedAsThumbnail() {
        readResource("rss_lemonde.xml") {
            val feed = feedParser.parseFeedResponse(
                URL("http://www.lemonde.fr/rss/une.xml"),
                it,
                null,
            )

            val item = feed.items!![0]
            assertEquals(
                "http://s1.lemde.fr/image/2018/02/11/644x322/5255112_3_a8dc_martin-fourcade_02be61d126b2da39d977b2e1902c819a.jpg",
                item.image,
            )
        }
    }

    @Test
    fun parsesYoutubeMediaInfo() = runBlocking {
        val feed = readResource("atom_youtube.xml") {
            feedParser.parseFeedResponse(
                URL("http://www.youtube.com/feeds/videos.xml"),
                it,
                null,
            )
        }

        val item = feed.items!!.first()

        assertEquals("Can You Observe a Typical Universe?", item.title)
        assertEquals("https://i2.ytimg.com/vi/q-6oU3jXAho/hqdefault.jpg", item.image)
        assertTrue {
            item.content_text!!.startsWith("Sign Up on Patreon to get access to the Space Time Discord!")
        }
    }

    @Test
    fun parsesPeertubeMediaInfo() = runBlocking {
        val feed = readResource("rss_peertube.xml") {
            feedParser.parseFeedResponse(URL("https://framatube.org/feeds/videos.xml"), it, null)
        }

        val item = feed.items!!.first()

        assertEquals("1.4. Et les réseaux sociaux ?", item.title)
        assertEquals(
            "https://framatube.org/static/thumbnails/ed5c048d-01f3-4ceb-97db-6e278de512b0.jpg",
            item.image,
        )
        assertTrue {
            item.content_text!!.startsWith("MOOC CHATONS#1 - Internet")
        }
    }

    @Test
    fun parsesYahooMediaRss() = runBlocking {
        val feed = readResource("rss_mediarss.xml") {
            feedParser.parseFeedResponse(
                URL("https://rutube.ru/mrss/video/person/11234072/"),
                it,
                null,
            )
        }

        val item = feed.items!!.first()

        assertEquals("Камеди Клаб: «3 сентября»", item.title)
        assertEquals(
            "https://pic.rutubelist.ru/video/93/24/93245691f0e18d063da5fa5cd60fa6de.jpg?size=l",
            item.image,
        )
    }

    @Test
    fun parsesYahooMediaRss2() = runBlocking {
        val feed = readResource("rss_myanimelist.xml") {
            feedParser.parseFeedResponse(
                URL("https://myanimelist.net/rss/news.xml"),
                it,
                null,
            )
        }

        val item = feed.items!!.first()

        assertEquals(
            "https://cdn.myanimelist.net/s/common/uploaded_files/1664092688-dd34666e64d7ae624e6e2c70087c181f.jpeg",
            item.image,
        )
    }

    @Test
    fun parsesYahooMediaRssPicksLargestThumbnail() = runBlocking {
        val feed = readResource("rss_theguardian.xml") {
            feedParser.parseFeedResponse(
                URL("https://www.theguardian.com/world/rss"),
                it,
                null,
            )
        }

        val item = feed.items!!.first()

        assertEquals(
            "https://i.guim.co.uk/img/media/c4d7049b24ee34d1c4c630c751094cabc57c54f6/0_32_6000_3601/master/6000.jpg?width=460&quality=85&auto=format&fit=max&s=919d72fef6d4f3469aff69e94964126c",
            item.image,
        )
    }

    @Test
    fun encodingTestWithSmileys() = runBlocking {
        val feed = readResource("rss_lawnchair.xml") {
            feedParser.parseFeedResponse(
                URL("https://nitter.weiler.rocks/lawnchairapp/rss"),
                it,
                null,
            )
        }

        val item = feed.items!!.first()

        assertTrue {
            "\uD83D\uDE0D\uD83E\uDD29" in item.content_html!!
        }
    }

    @Test
    @Throws(Exception::class)
    fun getAlternateFeedLinksDoesNotReturnRelativeLinks() {
        readResource("fz.html") {
            val metadata = feedParser.getSiteMetaDataInHtml(URL("https://www.fz.se"), it)
            assertEquals(
                listOf(
                    AlternateLink(link = URL("https://www.fz.se/feeds/nyheter"), type = "application/rss+xml"),
                    AlternateLink(link = URL("https://www.fz.se/feeds/forum"), type = "application/rss+xml"),
                ),
                metadata!!.alternateFeedLinks,
            )
        }
    }

    @Test
    fun findsAppleTouchIconForFeed() = runBlocking {
        val metadata = readResource("fz.html") {
            feedParser.getSiteMetaDataInHtml(URL("https://www.fz.se"), it)
        }

        assertEquals("https://www.fz.se/apple-touch-icon.png", metadata!!.feedImage)
    }

    @Test
    fun findsAppleTouchIconInHtml() = runBlocking {
        val icon = readResource("fz.html") {
            feedParser.getFeedIconInHtml(it, URL("https://www.fz.se"))
        }

        assertEquals("https://www.fz.se/apple-touch-icon.png", icon)
    }

    @Test
    fun findsFaviconInHtml() = runBlocking {
        val icon = readResource("slashdot.html") {
            feedParser.getFeedIconInHtml(it, URL("https://slashdot.org"))
        }

        assertEquals("https://slashdot.org/favicon.ico", icon)
    }

    @Test
    @Throws(Exception::class)
    fun successfullyParsesAlternateLinkInBodyOfDocument() {
        readResource("nixos.html") {
            val metadata = feedParser.getSiteMetaDataInHtml(URL("https://nixos.org"), it)
            assertEquals(
                listOf(AlternateLink(URL("https://nixos.org/news-rss.xml"), "application/rss+xml")),
                metadata!!.alternateFeedLinks,
            )
        }
    }

    @Test
    @Throws(Exception::class)
    fun getAlternateFeedLinksResolvesRelativeLinksGivenBaseUrl() {
        readResource("fz.html") {
            val metadata = feedParser.getSiteMetaDataInHtml(URL("https://www.fz.se/index.html"), it)
            assertEquals(
                listOf(
                    AlternateLink(URL("https://www.fz.se/feeds/nyheter"), "application/rss+xml"),
                    AlternateLink(URL("https://www.fz.se/feeds/forum"), "application/rss+xml"),
                ),
                metadata!!.alternateFeedLinks,
            )
        }
    }

    @Test
    @Throws(Exception::class)
    fun encodingIsHandledInAtomRss() = runBlocking {
        val feed = golemDe.use { feedParser.parseFeedResponse(it) }

        assertEquals(true, feed?.items?.get(0)?.content_text?.contains("größte"))
    }

    // Bug in Rome which I am working around, this will crash if not worked around
    @Test
    @Throws(Exception::class)
    fun emptySlashCommentsDontCrashParsingAndEncodingIsStillRespected() = runBlocking {
        val feed = emptySlashComment.use { feedParser.parseFeedResponse(it) }

        assertEquals(1, feed?.items?.size)
        assertEquals(
            true,
            feed?.items?.get(0)?.content_text?.contains("größte"),
            feed?.items?.get(0)?.content_text!!,
        )
    }

    @Test
    @Throws(Exception::class)
    fun correctAlternateLinkInAtomIsUsedForUrl() = runBlocking {
        val feed = utdelningsSeglarenAtom.use { feedParser.parseFeedResponse(it) }

        assertEquals(
            "http://utdelningsseglaren.blogspot.com/2017/12/tips-pa-6-podcasts.html",
            feed?.items?.first()?.url,
        )
    }

    @Test
    @Throws(Exception::class)
    fun relativeLinksAreMadeAbsoluteAtom() = runBlocking {
        val feed = feedParser.parseFeedResponse(
            URL("http://cowboyprogrammer.org/feed.atom"),
            atomRelative,
            null,
        )
        assertNotNull(feed)

        assertEquals("http://cowboyprogrammer.org/feed.atom", feed.feed_url)
    }

    @Test
    @Throws(Exception::class)
    fun relativeLinksAreMadeAbsoluteAtomNoBase() = runBlocking {
        val feed = feedParser.parseFeedResponse(
            URL("http://cowboyprogrammer.org/feed.atom"),
            atomRelativeNoBase,
            null,
        )
        assertNotNull(feed)

        assertEquals("http://cowboyprogrammer.org/feed.atom", feed.feed_url)
    }

    @Test
    @Throws(Exception::class)
    fun relativeFeedLinkInRssIsMadeAbsolute() = runBlocking {
        val feed = lineageosRss.use { feedParser.parseFeedResponse(it) }
        assertNotNull(feed)

        assertEquals("https://lineageos.org/", feed.home_page_url)
        assertEquals("https://lineageos.org/feed.xml", feed.feed_url)

        assertEquals("https://lineageos.org/Changelog-16", feed.items?.get(0)?.id)
        assertEquals("https://lineageos.org/Changelog-16/", feed.items?.get(0)?.url)
        assertEquals(
            "https://lineageos.org/images/2018-02-25/lineageos-15.1-hero.png",
            feed.items?.get(0)?.image,
        )
    }

    @Test
    @Throws(Exception::class)
    fun noStyles() = runBlocking {
        val feed = researchRsc.use { feedParser.parseFeedResponse(it) }
        assertNotNull(feed)

        assertEquals("http://research.swtch.com/feed.atom", feed.feed_url)

        assertEquals(17, feed.items!!.size)

        val (_, _, _, title, _, _, summary, image) = feed.items!![9]

        assertEquals("http://research.swtch.com/qr-bbc.png", image)

        assertEquals(
            "QArt Codes",
            title,
        )

        // Style tags should be ignored
        assertEquals(
            "QR codes are 2-dimensional bar codes that encode arbitrary text strings. A common use of QR codes is to encode URLs so that people can scan a QR code (for example, on an advertising poster, building r",
            summary,
        )
    }

    @Test
    @Throws(Exception::class)
    fun feedAuthorIsUsedAsFallback() = runBlocking {
        val feed = researchRsc.use { feedParser.parseFeedResponse(it) }
        assertNotNull(feed)

        assertEquals("http://research.swtch.com/feed.atom", feed.feed_url)

        assertEquals(17, feed.items!!.size)

        val (_, _, _, _, _, _, _, _, _, _, _, author) = feed.items!![9]

        assertEquals("Russ Cox", feed.author!!.name)
        assertEquals(feed.author, author)
    }

    @Test
    @Throws(Exception::class)
    fun nixos() = runBlocking {
        val feed = nixosRss.use { feedParser.parseFeedResponse(it) }
        assertNotNull(feed)

        assertEquals("http://nixos.org/news-rss.xml", feed.feed_url)

        assertEquals(99, feed.items!!.size)

        val (_, _, _, title, _, _, _, image) = feed.items!![0]

        assertEquals("https://nixos.org/logo/nixos-logo-18.09-jellyfish-lores.png", image)
        assertEquals("NixOS 18.09 released", title)
    }

    @Test
    @Throws(Exception::class)
    fun nixers() = runBlocking {
        val feed = nixersRss.use { feedParser.parseFeedResponse(it) }
        assertNotNull(feed)

        assertEquals("https://newsletter.nixers.net/feed.xml", feed.feed_url)

        assertEquals(111, feed.items!!.size)

        val item = feed.items!![0]

        // Timezone issues - so only verify date
        assertTrue(message = "Expected a pubdate to have been parsed") {
            item.date_published!!.startsWith("2019-01-25")
        }
    }

    @Test
    fun doesNotFetchVideos(): Unit = runBlocking {
        val exception = assertFails {
            videoResponse.use { feedParser.parseFeedResponse(it) }
        }
        assertTrue {
            exception.message?.contains("video/mp4") ?: false
        }
    }

    @Test
    @Throws(Exception::class)
    fun cyklist() = runBlocking {
        val feed = cyklistBloggen.use { feedParser.parseFeedResponse(it) }
        assertNotNull(feed)

        assertEquals("http://www.cyklistbloggen.se/feed/", feed.feed_url)

        assertEquals(10, feed.items!!.size)

        val (_, _, _, title, _, _, summary, image) = feed.items!![0]

        assertEquals(
            "http://www.cyklistbloggen.se/wp-content/uploads/2014/01/Danviksklippan-skyltad.jpg",
            image,
        )

        assertEquals(
            "Ingen ombyggning av Danvikstull",
            title,
        )

        // Make sure character 160 (non-breaking space) is trimmed
        assertEquals(
            "För mer än tre år sedan aviserade dåvarande Allians-styrda Stockholms Stad att man äntligen skulle bredda den extremt smala passagen på pendlingsstråket vid Danvikstull: I smalaste passagen är gångdel",
            summary,
        )
    }

    @Test
    @Throws(Exception::class)
    fun cowboy() = runBlocking {
        val feed = cowboyRss.use { feedParser.parseFeedResponse(it) }
        assertNotNull(feed)

        assertEquals("http://cowboyprogrammer.org/index.xml", feed.feed_url)

        assertEquals(15, feed.items!!.size)

        var entry = feed.items!![1]

        assertEquals(
            "https://cowboyprogrammer.org/images/zopfli_all_the_things.jpg",
            entry.image,
        )

        // Snippet should not contain images
        entry = feed.items!![4]
        assertEquals("Fixing the up button in Python shell history", entry.title)
        assertEquals(
            "In case your python/ipython shell doesn’t have a working history, e.g. pressing ↑ only prints some nonsensical ^[[A, then you are missing either the readline or ncurses library. Ipython is more descri",
            entry.summary,
        )
        // Snippet should not contain links
        entry = feed.items!![1]
        assertEquals("Compress all the images!", entry.title)
        assertEquals(
            "Update 2016-11-22: Made the Makefile compatible with BSD sed (MacOS) One advantage that static sites, such as those built by Hugo, provide is fast loading times. Because there is no processing to be d",
            entry.summary,
        )
    }

    @Test
    @Throws(Exception::class)
    fun rss() = runBlocking {
        val feed = cornucopiaRss.use { feedParser.parseFeedResponse(it) }
        val (_, _, home_page_url, feed_url, _, _, _, _, _, _, _, _, items) = feed!!

        assertEquals("http://cornucopia.cornubot.se/", home_page_url)
        assertEquals("https://cornucopia.cornubot.se/feeds/posts/default?alt=rss", feed_url)

        assertEquals(25, items!!.size)
        val (_, _, _, title, content_html, _, summary, image, _, _, _, _, _, attachments) = items[0]

        assertEquals(
            "Tredje månaden med överhettad svensk ekonomi - tydlig säljsignal för börsen",
            title,
        )
        assertEquals(
            "Tredje månaden med överhettad svensk ekonomi - tydlig säljsignal för börsen",
            title,
        )

        assertEquals(
            "För tredje månaden på raken ligger Konjunkturinsitutets barometerindikator (\"konjunkturbarometern\") kvar i överhettat läge. Det råder alltså en klart och tydligt långsiktig säljsignal i enlighet med k",
            summary,
        )
        assertTrue(content_html!!.startsWith("För tredje månaden på raken"))
        assertEquals(
            "https://1.bp.blogspot.com/-hD_mqKJx-XY/WLwTIKSEt6I/AAAAAAAAqfI/sztWEjwSYAoN22y_YfnZ-yotKjQsypZHACLcB/s72-c/konj.png",
            image,
        )

        assertEquals<List<Any>?>(emptyList(), attachments)
    }

    @Test
    @Throws(Exception::class)
    fun atom() = runBlocking {
        val feed = cornucopiaAtom.use { feedParser.parseFeedResponse(it) }
        val (_, _, home_page_url, feed_url, _, _, _, _, _, _, _, _, items) = feed!!

        assertEquals("http://cornucopia.cornubot.se/", home_page_url)
        assertEquals("http://www.blogger.com/feeds/8354057230547055221/posts/default", feed_url)

        assertEquals(25, items!!.size)
        val (_, _, _, title, content_html, _, summary, image, _, _, _, _, _, attachments) = items[0]

        assertEquals(
            "Tredje månaden med överhettad svensk ekonomi - tydlig säljsignal för börsen",
            title,
        )
        assertEquals(
            "Tredje månaden med överhettad svensk ekonomi - tydlig säljsignal för börsen",
            title,
        )

        assertEquals(
            "För tredje månaden på raken ligger Konjunkturinsitutets barometerindikator (\"konjunkturbarometern\") kvar i överhettat läge. Det råder alltså en klart och tydligt långsiktig säljsignal i enlighet med k",
            summary,
        )
        assertTrue(content_html!!.startsWith("För tredje månaden på raken"))
        assertEquals(
            "https://1.bp.blogspot.com/-hD_mqKJx-XY/WLwTIKSEt6I/AAAAAAAAqfI/sztWEjwSYAoN22y_YfnZ-yotKjQsypZHACLcB/s72-c/konj.png",
            image,
        )

        assertEquals<List<Any>?>(emptyList(), attachments)
    }

    @Test
    @Throws(Exception::class)
    fun atomCowboy() = runBlocking {
        val feed = cowboyAtom.use { feedParser.parseFeedResponse(it) }
        val (_, _, _, _, _, _, _, icon, _, _, _, _, items) = feed!!

        assertEquals(15, items!!.size)
        val (id, _, _, _, _, _, _, image, _, date_published) = items[1]

        assertEquals("http://cowboyprogrammer.org/dummy-id-to-distinguis-from-alternate-link", id)
        assertTrue(date_published!!.contains("2016"), "Should take the updated timestamp")
        assertEquals(
            "http://localhost:1313/images/zopfli_all_the_things.jpg",
            image,
        )

        assertEquals("http://localhost:1313/css/images/logo.png", icon)
    }

    @Test
    @Throws(Exception::class)
    fun morningPaper() = runBlocking {
        val feed = morningPaper.use { feedParser.parseFeedResponse(it) }
        val (_, _, home_page_url, feed_url, _, _, _, _, _, _, _, _, items) = feed!!

        assertEquals("https://blog.acolyer.org", home_page_url)
        assertEquals("https://blog.acolyer.org/feed/", feed_url)

        assertEquals(10, items!!.size)
        val (_, _, _, title, _, _, _, image) = items[0]

        assertEquals(
            "Thou shalt not depend on me: analysing the use of outdated JavaScript libraries on the web",
            title,
        )

        assertEquals(
            "http://1.gravatar.com/avatar/a795b4f89a6d096f314fc0a2c80479c1?s=96&d=identicon&r=G",
            image,
        )
    }

    @Test
    @Throws(Exception::class)
    fun londoner() = runBlocking {
        val feed = londoner.use { feedParser.parseFeedResponse(it) }
        val (_, _, home_page_url, feed_url, _, _, _, _, _, _, _, _, items) = feed!!

        assertEquals("http://londonist.com/", home_page_url)
        assertEquals("http://londonist.com/feed", feed_url)

        assertEquals(40, items!!.size)
        val (_, _, _, title, _, _, _, image) = items[0]

        assertEquals(
            "Make The Most Of London's Offerings With Chip",
            title,
        )

        assertEquals(
            "http://assets.londonist.com/uploads/2017/06/chip_2.jpg",
            image,
        )
    }

    @Test
    @Throws(Exception::class)
    fun noLinkShouldBeNull() = runBlocking {
        val feed = anon.use { feedParser.parseFeedResponse(it) }!!

        assertEquals("http://ANON.com/sub", feed.home_page_url)
        assertEquals("http://anon.com/rss", feed.feed_url)
        assertEquals("ANON", feed.title)
        assertEquals("ANON", feed.description)

        assertEquals(1, feed.items!!.size)
        val item = feed.items!![0]

        assertNull(item.url)

        assertEquals("ANON", item.title)
        assertEquals("ANON", item.content_text)
        assertEquals("ANON", item.content_html)
        assertEquals("ANON", item.summary)

        /*assertEquals("2018-12-13 00:00:00",
                item.date_published)*/
    }

    @Test
    fun golem2ShouldBeParsedDespiteEmptySlashComments() = runBlocking {
        val feed = golemDe2.use { feedParser.parseFeedResponse(it) }

        assertEquals("Golem.de", feed?.title)
    }

    @Test
    @Throws(Exception::class)
    @Ignore
    fun cowboyAuthenticated() = runBlocking {
        runBlocking {
            val feed =
                feedParser.parseFeedUrl(URL("https://test:test@cowboyprogrammer.org/auth_basic/index.xml"))
            assertEquals("Cowboy Programmer", feed?.title)
        }
    }

    @Test
    fun diskuse() = runBlocking {
        runBlocking {
            val feed = diskuse.use { feedParser.parseFeedResponse(it) }
            val entry = feed!!.items!!.first()

            assertEquals(
                "Kajman, O této diskusi: test <pre> in <description> and <b>bold</b> in title",
                entry.title,
            )
        }
    }

    @Test
    @Ignore
    @Throws(Exception::class)
    fun fz() = runBlocking {
        val feed = fz.use { feedParser.parseFeedResponse(it) }
        val (_, _, home_page_url, feed_url, _, _, _, _, _, _, _, _, items) = feed!!

        assertEquals("http://www.fz.se/nyheter/", home_page_url)
        assertNull(feed_url)

        assertEquals(20, items!!.size)
        val (_, _, _, title, _, _, _, image) = items[0]

        assertEquals(
            "Nier: Automata bjuder på maffig lanseringstrailer",
            title,
        )

        assertEquals(
            "http://d2ihp3fq52ho68.cloudfront.net/YTo2OntzOjI6ImlkIjtpOjEzOTI3OTM7czoxOiJ3IjtpOjUwMDtzOjE6ImgiO2k6OTk5OTtzOjE6ImMiO2k6MDtzOjE6InMiO2k6MDtzOjE6ImsiO3M6NDA6IjU5YjA2YjgyZjkyY2IxZjBiMDZjZmI5MmE3NTk5NjMzMjIyMmU4NGMiO30=",
            image,
        )
    }

    @Test
    fun geekpark() = runBlocking {
        val feed = geekpark.use { feedParser.parseFeedResponse(it) }!!

        assertEquals("极客公园（！）", feed.title)

        assertEquals(30, feed.items?.size)
    }

    @Test
    fun contentTypeHtmlIsNotUnescapedTwice() = runBlocking {
        val feed = contentTypeHtml.use { feedParser.parseFeedResponse(it) }!!

        val item = feed.items!!.single()

        assertFalse(
            item.content_html!!.contains(" <pre><code class=\"language-R\">obs.lon <- ncvar_get(nc.obs, 'lon')"),
        )

        assertTrue(
            item.content_html!!.contains(" <pre><code class=\"language-R\">obs.lon &lt;- ncvar_get(nc.obs, 'lon')"),
        )
    }

    @Test
    fun escapedRssDescriptionIsProperlyUnescaped() = runBlocking {
        val feed = feedParser.parseFeedResponse(
            URL("http://cowboyprogrammer.org"),
            rssWithHtmlEscapedDescription,
            null,
        )

        val item = feed.items!!.single()

        assertEquals(
            "http://cowboyprogrammer.org/hello.jpg&cached=true",
            item.image,
        )
        assertEquals(
            "<img src=\"hello.jpg&amp;cached=true\">",
            item.content_html,
        )
    }

    @Test
    fun escapedAtomContentIsProperlyUnescaped() = runBlocking {
        val feed = feedParser.parseFeedResponse(
            URL("http://cowboyprogrammer.org"),
            atomWithHtmlEscapedContents,
            null,
        )

        val text = feed.items!!.first()
        assertEquals(
            "http://cowboyprogrammer.org/hello.jpg&cached=true",
            text.image,
        )
        assertEquals(
            "<img src=\"hello.jpg&amp;cached=true\">",
            text.content_html,
        )

        val html = feed.items!![1]
        assertEquals(
            "http://cowboyprogrammer.org/hello.jpg&cached=true",
            html.image,
        )
        assertEquals(
            "<img src=\"hello.jpg&amp;cached=true\">",
            html.content_html,
        )

        val xhtml = feed.items!![2]
        assertEquals(
            "http://cowboyprogrammer.org/hello.jpg&cached=true",
            xhtml.image,
        )
        assertTrue("Actual:\n${xhtml.content_html}") {
            "<img src=\"hello.jpg&amp;cached=true\" />" in xhtml.content_html!!
        }
    }

    @Test
    fun handlesUnknownProtocols() = runBlocking {
        val feed = feedParser.parseFeedResponse(
            URL("https://gemini.circumlunar.space"),
            atomWithUnknownProtocol,
            null,
        )

        assertEquals(8, feed.items!!.size)
    }

    private fun <T> readResource(asdf: String, block: suspend (String) -> T): T {
        val body = javaClass.getResourceAsStream(asdf)!!
            .bufferedReader()
            .use(BufferedReader::readText)

        return runBlocking {
            block(body)
        }
    }

    private fun getCowboyHtml(): String =
        javaClass.getResourceAsStream("cowboyprogrammer.html")!!
            .bufferedReader()
            .use {
                it.readText()
            }

    private val emptySlashComment: Response
        get() = bytesToResponse(
            "empty_slash_comment.xml",
            "https://rss.golem.de/rss.php?feed=RSS2.0",
        )

    private val golemDe: Response
        get() = bytesToResponse("golem-de.xml", "https://rss.golem.de/rss.php?feed=RSS2.0")

    private val golemDe2: Response
        get() = bytesToResponse("rss_golem_2.xml", "https://rss.golem.de/rss.php?feed=RSS2.0")

    private val utdelningsSeglarenAtom: Response
        get() = bytesToResponse(
            "atom_utdelningsseglaren.xml",
            "http://utdelningsseglaren.blogspot.com/feeds/posts/default",
        )

    private val lineageosRss: Response
        get() = bytesToResponse("rss_lineageos.xml", "https://lineageos.org/feed.xml")

    private val cornucopiaAtom: Response
        get() = bytesToResponse(
            "atom_cornucopia.xml",
            "https://cornucopia.cornubot.se/feeds/posts/default",
        )

    private val cornucopiaRss: Response
        get() = bytesToResponse(
            "rss_cornucopia.xml",
            "https://cornucopia.cornubot.se/feeds/posts/default?alt=rss",
        )

    private val cowboyRss: Response
        get() = bytesToResponse("rss_cowboy.xml", "http://cowboyprogrammer.org/index.xml")

    private val cowboyAtom: Response
        get() = bytesToResponse("atom_cowboy.xml", "http://cowboyprogrammer.org/atom.xml")

    private val cyklistBloggen: Response
        get() = bytesToResponse("rss_cyklistbloggen.xml", "http://www.cyklistbloggen.se/feed/")

    private val researchRsc: Response
        get() = bytesToResponse("atom_research_rsc.xml", "http://research.swtch.com/feed.atom")

    private val morningPaper: Response
        get() = bytesToResponse("rss_morningpaper.xml", "https://blog.acolyer.org/feed/")

    private val fz: Response
        get() = bytesToResponse("rss_fz.xml", "https://www.fz.se/feeds/nyheter")

    private val londoner: Response
        get() = bytesToResponse("rss_londoner.xml", "http://londonist.com/feed")

    private val anon: Response
        get() = bytesToResponse("rss_anon.xml", "http://ANON.com/rss")

    private val nixosRss: Response
        get() = bytesToResponse("rss_nixos.xml", "http://nixos.org/news-rss.xml")

    private val nixersRss: Response
        get() = bytesToResponse(
            "rss_nixers_newsletter.xml",
            "https://newsletter.nixers.net/feed.xml",
        )

    private val videoResponse: Response
        get() = bytesToResponse(
            "rss_nixers_newsletter.xml",
            "https://foo.bar/video.mp4",
            "video/mp4",
        )

    private val diskuse: Response
        get() = bytesToResponse(
            "rss_diskuse.xml",
            "https://diskuse.jakpsatweb.cz/rss2.php?topic=173233",
        )

    private val geekpark: Response
        get() = bytesToResponse("rss_geekpark.xml", "http://main_test.geekpark.net/rss.rss")

    private val contentTypeHtml: Response
        get() = bytesToResponse(
            "atom_content_type_html.xml",
            "http://www.zoocoop.com/contentoob/o1.atom",
        )

    private fun bytesToResponse(
        resourceName: String,
        url: String,
        contentType: String = "application/xml",
    ): Response {
        val responseBody: ResponseBody =
            javaClass.getResourceAsStream(resourceName)!!
                .use { it.readBytes() }
                .toResponseBody(contentType.toMediaTypeOrNull())

        return Response.Builder()
            .body(responseBody)
            .protocol(Protocol.HTTP_2)
            .code(200)
            .message("Test")
            .request(
                Request.Builder()
                    .url(url)
                    .build(),
            )
            .build()
    }
}

@Language("xml")
const val atomRelative = """
<?xml version='1.0' encoding='UTF-8'?>
<feed xmlns='http://www.w3.org/2005/Atom' xml:base='http://cowboyprogrammer.org'>
  <id>http://cowboyprogrammer.org</id>
  <title>Relative links</title>
  <updated>2003-12-13T18:30:02Z</updated>
  <link rel="self" href="/feed.atom"/>
</feed>
"""

@Language("xml")
const val atomRelativeNoBase = """
<?xml version='1.0' encoding='UTF-8'?>
<feed xmlns='http://www.w3.org/2005/Atom'>
  <id>http://cowboyprogrammer.org</id>
  <title>Relative links</title>
  <updated>2003-12-13T18:30:02Z</updated>
  <link rel="self" href="/feed.atom"/>
</feed>
"""

@Language("xml")
const val atomWithAlternateLinks = """
<?xml version='1.0' encoding='UTF-8'?>
<feed xmlns='http://www.w3.org/2005/Atom'>
  <id>http://cowboyprogrammer.org</id>
  <title>Relative links</title>
  <updated>2003-12-13T18:30:02Z</updated>
  <link rel="self" href="/feed.atom"/>
  <link rel="alternate" type="text/html" href="http://localhost:1313/" />
  <link rel="alternate" type="application/rss" href="http://localhost:1313/index.xml" />
  <link rel="alternate" type="application/json" href="http://localhost:1313/feed.json" />
</feed>
"""

@Language("xml")
const val atomWithHtmlEscapedContents = """
<?xml version='1.0' encoding='UTF-8'?>
<feed xmlns='http://www.w3.org/2005/Atom'>
  <id>http://cowboyprogrammer.org</id>
  <title>escaping test</title>
  <updated>2003-12-13T18:30:02Z</updated>
  <link rel="self" href="/feed.atom"/>
  <entry>
    <id>http://cowboyprogrammer.org/1</id>
    <title>text</title>
    <link href="http://cowboyprogrammer.org/1"/>
    <updated>2020-10-12T21:26:03Z</updated>
    <content type="text">&lt;img src=&quot;hello.jpg&amp;amp;cached=true&quot;&gt;</content>
  </entry>
  <entry>
    <id>http://cowboyprogrammer.org/2</id>
    <title>html</title>
    <link href="http://cowboyprogrammer.org/2"/>
    <updated>2020-10-12T21:26:03Z</updated>
    <content type="html">&lt;img src=&quot;hello.jpg&amp;amp;cached=true&quot;&gt;</content>
  </entry>
  <entry>
    <id>http://cowboyprogrammer.org/3</id>
    <title>xhtml</title>
    <link href="http://cowboyprogrammer.org/3"/>
    <updated>2020-10-12T21:26:03Z</updated>
    <content type="xhtml">
       <div xmlns="http://www.w3.org/1999/xhtml">
         <img src="hello.jpg&amp;cached=true"/>
      </div>
    </content>
  </entry>
</feed>
"""

@Language("xml")
const val rssWithHtmlEscapedDescription = """
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<rss version="2.0">
  <channel>
    <title>Cowboy Programmer</title>
    <link>https://cowboyprogrammer.org/</link>
    <description>Recent content in Cowboy Programmer on Cowboy Programmer</description>
    <language>en-us</language>
    <lastBuildDate>Sun, 01 Sep 2019 23:21:00 +0200</lastBuildDate>
    
    <item>
      <title>description</title>
      <link>http://cowboyprogrammer.org/4</link>
      <pubDate>Sun, 01 Sep 2019 23:21:00 +0200</pubDate>
      <author>jonas@cowboyprogrammer.org (Space Cowboy)</author>
      <guid>http://cowboyprogrammer.org/4</guid>
      <description>&lt;img src=&quot;hello.jpg&amp;amp;cached=true&quot;&gt;</description>
    </item>
  </channel>
</rss>
"""

@Language("xml")
const val atomWithUnknownProtocol = """
<?xml version='1.0' encoding='UTF-8'?>
<feed xmlns="http://www.w3.org/2005/Atom">
<id>gemini://gemini.circumlunar.space/news/</id>
<title>Official Project Gemini news feed</title>
<updated>2023-03-01T18:34:36.714818+00:00</updated>
<author>
<name>Solderpunk</name>
<email>solderpunk@posteo.net</email>
</author>
<link href="gemini://gemini.circumlunar.space/news/atom.xml" rel="self"/>
<link href="gemini://gemini.circumlunar.space/news/" rel="alternate"/>
<generator uri="https://lkiesow.github.io/python-feedgen" version="0.9.0">python-feedgen</generator>
<entry>
<id>gemini://gemini.circumlunar.space/news/2022_01_16.gmi</id>
<title>2022-01-16 - Mailing list downtime, official news feed</title>
<updated>2022-01-16T16:10:48.445244+00:00</updated>
<link href="gemini://gemini.circumlunar.space/news/2022_01_16.gmi" rel="alternate"/>
</entry>
<entry>
<id>gemini://gemini.circumlunar.space/news/2022_01_22.gmi</id>
<title>2022-01-22 - Mailing list archives, Atom feed for official news</title>
<updated>2022-01-22T18:44:56.495170+00:00</updated>
<link href="gemini://gemini.circumlunar.space/news/2022_01_22.gmi" rel="alternate"/>
</entry>
<entry>
<id>gemini://gemini.circumlunar.space/news/2022_01_30.gmi</id>
<title>2022-01-30 - Minor specification update (0.16.1)</title>
<updated>2022-01-30T14:38:04.738832+00:00</updated>
<link href="gemini://gemini.circumlunar.space/news/2022_01_30.gmi" rel="alternate"/>
</entry>
<entry>
<id>gemini://gemini.circumlunar.space/news/2022_06_20.gmi</id>
<title>2022-06-20 - Three years of Gemini!</title>
<updated>2022-06-20T17:48:06.479966+00:00</updated>
<link href="gemini://gemini.circumlunar.space/news/2022_06_20.gmi" rel="alternate"/>
</entry>
<entry>
<id>gemini://gemini.circumlunar.space/news/2023_01_08.gmi</id>
<title>2023-01-08 - Changing DNS server</title>
<updated>2023-01-08T14:12:22.568187+00:00</updated>
<link href="gemini://gemini.circumlunar.space/news/2023_01_08.gmi" rel="alternate"/>
</entry>
<entry>
<id>gemini://gemini.circumlunar.space/news/2023_01_14.gmi</id>
<title>2023-01-14 - Tidying up gemini.circumlunar.space user capsules</title>
<updated>2023-01-14T15:58:26.933828+00:00</updated>
<link href="gemini://gemini.circumlunar.space/news/2023_01_14.gmi" rel="alternate"/>
</entry>
<entry>
<id>gemini://gemini.circumlunar.space/news/2023_02_14.gmi</id>
<title>2023-02-14 - Empty user capsules removed</title>
<updated>2023-02-14T08:45:03.559039+00:00</updated>
<link href="gemini://gemini.circumlunar.space/news/2023_02_14.gmi" rel="alternate"/>
</entry>
<entry>
<id>gemini://gemini.circumlunar.space/news/2023_03_01.gmi</id>
<title>2023-03-01 - Molly Brown upgrade</title>
<updated>2023-03-01T18:34:36.714818+00:00</updated>
<link href="gemini://gemini.circumlunar.space/news/2023_03_01.gmi" rel="alternate"/>
</entry>
</feed>
"""
