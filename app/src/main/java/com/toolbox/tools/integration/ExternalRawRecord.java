package com.toolbox.tools.integration;
import java.util.Collections; import java.util.LinkedHashMap; import java.util.Map; import java.util.Objects;
public final class ExternalRawRecord {
 public static final int MAX_FIELDS=64; public static final int MAX_VALUE_LENGTH=4096;
 private final String externalId; private final Map<String,String> fields;
 public ExternalRawRecord(String externalId,Map<String,String> fields){
  this.externalId=requireExternalId(externalId);
  if(fields==null||fields.size()>MAX_FIELDS) throw new IllegalArgumentException("external fields invalid");
  LinkedHashMap<String,String> c=new LinkedHashMap<>();
  for(Map.Entry<String,String> e:fields.entrySet()){
   String k=requireField(e.getKey()); String v=Objects.requireNonNull(e.getValue(),"value");
   if(v.length()>MAX_VALUE_LENGTH) throw new IllegalArgumentException("external value too long");
   c.put(k,v);
  }
  this.fields=Collections.unmodifiableMap(c);
 }
 private static String requireExternalId(String s){ s=Objects.requireNonNull(s,"externalId").trim(); if(s.isEmpty()||s.length()>128) throw new IllegalArgumentException("external id invalid"); return s;}
 private static String requireField(String s){ s=Objects.requireNonNull(s,"field").trim(); if(!s.matches("[A-Za-z0-9._-]{1,64}")) throw new IllegalArgumentException("external field invalid"); return s;}
 public String externalId(){return externalId;} public Map<String,String> fields(){return fields;}
}
