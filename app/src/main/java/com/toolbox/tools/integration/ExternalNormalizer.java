package com.toolbox.tools.integration;
import java.util.ArrayList; import java.util.LinkedHashMap; import java.util.List; import java.util.Map; import java.util.TreeMap;
public final class ExternalNormalizer {
 public NormalizationResult normalize(String adapterId,ExternalSnapshot snapshot){
  List<NormalizedRecord> out=new ArrayList<>(); List<String> diagnostics=new ArrayList<>(); Map<String,Boolean> seen=new LinkedHashMap<>();
  for(ExternalRawRecord raw:snapshot.records()){
   String stable=toStable(adapterId,raw.externalId());
   if(seen.put(stable,Boolean.TRUE)!=null){diagnostics.add("DUPLICATE_EXTERNAL_ID:"+raw.externalId());continue;}
   TreeMap<String,String> ordered=new TreeMap<>(raw.fields());
   out.add(new NormalizedRecord(stable,raw.externalId(),ordered));
  }
  out.sort((a,b)->a.stableId().compareTo(b.stableId()));
  return new NormalizationResult(out,diagnostics);
 }
 private static String toStable(String adapter,String external){
  String cleaned=external.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9._-]+","-");
  cleaned=cleaned.replaceAll("^-+|-+$","");
  if(cleaned.isEmpty()) cleaned="item";
  if(cleaned.length()>80) cleaned=cleaned.substring(0,80);
  return com.toolbox.tools.core.StableId.require(adapter+".item."+cleaned,"normalizedId");
 }
}
