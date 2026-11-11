package app.eddy.browser

import app.eddy.browser.data.database.SiteSettings
import app.eddy.browser.data.database.SiteSettingsDao
import app.eddy.browser.privacy.SiteFeature
import app.eddy.browser.privacy.SitePermissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SitePermissionsTest {
    @Test fun unrelatedSiteChangeDoesNotPreventInitialCacheReconciliation() = runBlocking {
        val dao = DelayedDao()
        val initial = CompletableDeferred<List<SiteSettings>>()
        dao.initial = initial
        dao.rows[HOST] = SiteSettings(HOST, camera = SiteSettings.ALLOW)
        val sites = SitePermissions(dao, CoroutineScope(coroutineContext + Dispatchers.Unconfined))
        sites.set(HOST, SiteFeature.MICROPHONE, SiteSettings.BLOCK)
        sites.set("https://other.example", SiteFeature.POPUPS, SiteSettings.BLOCK)
        initial.complete(dao.rows.values.toList())
        assertEquals(SiteSettings.ALLOW, sites.peek(HOST)?.camera)
        assertEquals(SiteSettings.BLOCK, sites.peek(HOST)?.microphone)
    }

    @Test fun olderWriteDoesNotUndoNewerBlock() = runBlocking {
        val dao = DelayedDao()
        val sites = SitePermissions(dao, CoroutineScope(coroutineContext + Dispatchers.Unconfined))
        val first = CompletableDeferred<Unit>()
        val second = CompletableDeferred<Unit>()
        dao.upsertGates.addAll(listOf(first, second))
        sites.set(HOST, SiteFeature.CAMERA, SiteSettings.ALLOW)
        sites.set(HOST, SiteFeature.CAMERA, SiteSettings.BLOCK)
        first.complete(Unit)
        assertEquals(SiteSettings.BLOCK, sites.peek(HOST)?.camera)
        second.complete(Unit)
        assertEquals(SiteSettings.BLOCK, dao.rows[HOST]?.camera)
    }

    @Test fun olderResetDoesNotEraseNewerChoice() = runBlocking {
        for (all in listOf(false, true)) {
            val dao = DelayedDao()
            val sites = SitePermissions(dao, CoroutineScope(coroutineContext + Dispatchers.Unconfined))
            val reset = CompletableDeferred<Unit>()
            val write = CompletableDeferred<Unit>()
            dao.resetGate = reset
            dao.upsertGates.add(write)
            if (all) sites.resetAll() else sites.reset(HOST)
            sites.set(HOST, SiteFeature.CAMERA, SiteSettings.BLOCK)
            reset.complete(Unit)
            assertEquals(SiteSettings.BLOCK, sites.peek(HOST)?.camera)
            write.complete(Unit)
            assertEquals(SiteSettings.BLOCK, dao.rows[HOST]?.camera)
        }
    }

    private class DelayedDao : SiteSettingsDao {
        val rows = mutableMapOf<String, SiteSettings>()
        val upsertGates = ArrayDeque<CompletableDeferred<Unit>>()
        var resetGate: CompletableDeferred<Unit>? = null
        var initial: CompletableDeferred<List<SiteSettings>>? = null
        override fun all() = flow { emit(initial?.await() ?: rows.values.toList()) }
        override suspend fun get(host: String) = rows[host]
        override suspend fun upsert(s: SiteSettings) {
            upsertGates.removeFirstOrNull()?.await()
            rows[s.host] = s
        }
        override suspend fun delete(host: String) { resetGate?.await(); rows.remove(host) }
        override suspend fun clear() { resetGate?.await(); rows.clear() }
    }

    private companion object { const val HOST = "https://example.com" }

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
