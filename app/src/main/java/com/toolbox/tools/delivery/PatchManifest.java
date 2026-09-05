package com.toolbox.tools.delivery;

import com.toolbox.tools.core.StableId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class PatchManifest {
    public static final int CURRENT_SCHEMA_VERSION = 2;

    private final int schemaVersion;
    private final String patchId;
    private final String projectId;
    private final long baseRevision;
    private final long targetRevision;
    private final String parentSignedApkSha256;
    private final String targetCandidateSha256;
    private final String rollbackBaselineApkSha256;
    private final String payloadSha256;

    private final String packageType;
    private final String targetPackage;
    private final String packageVersion;
    private final int minHostVersionCode;
    private final int maxHostVersionCode;
    private final Set<String> dependencies;
    private final Set<String> requiredCapabilities;
    private final Map<String, String> fileHashes;
    private final String intent;

    private final String canonical;
    private final String contentSha256;

    /**
     * Legacy V1 constructor retained for rollback/package compatibility.
     */
    public PatchManifest(
            String patchId,
            String projectId,
            long baseRevision,
            long targetRevision,
            String parentSignedApkSha256,
            String targetCandidateSha256,
            String rollbackBaselineApkSha256,
            String payloadSha256
    ){
        this.schemaVersion = 1;
        this.patchId = StableId.require(patchId, "patchId");
        this.projectId = StableId.require(projectId, "projectId");
        requireRevisionChain(baseRevision, targetRevision);
        requireSha256(
                parentSignedApkSha256,
                "parentSignedApkSha256"
        );
        requireSha256(
                targetCandidateSha256,
                "targetCandidateSha256"
        );
        requireSha256(
                rollbackBaselineApkSha256,
                "rollbackBaselineApkSha256"
        );
        requireSha256(payloadSha256, "payloadSha256");

        this.baseRevision = baseRevision;
        this.targetRevision = targetRevision;
        this.parentSignedApkSha256 = parentSignedApkSha256;
        this.targetCandidateSha256 = targetCandidateSha256;
        this.rollbackBaselineApkSha256 = rollbackBaselineApkSha256;
        this.payloadSha256 = payloadSha256;

        this.packageType = "DECLARATIVE_PATCH";
        this.targetPackage = "com.toolbox.tools";
        this.packageVersion = "1";
        this.minHostVersionCode = 1;
        this.maxHostVersionCode = Integer.MAX_VALUE;
        this.dependencies = Collections.emptySet();
        this.requiredCapabilities = Collections.emptySet();
        this.fileHashes = Collections.singletonMap(
                "payload",
                payloadSha256
        );
        this.intent = "EVOLUTION";

        this.canonical = "TBX_PATCH_V1\n"
                + patchId + "\n"
                + projectId + "\n"
                + baseRevision + "\n"
                + targetRevision + "\n"
                + parentSignedApkSha256 + "\n"
                + targetCandidateSha256 + "\n"
                + rollbackBaselineApkSha256 + "\n"
                + payloadSha256 + "\n";
        this.contentSha256 = PatchPayload.sha256(canonical);
    }

    public PatchManifest(
            String patchId,
            String projectId,
            long baseRevision,
            long targetRevision,
            String parentSignedApkSha256,
            String targetCandidateSha256,
            String rollbackBaselineApkSha256,
            String payloadSha256,
            String packageType,
            String targetPackage,
            String packageVersion,
            int minHostVersionCode,
            int maxHostVersionCode,
            Set<String> dependencies,
            Set<String> requiredCapabilities,
            Map<String, String> fileHashes,
            String intent
    ) {
        this.schemaVersion = CURRENT_SCHEMA_VERSION;
        this.patchId = StableId.require(patchId, "patchId");
        this.projectId = StableId.require(projectId, "projectId");
        requireRevisionChain(baseRevision, targetRevision);
        requireSha256(
                parentSignedApkSha256,
                "parentSignedApkSha256"
        );
        requireSha256(
                targetCandidateSha256,
                "targetCandidateSha256"
        );
        requireSha256(
                rollbackBaselineApkSha256,
                "rollbackBaselineApkSha256"
        );
        requireSha256(payloadSha256, "payloadSha256");

        if (!"DECLARATIVE_PATCH".equals(packageType)) {
            throw new IllegalArgumentException(
                    "packageType unsupported"
            );
        }
        if (targetPackage == null
                || !targetPackage.matches(
                        "[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+"
                )) {
            throw new IllegalArgumentException(
                    "targetPackage invalid"
            );
        }
        if (packageVersion == null
                || !packageVersion.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException(
                    "packageVersion invalid"
            );
        }
        if (minHostVersionCode < 1
                || maxHostVersionCode < minHostVersionCode) {
            throw new IllegalArgumentException(
                    "host compatibility invalid"
            );
        }
        if (!"EVOLUTION".equals(intent)
                && !"REPAIR".equals(intent)
                && !"MIGRATION".equals(intent)) {
            throw new IllegalArgumentException(
                    "patch intent invalid"
            );
        }

        LinkedHashSet<String> normalizedDependencies =
                new LinkedHashSet<>();
        if (dependencies != null) {
            for (String dependency : new TreeSet<>(dependencies)) {
                normalizedDependencies.add(
                        StableId.require(
                                dependency,
                                "patchDependency"
                        )
                );
            }
        }
        if (normalizedDependencies.size() > 128) {
            throw new IllegalArgumentException(
                    "patch dependency budget exceeded"
            );
        }

        LinkedHashSet<String> normalizedCapabilities =
                new LinkedHashSet<>();
        Set<String> allowedCapabilities =
                new LinkedHashSet<>(
                        java.util.Arrays.asList(
                                "ui",
                                "logic",
                                "data",
                                "binding",
                                "asset"
                        )
                );
        if (requiredCapabilities != null) {
            for (String capability
                    : new TreeSet<>(requiredCapabilities)) {
                String value = capability == null
                        ? ""
                        : capability.trim()
                            .toLowerCase(
                                    java.util.Locale.ROOT
                            );
                if (!allowedCapabilities.contains(value)) {
                    throw new IllegalArgumentException(
                            "patch capability invalid:"
                                    + capability
                    );
                }
                normalizedCapabilities.add(value);
            }
        }

        LinkedHashMap<String, String> normalizedHashes =
                new LinkedHashMap<>();
        if (fileHashes != null) {
            for (Map.Entry<String, String> entry
                    : new TreeMap<>(fileHashes).entrySet()) {
                String path = entry.getKey();
                if (path == null
                        || !path.matches(
                                "[A-Za-z0-9._/-]{1,160}"
                        )
                        || path.startsWith("/")
                        || path.contains("..")
                        || path.contains("//")) {
                    throw new IllegalArgumentException(
                            "patch file path invalid"
                    );
                }
                requireSha256(
                        entry.getValue(),
                        "patch file hash"
                );
                normalizedHashes.put(
                        path,
                        entry.getValue()
                );
            }
        }
        if (!payloadSha256.equals(
                normalizedHashes.get("payload")
        )) {
            throw new IllegalArgumentException(
                    "payload file hash missing/mismatch"
            );
        }

        this.baseRevision = baseRevision;
        this.targetRevision = targetRevision;
        this.parentSignedApkSha256 = parentSignedApkSha256;
        this.targetCandidateSha256 = targetCandidateSha256;
        this.rollbackBaselineApkSha256 = rollbackBaselineApkSha256;
        this.payloadSha256 = payloadSha256;
        this.packageType = packageType;
        this.targetPackage = targetPackage;
        this.packageVersion = packageVersion;
        this.minHostVersionCode = minHostVersionCode;
        this.maxHostVersionCode = maxHostVersionCode;
        this.dependencies = Collections.unmodifiableSet(
                normalizedDependencies
        );
        this.requiredCapabilities = Collections.unmodifiableSet(
                normalizedCapabilities
        );
        this.fileHashes = Collections.unmodifiableMap(
                normalizedHashes
        );
        this.intent = intent;

        StringBuilder out = new StringBuilder();
        out.append("TBX_PATCH_V2\n")
                .append(patchId).append('\n')
                .append(projectId).append('\n')
                .append(baseRevision).append('\n')
                .append(targetRevision).append('\n')
                .append(parentSignedApkSha256).append('\n')
                .append(targetCandidateSha256).append('\n')
                .append(rollbackBaselineApkSha256).append('\n')
                .append(payloadSha256).append('\n')
                .append(packageType).append('\n')
                .append(targetPackage).append('\n')
                .append(packageVersion).append('\n')
                .append(minHostVersionCode).append('\n')
                .append(maxHostVersionCode).append('\n')
                .append(intent).append('\n');
        for (String dependency : this.dependencies) {
            out.append("dependency|")
                    .append(dependency)
                    .append('\n');
        }
        for (String capability : this.requiredCapabilities) {
            out.append("capability|")
                    .append(capability)
                    .append('\n');
        }
        for (Map.Entry<String, String> entry
                : this.fileHashes.entrySet()) {
            out.append("file|")
                    .append(entry.getKey())
                    .append('|')
                    .append(entry.getValue())
                    .append('\n');
        }
        this.canonical = out.toString();
        this.contentSha256 = PatchPayload.sha256(canonical);
    }

    public int schemaVersion(){return schemaVersion;}
    public String patchId(){return patchId;}
    public String projectId(){return projectId;}
    public long baseRevision(){return baseRevision;}
    public long targetRevision(){return targetRevision;}
    public String parentSignedApkSha256(){return parentSignedApkSha256;}
    public String targetCandidateSha256(){return targetCandidateSha256;}
    public String rollbackBaselineApkSha256(){return rollbackBaselineApkSha256;}
    public String payloadSha256(){return payloadSha256;}
    public String packageType(){return packageType;}
    public String targetPackage(){return targetPackage;}
    public String packageVersion(){return packageVersion;}
    public int minHostVersionCode(){return minHostVersionCode;}
    public int maxHostVersionCode(){return maxHostVersionCode;}
    public Set<String> dependencies(){return dependencies;}
    public Set<String> requiredCapabilities(){return requiredCapabilities;}
    public Map<String,String> fileHashes(){return fileHashes;}
    public String intent(){return intent;}
    public String canonical(){return canonical;}
    public String contentSha256(){return contentSha256;}

    public boolean supportsHost(
            String packageName,
            int versionCode,
            Set<String> capabilities
    ) {
        if (!targetPackage.equals(packageName)) return false;
        if (versionCode < minHostVersionCode
                || versionCode > maxHostVersionCode) {
            return false;
        }
        Set<String> supported = capabilities == null
                ? Collections.emptySet()
                : capabilities;
        return supported.containsAll(requiredCapabilities);
    }

    private static void requireRevisionChain(
            long baseRevision,
            long targetRevision
    ) {
        if (baseRevision <= 0
                || targetRevision != baseRevision + 1) {
            throw new IllegalArgumentException(
                    "patch revision chain invalid"
            );
        }
    }

    private static void requireSha256(
            String value,
            String label
    ){
        if (value == null
                || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    label + " invalid"
            );
        }
    }
}
