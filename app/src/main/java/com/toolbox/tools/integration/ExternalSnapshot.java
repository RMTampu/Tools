package com.toolbox.tools.integration;
import java.util.ArrayList; import java.util.Collections; import java.util.List; import java.util.Objects;
public final class ExternalSnapshot {
 public static final int MAX_RECORDS=1000;
 private final String cursor; private final long revision; private final List<ExternalRawRecord> records;
 public ExternalSnapshot(String cursor,long revision,List<ExternalRawRecord> records){
  String c=Objects.requireNonNull(cursor,"cursor").trim(); if(c.isEmpty()||c.length()>128) throw new IllegalArgumentException("cursor invalid");
  if(revision<0) throw new IllegalArgumentException("revision invalid");
  if(records==null||records.size()>MAX_RECORDS) throw new IllegalArgumentException("record count invalid");
  this.cursor=c; this.revision=revision; this.records=Collections.unmodifiableList(new ArrayList<>(records));
 }
 public String cursor(){return cursor;} public long revision(){return revision;} public List<ExternalRawRecord> records(){return records;}
}
