package com.toolbox.tools.library;

import com.toolbox.tools.core.DigestUtils;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class InMemoryAssetStore implements AssetStore {
    private final Map<String, byte[]> originals = new LinkedHashMap<>();
    private final Map<String, byte[]> previews = new LinkedHashMap<>();

    @Override
    public synchronized void importOriginal(
            AssetDescriptor descriptor,
            byte[] bytes
    ) throws IOException {
        verify(descriptor, bytes);
        String key = key(descriptor);
        byte[] existing = originals.get(key);
        if (existing != null
                && !DigestUtils.sha256(existing).equals(descriptor.sha256())) {
            throw new IOException("existing original integrity mismatch");
        }
        originals.put(key, bytes.clone());
    }

    @Override
    public synchronized AssetStatus status(AssetDescriptor descriptor) {
        byte[] bytes = originals.get(key(descriptor));
        if (bytes == null) return AssetStatus.MISSING_ASSET;
        if (bytes.length > descriptor.maxBytes()) {
            return AssetStatus.INCOMPATIBLE_ASSET;
        }
        return DigestUtils.sha256(bytes).equals(descriptor.sha256())
                ? AssetStatus.AVAILABLE
                : AssetStatus.BROKEN_ASSET_INTEGRITY;
    }

    @Override
    public synchronized byte[] readOriginal(AssetDescriptor descriptor)
            throws IOException {
        if (status(descriptor) != AssetStatus.AVAILABLE) {
            throw new IOException("asset unavailable");
        }
        return originals.get(key(descriptor)).clone();
    }

    @Override
    public synchronized void relinkOriginal(
            AssetDescriptor descriptor,
            byte[] candidate
    ) throws IOException {
        verify(descriptor, candidate);
        originals.put(key(descriptor), candidate.clone());
    }

    @Override
    public synchronized void writePreviewCache(
            AssetDescriptor descriptor,
            byte[] preview
    ) throws IOException {
        if (preview == null
                || preview.length > Math.min(descriptor.maxBytes(), 512 * 1024L)) {
            throw new IOException("preview cache exceeds budget");
        }
        previews.put(key(descriptor), preview.clone());
    }

    @Override
    public synchronized void clearCache() {
        previews.clear();
    }

    @Override
    public synchronized boolean originalExists(AssetDescriptor descriptor) {
        return originals.containsKey(key(descriptor));
    }

    @Override
    public synchronized boolean previewExists(AssetDescriptor descriptor) {
        return previews.containsKey(key(descriptor));
    }

    private static void verify(
            AssetDescriptor descriptor,
            byte[] bytes
    ) throws IOException {
        if (bytes == null || bytes.length == 0) {
            throw new IOException("asset payload empty");
        }
        if (bytes.length > descriptor.maxBytes()) {
            throw new IOException("asset payload exceeds contract budget");
        }
        if (!DigestUtils.sha256(bytes).equals(descriptor.sha256())) {
            throw new IOException("asset payload SHA-256 mismatch");
        }
    }

    private static String key(AssetDescriptor descriptor) {
        return descriptor.assetId() + "@" + descriptor.version();
    }
}
