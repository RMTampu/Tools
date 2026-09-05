package com.toolbox.tools.android;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class InstalledApplicationCatalog {
    public static final class Entry {
        private final String packageName;
        private final String label;
        private final boolean system;
        private final boolean enabled;
        Entry(String packageName,String label,boolean system,boolean enabled){
            this.packageName=packageName;this.label=label;this.system=system;this.enabled=enabled;
        }
        public String packageName(){return packageName;}
        public String label(){return label;}
        public boolean system(){return system;}
        public boolean enabled(){return enabled;}
    }
    private InstalledApplicationCatalog(){}
    public static List<Entry> list(Context context){
        if(context==null)throw new NullPointerException("context");
        PackageManager pm=context.getPackageManager();
        List<ApplicationInfo> raw=pm.getInstalledApplications(PackageManager.GET_META_DATA);
        ArrayList<Entry> out=new ArrayList<>();
        for(ApplicationInfo info:raw){
            if(info==null||info.packageName==null)continue;
            CharSequence appLabel=pm.getApplicationLabel(info);
            String label=appLabel==null?info.packageName:appLabel.toString().trim();
            if(label.isEmpty())label=info.packageName;
            out.add(new Entry(info.packageName,label,(info.flags&ApplicationInfo.FLAG_SYSTEM)!=0,info.enabled));
        }
        out.sort(Comparator.comparing((Entry e)->e.label().toLowerCase(Locale.ROOT)).thenComparing(Entry::packageName));
        return Collections.unmodifiableList(out);
    }
}
