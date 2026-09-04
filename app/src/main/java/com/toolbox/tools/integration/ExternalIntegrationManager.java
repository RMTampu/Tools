package com.toolbox.tools.integration;
import java.util.*;
public final class ExternalIntegrationManager{private final ExternalAdapterDescriptor adapter=new ExternalAdapterDescriptor("adapter.demo","Sumber Demo",1,EnumSet.allOf(ExternalCapability.class));private final ExternalNormalizer normalizer=new ExternalNormalizer();private final DeterministicExporter exporter=new DeterministicExporter();private final SyncEngine sync=new SyncEngine();
public ExternalAdapterDescriptor adapter(){return adapter;}public NormalizationResult importSnapshot(ExternalSnapshot s){if(!adapter.supports(ExternalCapability.IMPORT))throw new IllegalStateException("IMPORT_UNAVAILABLE");return normalizer.normalize(adapter.adapterId(),s);}
public ExportPackage export(List<NormalizedRecord> r){if(!adapter.supports(ExternalCapability.EXPORT))throw new IllegalStateException("EXPORT_UNAVAILABLE");return exporter.export(adapter.adapterId(),adapter.schemaVersion(),r);}
public SyncPlan planSync(ExternalSnapshot s){if(!adapter.supports(ExternalCapability.SYNC))throw new IllegalStateException("SYNC_UNAVAILABLE");return sync.plan(s,normalizer.normalize(adapter.adapterId(),s));}
public void applySync(SyncPlan p){sync.apply(p);}public SyncEngine sync(){return sync;}
public ExternalSnapshot demoSnapshot(long revision,String cursor){return new ExternalSnapshot(cursor,revision,Collections.singletonList(new ExternalRawRecord("alpha",Collections.singletonMap("title","Contoh"))));}}
