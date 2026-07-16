package com.example.flymestatusbarsizer;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

public final class F2fsGcJobService extends JobService {
    private static final int JOB_ID = 22001;

    static void syncSchedule(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null) {
            return;
        }
        boolean enabled = SettingsStore.readBoolean(
                SettingsStore.prefs(context),
                SettingsStore.KEY_F2FS_GC_ENABLED,
                SettingsStore.DEFAULT_F2FS_GC_ENABLED);
        if (!enabled) {
            scheduler.cancel(JOB_ID);
            return;
        }
        JobInfo job = new JobInfo.Builder(JOB_ID,
                new ComponentName(context, F2fsGcJobService.class))
                .setRequiresCharging(true)
                .setRequiresDeviceIdle(true)
                .setPeriodic(24L * 60L * 60L * 1000L)
                .setPersisted(true)
                .build();
        scheduler.schedule(job);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        F2fsGcManager.run(this, false, () -> jobFinished(params, false));
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
