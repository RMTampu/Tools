package com.toolbox.tools.android;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public final class ToolBoxBackgroundJobService extends JobService {
    @Override public boolean onStartJob(JobParameters params){
        final String taskId=params.getExtras().getString(AndroidBackgroundTaskScheduler.EXTRA_TASK_ID,"");
        new Thread(()->{
            String result=execute(taskId);
            getSharedPreferences(AndroidBackgroundTaskScheduler.PREFS,Context.MODE_PRIVATE).edit()
                    .putString(AndroidBackgroundTaskScheduler.KEY_LAST_TASK,taskId)
                    .putString(AndroidBackgroundTaskScheduler.KEY_LAST_RESULT,result)
                    .putLong("last_finished_at",System.currentTimeMillis()).apply();
            jobFinished(params,false);
        },"toolbox-background-job").start();
        return true;
    }
    @Override public boolean onStopJob(JobParameters params){return true;}
    private String execute(String taskId){
        if("task.project.index.refresh".equals(taskId)){
            try{
                File marker=new File(getFilesDir(),"background/project-index-refresh.marker");
                File parent=marker.getParentFile();
                if(parent!=null&&!parent.isDirectory()&&!parent.mkdirs())return "FAILED_STORAGE";
                try(FileOutputStream out=new FileOutputStream(marker,false)){
                    out.write(("completedAt="+System.currentTimeMillis()+"\n").getBytes(StandardCharsets.UTF_8));
                    out.flush();out.getFD().sync();
                }
                return "SUCCESS";
            }catch(Exception error){return "FAILED_IO";}
        }
        return "SUCCESS";
    }
}
