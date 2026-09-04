package com.toolbox.tools.library;

import com.toolbox.tools.core.DigestUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

public final class BuiltinAssetCatalog {
    public static final class BuiltinAsset {
        private final AssetDescriptor descriptor;
        private final byte[] payload;

        BuiltinAsset(AssetDescriptor descriptor, byte[] payload) {
            this.descriptor = descriptor;
            this.payload = payload;
        }

        public AssetDescriptor descriptor() { return descriptor; }
        public byte[] payload() { return payload.clone(); }
    }

    private BuiltinAssetCatalog() {}

    public static List<BuiltinAsset> all() {
        return Arrays.asList(
                json(
                        "asset.theme.dark.neon",
                        "tema-gelap-neon.json",
                        "tool.asset",
                        "{\"nama\":\"Gelap Neon\",\"latar\":\"#071016\",\"permukaan\":\"#0D1B24\",\"neon\":\"#00F0B5\",\"neonBiru\":\"#4CC9FF\",\"teks\":\"#E8FFF8\"}"
                ),
                json(
                        "asset.tokens.default",
                        "token-bawaan.json",
                        "tool.ui",
                        "{\"radiusKartu\":18,\"spasi\":8,\"elevasi\":2,\"teksUtama\":16,\"teksJudul\":22}"
                ),
                json(
                        "asset.binding.profiles",
                        "profil-binding.json",
                        "tool.binding",
                        "{\"text\":\"binding.text\",\"value\":\"binding.value\",\"visible\":\"binding.visible\",\"enabled\":\"binding.enabled\"}"
                ),
                json(
                        "asset.animation.presets",
                        "preset-animasi.json",
                        "tool.ui",
                        "{\"preset\":[\"fade\",\"slide\",\"scale\",\"rotate\"],\"durasiDefaultMs\":220}"
                ),
                json(
                        "asset.component.kit",
                        "kit-komponen-bawaan.json",
                        "tool.asset",
                        "{\"komponen\":[\"button\",\"text\",\"input\",\"image\",\"container\",\"list\",\"card\"]}"
                )
        );
    }

    public static void install(
            AssetRegistry registry,
            AssetStore store
    ) throws IOException {
        AssetPayloadValidator validator = new AssetPayloadValidator();
        for (BuiltinAsset item : all()) {
            if (registry.resolveExact(
                    item.descriptor().assetId(),
                    item.descriptor().version()
            ) == null) {
                registry.publishReady(
                        item.descriptor(),
                        item.payload(),
                        validator
                );
            }
            if (!store.originalExists(item.descriptor())) {
                store.importOriginal(item.descriptor(), item.payload());
            }
        }
    }

    private static BuiltinAsset json(
            String id,
            String fileName,
            String consumer,
            String json
    ) {
        byte[] payload=json.getBytes(StandardCharsets.UTF_8);
        AssetDescriptor descriptor=new AssetDescriptor(
                id,
                VersionNumber.parse("1.0.0"),
                AssetKind.JSON,
                DigestUtils.sha256(payload),
                true,
                fileName,
                "toolbox.product",
                "application/json",
                Math.max(1024,payload.length*2L),
                new LinkedHashSet<>(Collections.singletonList(consumer)),
                CatalogLifecycle.DRAFT
        );
        return new BuiltinAsset(descriptor,payload);
    }
}
