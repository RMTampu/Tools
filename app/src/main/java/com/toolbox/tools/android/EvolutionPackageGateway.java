package com.toolbox.tools.android;

import android.content.ContentResolver;
import android.net.Uri;

import com.toolbox.tools.delivery.PatchManifest;
import com.toolbox.tools.delivery.PatchPayload;
import com.toolbox.tools.delivery.RemoteVerificationProof;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class EvolutionPackageGateway {
    public static final int MAX_PACKAGE_BYTES = 2 * 1024 * 1024;

    public static final class Package {
        private final PatchManifest manifest;
        private final PatchPayload payload;
        private final RemoteVerificationProof proof;

        Package(
                PatchManifest manifest,
                PatchPayload payload,
                RemoteVerificationProof proof
        ) {
            this.manifest = manifest;
            this.payload = payload;
            this.proof = proof;
        }

        public PatchManifest manifest() { return manifest; }
        public PatchPayload payload() { return payload; }
        public RemoteVerificationProof proof() { return proof; }
    }

    public Package read(ContentResolver resolver, Uri uri)
            throws Exception {
        if (resolver == null || uri == null) {
            throw new IllegalArgumentException(
                    "paket evolusi tidak tersedia"
            );
        }
        if (!"content".equals(uri.getScheme())) {
            throw new IllegalArgumentException(
                    "paket harus content URI"
            );
        }

        byte[] bytes = readBounded(resolver, uri);
        JSONObject root = new JSONObject(
                new String(bytes, StandardCharsets.UTF_8)
        );

        int schemaVersion = root.optInt("schemaVersion", -1);
        if (schemaVersion != 1
                && schemaVersion
                    != PatchManifest.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "schema app.patch tidak didukung"
            );
        }

        JSONObject payloadJson = root.getJSONObject("payload");
        Map<String, String> upserts = new LinkedHashMap<>();
        JSONObject upsertJson = payloadJson.getJSONObject("upserts");
        JSONArray names = upsertJson.names();
        if (names != null) {
            for (int i = 0; i < names.length(); i++) {
                String key = names.getString(i);
                upserts.put(key, upsertJson.getString(key));
            }
        }
        Set<String> deletes = new LinkedHashSet<>();
        JSONArray deleteJson = payloadJson.optJSONArray("deletes");
        if (deleteJson != null) {
            for (int i = 0; i < deleteJson.length(); i++) {
                deletes.add(deleteJson.getString(i));
            }
        }
        PatchPayload payload = new PatchPayload(
                upserts,
                deletes
        );

        JSONObject manifestJson = root.getJSONObject("manifest");
        String declaredPayload = manifestJson.optString(
                "payloadSha256",
                payload.sha256()
        );
        if (!payload.sha256().equals(declaredPayload)) {
            throw new IllegalArgumentException(
                    "hash payload app.patch berbeda"
            );
        }

        PatchManifest manifest;
        if (schemaVersion == 1) {
            manifest = new PatchManifest(
                    manifestJson.getString("patchId"),
                    manifestJson.getString("projectId"),
                    manifestJson.getLong("baseRevision"),
                    manifestJson.getLong("targetRevision"),
                    manifestJson.getString(
                            "parentSignedApkSha256"
                    ),
                    manifestJson.getString(
                            "targetCandidateSha256"
                    ),
                    manifestJson.getString(
                            "rollbackBaselineApkSha256"
                    ),
                    declaredPayload
            );
        } else {
            Set<String> dependencies =
                    stringSet(
                            manifestJson.optJSONArray(
                                    "dependencies"
                            )
                    );
            Set<String> capabilities =
                    stringSet(
                            manifestJson.optJSONArray(
                                    "requiredCapabilities"
                            )
                    );
            Map<String, String> fileHashes =
                    stringMap(
                            manifestJson.getJSONObject(
                                    "fileHashes"
                            )
                    );
            if (!declaredPayload.equals(
                    fileHashes.get("payload"))) {
                throw new IllegalArgumentException(
                        "fileHashes.payload berbeda"
                );
            }

            manifest = new PatchManifest(
                    manifestJson.getString("patchId"),
                    manifestJson.getString("projectId"),
                    manifestJson.getLong("baseRevision"),
                    manifestJson.getLong("targetRevision"),
                    manifestJson.getString(
                            "parentSignedApkSha256"
                    ),
                    manifestJson.getString(
                            "targetCandidateSha256"
                    ),
                    manifestJson.getString(
                            "rollbackBaselineApkSha256"
                    ),
                    declaredPayload,
                    manifestJson.getString("packageType"),
                    manifestJson.getString("targetPackage"),
                    manifestJson.getString("packageVersion"),
                    manifestJson.getInt("minHostVersionCode"),
                    manifestJson.getInt("maxHostVersionCode"),
                    dependencies,
                    capabilities,
                    fileHashes,
                    manifestJson.getString("intent")
            );
        }

        JSONObject proofJson = root.getJSONObject("proof");
        RemoteVerificationProof proof =
                new RemoteVerificationProof(
                        proofJson.getString(
                                "signerIdentitySha256"
                        ),
                        proofJson.getString("algorithm"),
                        proofJson.getString("signatureBase64")
                );

        return new Package(
                manifest,
                payload,
                proof
        );
    }

    private static byte[] readBounded(
            ContentResolver resolver,
            Uri uri
    ) throws Exception {
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) {
                throw new IllegalArgumentException(
                        "paket tidak dapat dibaca"
                );
            }
            ByteArrayOutputStream output =
                    new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_PACKAGE_BYTES) {
                    throw new IllegalArgumentException(
                            "paket evolusi melebihi batas"
                    );
                }
                output.write(buffer, 0, read);
            }
            if (total == 0) {
                throw new IllegalArgumentException(
                        "paket evolusi kosong"
                );
            }
            return output.toByteArray();
        }
    }

    private static Set<String> stringSet(JSONArray array)
            throws Exception {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (array == null) return out;
        if (array.length() > 128) {
            throw new IllegalArgumentException(
                    "array manifest melebihi batas"
            );
        }
        for (int i = 0; i < array.length(); i++) {
            out.add(array.getString(i));
        }
        return out;
    }

    private static Map<String, String> stringMap(
            JSONObject object
    ) throws Exception {
        LinkedHashMap<String, String> out =
                new LinkedHashMap<>();
        JSONArray names = object.names();
        if (names == null) return out;
        if (names.length() > 256) {
            throw new IllegalArgumentException(
                    "fileHashes melebihi batas"
            );
        }
        for (int i = 0; i < names.length(); i++) {
            String key = names.getString(i);
            out.put(key, object.getString(key));
        }
        return out;
    }
}
