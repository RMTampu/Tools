package com.toolbox.tools.integration;
import java.util.ArrayList;import java.util.Collections;import java.util.List;
public final class SyncPlan{private final SyncStatus status;private final String cursor;private final long revision;private final List<NormalizedRecord> records;
public SyncPlan(SyncStatus status,String cursor,long revision,List<NormalizedRecord> records){this.status=status;this.cursor=cursor;this.revision=revision;this.records=Collections.unmodifiableList(new ArrayList<>(records));}
public SyncStatus status(){return status;}public String cursor(){return cursor;}public long revision(){return revision;}public List<NormalizedRecord> records(){return records;}}
