package com.toolbox.tools.integration;
import com.toolbox.tools.core.StableId;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.Objects;
public final class ExternalAdapterDescriptor {
 public static final int MAX_SCHEMA_VERSION=64;
 private final String adapterId; private final String labelIndonesia; private final int schemaVersion; private final Set<ExternalCapability> capabilities;
 public ExternalAdapterDescriptor(String adapterId,String labelIndonesia,int schemaVersion,Set<ExternalCapability> capabilities){
  this.adapterId=StableId.require(adapterId,"adapterId");
  String l=Objects.requireNonNull(labelIndonesia,"labelIndonesia").trim();
  if(l.isEmpty()||l.length()>120) throw new IllegalArgumentException("adapter label invalid");
  if(schemaVersion<=0||schemaVersion>MAX_SCHEMA_VERSION) throw new IllegalArgumentException("adapter schema invalid");
  this.labelIndonesia=l; this.schemaVersion=schemaVersion;
  EnumSet<ExternalCapability> c=capabilities==null||capabilities.isEmpty()?EnumSet.noneOf(ExternalCapability.class):EnumSet.copyOf(capabilities);
  this.capabilities=Collections.unmodifiableSet(c);
 }
 public String adapterId(){return adapterId;} public String labelIndonesia(){return labelIndonesia;} public int schemaVersion(){return schemaVersion;}
 public boolean supports(ExternalCapability c){return capabilities.contains(c);} public Set<ExternalCapability> capabilities(){return capabilities;}
}
