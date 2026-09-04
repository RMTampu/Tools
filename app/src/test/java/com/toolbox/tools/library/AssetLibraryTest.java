package com.toolbox.tools.library;

import com.toolbox.tools.core.DigestUtils;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class AssetLibraryTest {
    @Test
    public void readyJsonAssetRequiresHashConsumerMimeAndSyntax() {
        byte[] payload = "{\"name\":\"contoh\"}".getBytes(StandardCharsets.UTF_8);
        AssetDescriptor draft = descriptor(
                "asset.config.example",
                payload,
                AssetKind.JSON,
                "application/json"
        );

        AssetRegistry registry = new AssetRegistry();
        AssetStatus status = registry.publishReady(
                draft,
                payload,
                new AssetPayloadValidator()
        );

        assertEquals(AssetStatus.AVAILABLE, status);
        assertEquals(
                CatalogLifecycle.READY,
                registry.latestReady("asset.config.example").lifecycle()
        );
    }

    @Test
    public void invalidJsonAndUnsupportedRuntimeTypeCannotBecomeReady() {
        byte[] invalid = "{broken".getBytes(StandardCharsets.UTF_8);
        AssetDescriptor json = descriptor(
                "asset.config.bad",
                invalid,
                AssetKind.JSON,
                "application/json"
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new AssetRegistry().publishReady(
                        json,
                        invalid,
                        new AssetPayloadValidator()
                )
        );

        byte[] pngHeader = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47};
        AssetDescriptor image = descriptor(
                "asset.image.pending",
                pngHeader,
                AssetKind.IMAGE,
                "image/png"
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new AssetRegistry().publishReady(
                        image,
                        pngHeader,
                        new AssetPayloadValidator()
                )
        );
    }

    @Test
    public void duplicateDigestIsCandidateNotSilentMerge() {
        byte[] payload = "same".getBytes(StandardCharsets.UTF_8);
        AssetRegistry registry = new AssetRegistry();

        AssetDescriptor first = descriptor(
                "asset.raw.first",
                payload,
                AssetKind.RAW,
                "application/octet-stream"
        );
        AssetDescriptor second = descriptor(
                "asset.raw.second",
                payload,
                AssetKind.RAW,
                "application/octet-stream"
        );

        assertEquals(
                AssetStatus.AVAILABLE,
                registry.publishReady(first, payload, new AssetPayloadValidator())
        );
        assertEquals(
                AssetStatus.DUPLICATE_CANDIDATE,
                registry.publishReady(second, payload, new AssetPayloadValidator())
        );
        assertTrue(
                registry.isDuplicateDigest(
                        "asset.raw.second",
                        DigestUtils.sha256(payload)
                )
        );
    }

    @Test
    public void fileStoreKeepsOriginalWhenCacheIsClearedAndRelinkIsHashBound()
            throws Exception {
        Path root = Files.createTempDirectory("toolbox-stage3-assets");
        FileAssetStore store = new FileAssetStore(root.toFile());
        byte[] original = "original".getBytes(StandardCharsets.UTF_8);
        AssetDescriptor descriptor = descriptor(
                "asset.raw.original",
                original,
                AssetKind.RAW,
                "application/octet-stream"
        );

        store.importOriginal(descriptor, original);
        store.writePreviewCache(
                descriptor,
                "preview".getBytes(StandardCharsets.UTF_8)
        );
        assertTrue(store.originalExists(descriptor));
        assertTrue(store.previewExists(descriptor));

        store.clearCache();

        assertTrue(store.originalExists(descriptor));
        assertFalse(store.previewExists(descriptor));
        assertEquals(AssetStatus.AVAILABLE, store.status(descriptor));

        assertThrows(
                java.io.IOException.class,
                () -> store.relinkOriginal(
                        descriptor,
                        "wrong".getBytes(StandardCharsets.UTF_8)
                )
        );
        assertEquals(
                "original",
                new String(store.readOriginal(descriptor), StandardCharsets.UTF_8)
        );
    }

    @Test
    public void unsafeSourceNameFailsBeforeStoragePathExists() {
        byte[] payload = "x".getBytes(StandardCharsets.UTF_8);
        assertThrows(
                IllegalArgumentException.class,
                () -> new AssetDescriptor(
                        "asset.raw.escape",
                        VersionNumber.parse("1.0.0"),
                        AssetKind.RAW,
                        DigestUtils.sha256(payload),
                        true,
                        "../escape.bin",
                        "owner.project",
                        "application/octet-stream",
                        1024,
                        Collections.singleton("consumer.test"),
                        CatalogLifecycle.DRAFT
                )
        );
    }

    private static AssetDescriptor descriptor(
            String id,
            byte[] payload,
            AssetKind kind,
            String mime
    ) {
        return new AssetDescriptor(
                id,
                VersionNumber.parse("1.0.0"),
                kind,
                DigestUtils.sha256(payload),
                true,
                "source.bin",
                "owner.project",
                mime,
                1024 * 1024,
                Collections.singleton("consumer.test"),
                CatalogLifecycle.DRAFT
        );
    }
}
