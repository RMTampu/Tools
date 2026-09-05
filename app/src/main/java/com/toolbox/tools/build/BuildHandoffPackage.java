package com.toolbox.tools.build;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BuildHandoffPackage {
    private final String buildId;
    private final long projectRevision;
    private final String manifestFile;
    private final String irFile;
    private final String projectFile;
    private final Map<String, String> assetFiles;
    private final String packageContentSha256;

    BuildHandoffPackage(
            String buildId,
            long projectRevision,
            String manifestFile,
            String irFile,
            String projectFile,
            Map<String, String> assetFiles,
            String packageContentSha256
    ) {
        this.buildId = buildId;
        this.projectRevision = projectRevision;
        this.manifestFile = manifestFile;
        this.irFile = irFile;
        this.projectFile = projectFile;
        this.assetFiles = Collections.unmodifiableMap(
                new LinkedHashMap<>(assetFiles)
        );
        this.packageContentSha256 = packageContentSha256;
    }

    public String buildId() { return buildId; }
    public long projectRevision() { return projectRevision; }
    public String manifestFile() { return manifestFile; }
    public String irFile() { return irFile; }
    public String projectFile() { return projectFile; }
    public Map<String, String> assetFiles() { return assetFiles; }
    public String packageContentSha256() {
        return packageContentSha256;
    }

    public int fileCount() {
        return 3 + assetFiles.size();
    }
}
