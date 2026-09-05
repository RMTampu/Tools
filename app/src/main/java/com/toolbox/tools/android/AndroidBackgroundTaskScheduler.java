package com.toolbox.tools.android;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.PersistableBundle;

import com.toolbox.tools.product.BackgroundTaskManager;

public final class AndroidBackgroundTaskScheduler {
    public static final String PREFS="toolbox.background.runtime";
    public static final String KEY_LAST_TASK="last_task";
    public static final String KEY_LAST_RESULT="last_result";
    public static final String EXTRA_TASK_ID="task_id";
    private AndroidBackgroundTaskScheduler(){}
    public static int schedule(Context context,BackgroundTaskManager.Task task,long minLatencyMs){
        if(context==null||task==null)throw new NullPointerException("context/task");
        PersistableBundle extras=new PersistableBundle();extras.putString(EXTRA_TASK_ID,task.id());
        JobInfo.Builder b=new JobInfo.Builder(stableJobId(task.id()),new ComponentName(context,ToolBoxBackgroundJobService.class));
        b.setExtras(extras);b.setMinimumLatency(Math.max(0L,minLatencyMs));b.setOverrideDeadline(Math.max(1000L,minLatencyMs+10000L));
        for(BackgroundTaskManager.Constraint c:task.spec().constraints()){
            switch(c){
                case NETWORK:b.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);break;
                case BATTERY_NOT_LOW:b.setRequiresBatteryNotLow(true);break;
                case CHARGING:b.setRequiresCharging(true);break;
                case STORAGE_NOT_LOW:b.setRequiresStorageNotLow(true);break;
                default:break;
            }
        }
        JobScheduler s=(JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(s==null)throw new IllegalStateException("JobScheduler tidak tersedia");
        int result=s.schedule(b.build());
        if(result!=JobScheduler.RESULT_SUCCESS)throw new IllegalStateException("background task gagal dijadwalkan");
        return stableJobId(task.id());
    }
    public static String lastCompletedTask(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY_LAST_TASK,"");}
    public static String lastResult(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY_LAST_RESULT,"");}
    private static int stableJobId(String id){return 0x54000000|(id.hashCode()&0x00ffffff);}
}
