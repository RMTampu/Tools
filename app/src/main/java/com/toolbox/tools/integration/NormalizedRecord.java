package com.toolbox.tools.integration;
import com.toolbox.tools.core.StableId; import java.util.Collections; import java.util.LinkedHashMap; import java.util.Map;
public final class NormalizedRecord {
 private final String stableId; private final String externalId; private final Map<String,String> fields;
 public NormalizedRecord(String stableId,String externalId,Map<String,String> fields){
  this.stableId=StableId.require(stableId,"stableId"); this.externalId=externalId; this.fields=Collections.unmodifiableMap(new LinkedHashMap<>(fields));
 }
 public String stableId(){return stableId;} public String externalId(){return externalId;} public Map<String,String> fields(){return fields;}
}
