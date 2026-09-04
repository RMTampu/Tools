package com.toolbox.tools.library;

import com.toolbox.tools.core.StableId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public final class AssetRegistry {
    private final Map<String, NavigableMap<VersionNumber, AssetDescriptor>> byId =
            new LinkedHashMap<>();
    private final Map<String, List<AssetDescriptor>> byDigest =
            new LinkedHashMap<>();

    public synchronized AssetStatus publishReady(
            AssetDescriptor draft,
            byte[] payload,
            AssetPayloadValidator validator
    ) {
        if (draft.lifecycle() == CatalogLifecycle.ARCHIVED) {
            throw new IllegalArgumentException("archived asset cannot become READY");
        }
        AssetValidationResult validation = validator.validateReady(draft, payload);
        if (!validation.isPass()) {
            throw new IllegalArgumentException(
                    "asset validation failed: " + validation.message()
            );
        }
        return register(draft.withLifecycle(CatalogLifecycle.READY));
    }

    public synchronized AssetStatus register(AssetDescriptor descriptor) {
        String id = StableId.require(descriptor.assetId(), "assetId");
        NavigableMap<VersionNumber, AssetDescriptor> versions =
                byId.computeIfAbsent(id, ignored -> new TreeMap<>());
        if (versions.containsKey(descriptor.version())) {
            throw new IllegalArgumentException("asset version already registered");
        }
        versions.put(descriptor.version(), descriptor);
        List<AssetDescriptor> sameDigest =
                byDigest.computeIfAbsent(descriptor.sha256(), ignored -> new ArrayList<>());
        boolean duplicate = false;
        for (AssetDescriptor item : sameDigest) {
            if (!item.assetId().equals(descriptor.assetId())) {
                duplicate = true;
                break;
            }
        }
        sameDigest.add(descriptor);
        return duplicate ? AssetStatus.DUPLICATE_CANDIDATE : AssetStatus.AVAILABLE;
    }

    public synchronized AssetDescriptor resolveExact(
            String assetId,
            VersionNumber version
    ) {
        NavigableMap<VersionNumber, AssetDescriptor> versions =
                byId.get(StableId.require(assetId, "assetId"));
        return versions == null ? null : versions.get(version);
    }

    public synchronized AssetDescriptor latestReady(String assetId) {
        NavigableMap<VersionNumber, AssetDescriptor> versions =
                byId.get(StableId.require(assetId, "assetId"));
        if (versions == null) return null;
        for (AssetDescriptor descriptor : versions.descendingMap().values()) {
            if (descriptor.lifecycle() == CatalogLifecycle.READY) {
                return descriptor;
            }
        }
        return null;
    }

    public synchronized boolean hasAnyVersion(String assetId) {
        NavigableMap<VersionNumber, AssetDescriptor> versions =
                byId.get(StableId.require(assetId, "assetId"));
        return versions != null && !versions.isEmpty();
    }

    public synchronized boolean hasCompatible(
            String assetId,
            VersionRange range
    ) {
        NavigableMap<VersionNumber, AssetDescriptor> versions =
                byId.get(StableId.require(assetId, "assetId"));
        if (versions == null) return false;
        for (AssetDescriptor descriptor : versions.values()) {
            if (descriptor.lifecycle() == CatalogLifecycle.READY
                    && range.contains(descriptor.version())) {
                return true;
            }
        }
        return false;
    }

    public synchronized boolean isDuplicateDigest(
            String assetId,
            String sha256
    ) {
        List<AssetDescriptor> descriptors = byDigest.get(sha256);
        if (descriptors == null) return false;
        for (AssetDescriptor descriptor : descriptors) {
            if (!descriptor.assetId().equals(assetId)) return true;
        }
        return false;
    }

    public synchronized List<AssetDescriptor> allReady() {
        List<AssetDescriptor> out = new ArrayList<>();
        for (NavigableMap<VersionNumber, AssetDescriptor> versions : byId.values()) {
            for (AssetDescriptor descriptor : versions.values()) {
                if (descriptor.lifecycle() == CatalogLifecycle.READY) {
                    out.add(descriptor);
                }
            }
        }
        out.sort(Comparator.comparing(AssetDescriptor::assetId)
                .thenComparing(AssetDescriptor::version));
        return Collections.unmodifiableList(out);
    }
}
