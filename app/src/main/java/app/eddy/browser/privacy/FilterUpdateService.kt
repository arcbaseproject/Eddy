package app.eddy.browser.privacy

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import app.eddy.browser.EddyApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Refreshes downloaded filter lists about once a day, only while online and not on low battery. */
class FilterUpdateService : JobService() {
    private var job: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val app = application as EddyApp
        job = app.container.scope.launch {
            val settings = app.container.settingsStore.settings.first()
            val failures = app.container.blocker.update(settings.filterLists)
            app.container.settingsStore.update { it.copy(filterListsUpdatedAt = System.currentTimeMillis()) }
            jobFinished(params, failures > 0)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        job?.cancel()
        return true
    }

    companion object {
        private const val JOB_ID = 4201

        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (scheduler.getPendingJob(JOB_ID) != null) return
            scheduler.schedule(
                JobInfo.Builder(JOB_ID, ComponentName(context, FilterUpdateService::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setRequiresBatteryNotLow(true)
                    .setPeriodic(TimeUnit.DAYS.toMillis(1))
                    .setPersisted(false)
                    .build(),
            )
        }
    }
}
