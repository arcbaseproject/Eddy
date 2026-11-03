package app.eddy.browser

import app.eddy.browser.data.database.SiteSettings
import app.eddy.browser.data.database.SiteSettingsDao
import app.eddy.browser.privacy.SiteFeature
import app.eddy.browser.privacy.SitePermissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SitePermissionsTest {
    @Test fun privateResetPreservesStoredPermissions() = runBlocking {
        val host = "https://example.com"
        val rows = mutableMapOf(host to SiteSettings(host, camera = SiteSettings.BLOCK))
        val dao = object : SiteSettingsDao {
            override fun all() = MutableStateFlow(rows.values.toList())
            override suspend fun get(host: String) = rows[host]
            override suspend fun upsert(s: SiteSettings) { rows[s.host] = s }
            override suspend fun delete(host: String) { rows.remove(host) }
            override suspend fun clear() { rows.clear() }
        }
        val sites = SitePermissions(dao, CoroutineScope(Dispatchers.Unconfined))
        sites.set(host, SiteFeature.MICROPHONE, SiteSettings.BLOCK, incognito = true)
        assertEquals(SiteSettings.BLOCK, sites.peek(host, true)?.microphone)
        sites.reset(host, incognito = true)
        assertNull(sites.peek(host, true)?.camera)
        assertNull(sites.peek(host, true)?.microphone)
        assertEquals(SiteSettings.BLOCK, sites.peek(host)?.camera)
        assertEquals(SiteSettings.BLOCK, rows[host]?.camera)
        sites.clearPrivate()
        assertEquals(SiteSettings.BLOCK, sites.peek(host, true)?.camera)
    }
}
