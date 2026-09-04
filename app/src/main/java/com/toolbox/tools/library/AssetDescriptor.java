package com.toolbox.tools.library;

import com.toolbox.tools.core.StableId;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class AssetDescriptor {
    public static final long GLOBAL_MAX_ORIGINAL_BYTES = 8L * 1024L * 1024L;

    private final String assetId;
    private final VersionNumber version;
    private final AssetKind kind;
    private final String sha256;
    private final boolean required;
    private final String sourceName;
    private final String expectedOwnerId;
    private final String mimeType;
    private final long maxBytes;
    private final Set<String> consumerIds;
    private final CatalogLifecycle lifecycle;

    public AssetDescriptor(
            String assetId,
            VersionNumber version,
            AssetKind kind,
            String sha256,
            boolean required,
            String sourceName,
            String expectedOwnerId,
            String mimeType,
            long maxBytes,
            Set<String> consumerIds,
            CatalogLifecycle lifecycle
    ) {
        this.assetId = StableId.require(assetId, "assetId");
        this.version = Objects.requireNonNull(version, "version");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.sha256 = requireDigest(sha256);
        this.required = required;
        this.sourceName = requireSafeSourceName(sourceName);
        this.expectedOwnerId = StableId.require(expectedOwnerId, "expectedOwnerId");
        this.mimeType = requireMimeType(mimeType);
        if (maxBytes <= 0 || maxBytes > GLOBAL_MAX_ORIGINAL_BYTES) {
            throw new IllegalArgumentException("asset maxBytes outside allowed budget");
        }
        this.maxBytes = maxBytes;
        LinkedHashSet<String> consumers = new LinkedHashSet<>();
        if (consumerIds != null) {
            for (String id : consumerIds) {
                consumers.add(StableId.require(id, "consumerId"));
            }
        }
        this.consumerIds = Collections.unmodifiableSet(consumers);
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
    }

    private static String requireDigest(String value) {
        Objects.requireNonNull(value, "sha256");
        String normalized = value.toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("asset SHA-256 invalid");
        }
        return normalized;
    }

    private static String requireSafeSourceName(String value) {
        Objects.requireNonNull(value, "sourceName");
        if (value.isEmpty() || value.length() > 128
                || value.contains("/")
                || value.contains("\\")
                || value.contains("..")
                || value.indexOf('\u0000') >= 0) {
            throw new IllegalArgumentException("asset sourceName unsafe");
        }
        return value;
    }

    private static String requireMimeType(String value) {
        Objects.requireNonNull(value, "mimeType");
        if (!value.matches("[a-z0-9.+-]+/[a-z0-9.+-]+")) {
            throw new IllegalArgumentException("asset mimeType invalid");
        }
        return value;
    }

    public String assetId() { return assetId; }
    public VersionNumber version() { return version; }
    public AssetKind kind() { return kind; }
    public String sha256() { return sha256; }
    public boolean required() { return required; }
    public String sourceName() { return sourceName; }
    public String expectedOwnerId() { return expectedOwnerId; }
    public String mimeType() { return mimeType; }
    public long maxBytes() { return maxBytes; }
    public Set<String> consumerIds() { return consumerIds; }
    public CatalogLifecycle lifecycle() { return lifecycle; }

    public AssetDescriptor withLifecycle(CatalogLifecycle next) {
        return new AssetDescriptor(
                assetId,
                version,
                kind,
                sha256,
                required,
                sourceName,
                expectedOwnerId,
                mimeType,
                maxBytes,
                consumerIds,
                next
        );
    }
}
