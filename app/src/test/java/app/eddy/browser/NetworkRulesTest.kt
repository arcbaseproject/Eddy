package app.eddy.browser

import app.eddy.browser.privacy.ContentBlocker
import app.eddy.browser.privacy.NetworkRule
import app.eddy.browser.privacy.NetworkRules
import org.junit.Assert.*
import org.junit.Test

class NetworkRulesTest {
    @Test fun blocksPathsWithoutBlockingWholeSite() {
        val rules = NetworkRules(listOf("||example.com/ads/*"))
        assertEquals(true, rules.decision("https://cdn.example.com/ads/banner.js", "cdn.example.com", "example.com", false))
        assertNull(rules.decision("https://example.com/news", "example.com", "example.com", false))
        assertNull(rules.decision("https://notexample.com/ads/banner.js", "notexample.com", "example.com", true))
    }

    @Test fun exceptionsOverrideBlocks() {
        val rules = NetworkRules(listOf("||example.com/ads/*", "@@||example.com/ads/player.js"))
        assertEquals(false, rules.decision("https://example.com/ads/player.js", "example.com", "news.com", true))
    }

    @Test fun respectsPageDomainsAndPartyRestrictions() {
        val rules = NetworkRules(listOf("||ads.com^\$third-party,domain=news.com|~paid.news.com"))
        assertEquals(true, rules.decision("https://ads.com/banner", "ads.com", "www.news.com", true))
        assertNull(rules.decision("https://ads.com/banner", "ads.com", "paid.news.com", true))
        assertNull(rules.decision("https://ads.com/banner", "ads.com", "other.com", true))
        assertNull(rules.decision("https://ads.com/banner", "ads.com", "news.com", false))
    }

    @Test fun separatorDoesNotMatchLongerHost() {
        val rules = NetworkRules(listOf("||ads.com^"))
        assertNull(rules.decision("https://ads.com.evil.org/banner", "ads.com.evil.org", "news.com", true))
        assertEquals(true, rules.decision("https://ads.com:443/banner", "ads.com", "news.com", true))
    }

    @Test fun genericPathsMatchFirstPartyResources() {
        val rules = NetworkRules(listOf("/pagead/*"))
        assertEquals(true, rules.decision("https://news.com/pagead/banner.js", "news.com", "news.com", false))
        assertNull(rules.decision("https://news.com/story", "news.com", "news.com", false))
    }

    @Test fun unsupportedSyntaxIsIgnoredRatherThanBroadened() {
        assertNull(NetworkRule.parse("||ads.com^\$script"))
        assertNull(NetworkRule.parse("||ads.com^\$redirect=noopjs"))
        assertNull(NetworkRule.parse("/advert[0-9]+/"))
        assertNull(ContentBlocker.parseLine("||example.com^\$domain=news.com"))
        assertNull(ContentBlocker.parseLine("||example.com^/ads/*"))
        assertNull(ContentBlocker.parseLine("||example.com^\$~third-party"))
    }
}
