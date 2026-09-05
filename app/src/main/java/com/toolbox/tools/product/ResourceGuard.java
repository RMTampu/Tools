package com.toolbox.tools.product;

import com.toolbox.tools.authoring.AuthoringSection;
import com.toolbox.tools.core.StableId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ResourceGuard {
    public enum Pressure { NORMAL, REDUCED, CRITICAL }

    public static final class ScreenBudget {
        private final long maxPssBytes;
        private final int maxViews;
        private final int maxHeavyAssets;
        ScreenBudget(long pss,int views,int heavy){maxPssBytes=pss;maxViews=views;maxHeavyAssets=heavy;}
        public long maxPssBytes(){return maxPssBytes;} public int maxViews(){return maxViews;} public int maxHeavyAssets(){return maxHeavyAssets;}
    }

    private AuthoringSection heavyActive=AuthoringSection.UI;
    private long memoryBudgetBytes=192L*1024L*1024L;
    private int activeScreenCount=1;
    private String activeScreenId="screen.home";
    private final Map<String,ScreenBudget> screenBudgets=new LinkedHashMap<>();
    private final Map<String,List<Long>> pssSamples=new LinkedHashMap<>();

    public ResourceGuard(){configureScreen("screen.home",96L*1024L*1024L,180,4);configureScreen("screen.detail",96L*1024L*1024L,180,4);}

    public synchronized void activate(AuthoringSection section){heavyActive=Objects.requireNonNull(section,"section");activeScreenCount=1;}
    public synchronized AuthoringSection heavyActive(){return heavyActive;}
    public synchronized void setMemoryBudgetBytes(long value){if(value<32L*1024L*1024L)throw new IllegalArgumentException("budget terlalu kecil");memoryBudgetBytes=value;}
    public synchronized long memoryBudgetBytes(){return memoryBudgetBytes;}

    public synchronized void configureScreen(String screenId,long maxPssBytes,int maxViews,int maxHeavyAssets){
        String id=StableId.require(screenId,"screenId");if(maxPssBytes<16L*1024L*1024L||maxViews<1||maxHeavyAssets<0)throw new IllegalArgumentException("screen budget invalid");
        screenBudgets.put(id,new ScreenBudget(maxPssBytes,maxViews,maxHeavyAssets));
    }

    public synchronized void enterScreen(String screenId){String id=StableId.require(screenId,"screenId");if(!screenBudgets.containsKey(id))configureScreen(id,96L*1024L*1024L,180,4);activeScreenId=id;activeScreenCount=1;}
    public synchronized void releaseScreen(String screenId){String id=StableId.require(screenId,"screenId");if(id.equals(activeScreenId)){activeScreenId=null;activeScreenCount=0;}}
    public synchronized String activeScreenId(){return activeScreenId;}
    public synchronized int activeScreenCount(){return activeScreenCount;}

    public synchronized Pressure sample(String screenId,long pssBytes,int viewCount,int heavyAssets){
        String id=StableId.require(screenId,"screenId");ScreenBudget b=screenBudgets.get(id);if(b==null)throw new IllegalArgumentException("screen budget unavailable");
        List<Long> s=pssSamples.computeIfAbsent(id,k->new ArrayList<>());s.add(pssBytes);while(s.size()>32)s.remove(0);
        if(pssBytes>b.maxPssBytes||viewCount>b.maxViews||heavyAssets>b.maxHeavyAssets||pssBytes>memoryBudgetBytes)return Pressure.CRITICAL;
        if(pssBytes>b.maxPssBytes*8/10||viewCount>b.maxViews*8/10)return Pressure.REDUCED;
        return Pressure.NORMAL;
    }

    public synchronized boolean leakTrend(String screenId){
        List<Long> s=pssSamples.get(screenId);if(s==null||s.size()<4)return false;
        long first=s.get(0),last=s.get(s.size()-1);return last-first>8L*1024L*1024L;
    }

    public synchronized Map<String,ScreenBudget> budgets(){return Collections.unmodifiableMap(new LinkedHashMap<>(screenBudgets));}
    public synchronized boolean invariantPass(){return activeScreenCount<=1&&heavyActive!=null&&!screenBudgets.isEmpty();}
}
