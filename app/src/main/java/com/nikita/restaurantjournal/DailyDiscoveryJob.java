package com.nikita.restaurantjournal;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import com.google.android.gms.tasks.CancellationTokenSource;
import java.util.concurrent.TimeUnit;

/** Daily searches use the user's saved area, never background location access. */
public class DailyDiscoveryJob extends JobService {
    private static final int JOB_ID = 1201;
    private CancellationTokenSource cancellation;
    private boolean stopped;
    private int runSequence;

    static void ensureScheduled(Context context) {
        JobScheduler scheduler=(JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (!DiscoverySettings.configured(context) || !DiscoverySettings.prefs(context).getBoolean("daily",true)) scheduler.cancel(JOB_ID);
        else if (scheduler.getPendingJob(JOB_ID)==null) schedule(context);
    }

    static void schedule(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        scheduler.cancel(JOB_ID);
        if (!DiscoverySettings.configured(context) || !DiscoverySettings.prefs(context).getBoolean("daily", true)) return;
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(context, DailyDiscoveryJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(TimeUnit.DAYS.toMillis(1), TimeUnit.HOURS.toMillis(6))
                .setPersisted(true).build();
        if (scheduler.schedule(job) == JobScheduler.RESULT_FAILURE)
            DiscoverySettings.prefs(context).edit().putString("last_error", "Android couldn't schedule daily updates. Reopen settings to try again.").apply();
    }

    @Override public boolean onStartJob(JobParameters params) {
        if (!DiscoverySettings.configured(this) || !DiscoverySettings.prefs(this).getBoolean("daily", true)) return false;
        stopped = false;
        int sequence = ++runSequence;
        cancellation = new CancellationTokenSource();
        String revision = DiscoverySettings.prefs(this).getString("revision", "");
        try {
            DiscoveryClient.search(this, true, cancellation.getToken())
                    .addOnSuccessListener(response -> {
                        if (stopped || sequence != runSequence) return;
                        DiscoveryClient.record(this, response.getPlaces(), revision);
                        jobFinished(params, false);
                    })
                    .addOnFailureListener(error -> {
                        if (stopped || sequence != runSequence) return;
                        SharedPreferences p = DiscoverySettings.prefs(this);
                        if (revision.equals(p.getString("revision", ""))) p.edit().putString("last_error", DiscoveryClient.error(error)).apply();
                        // Wait until the next daily cycle rather than repeat billed calls.
                        jobFinished(params, false);
                    });
            return true;
        } catch (Exception error) {
            DiscoverySettings.prefs(this).edit().putString("last_error", DiscoveryClient.error(error)).apply();
            return false;
        }
    }

    @Override public boolean onStopJob(JobParameters params) {
        stopped = true;
        runSequence++;
        if (cancellation != null) cancellation.cancel();
        return false;
    }
}
