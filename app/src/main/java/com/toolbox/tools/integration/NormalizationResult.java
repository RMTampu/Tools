package com.toolbox.tools.integration;
import java.util.ArrayList; import java.util.Collections; import java.util.List;
public final class NormalizationResult {
 private final List<NormalizedRecord> records; private final List<String> diagnostics;
 public NormalizationResult(List<NormalizedRecord> records,List<String> diagnostics){this.records=Collections.unmodifiableList(new ArrayList<>(records));this.diagnostics=Collections.unmodifiableList(new ArrayList<>(diagnostics));}
 public boolean isPass(){return diagnostics.isEmpty();} public List<NormalizedRecord> records(){return records;} public List<String> diagnostics(){return diagnostics;}
}
