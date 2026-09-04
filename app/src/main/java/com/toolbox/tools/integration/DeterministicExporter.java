package com.toolbox.tools.integration;
import java.nio.charset.StandardCharsets; import java.security.MessageDigest; import java.util.List;
public final class DeterministicExporter {
 public ExportPackage export(String adapterId,int schemaVersion,List<NormalizedRecord> records){
  if(schemaVersion<=0||schemaVersion>ExternalAdapterDescriptor.MAX_SCHEMA_VERSION) throw new IllegalArgumentException("export schema invalid");
  StringBuilder b=new StringBuilder("TBX_EXTERNAL_V1\n").append(adapterId).append('|').append(schemaVersion).append('\n');
  java.util.ArrayList<NormalizedRecord> copy=new java.util.ArrayList<>(records); copy.sort((a,c)->a.stableId().compareTo(c.stableId()));
  for(NormalizedRecord r:copy){ b.append(r.stableId()).append('|').append(escape(r.externalId())).append('|');
   boolean first=true; for(java.util.Map.Entry<String,String> e:new java.util.TreeMap<>(r.fields()).entrySet()){if(!first)b.append(';');first=false;b.append(escape(e.getKey())).append('=').append(escape(e.getValue()));} b.append('\n');}
  String payload=b.toString(); return new ExportPackage(payload,sha256(payload));
 }
 private static String escape(String s){return s.replace("%","%25").replace("|","%7C").replace(";","%3B").replace("=","%3D").replace("\n","%0A");}
 private static String sha256(String s){try{MessageDigest d=MessageDigest.getInstance("SHA-256");byte[] h=d.digest(s.getBytes(StandardCharsets.UTF_8));StringBuilder o=new StringBuilder();for(byte x:h)o.append(String.format(java.util.Locale.ROOT,"%02x",x));return o.toString();}catch(Exception e){throw new IllegalStateException(e);}}
}
