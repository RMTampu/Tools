package com.toolbox.tools.product;

import com.toolbox.tools.core.StableId;
import com.toolbox.tools.runtime.DiagnosticCode;
import com.toolbox.tools.runtime.RuntimeDiagnostic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DiagnosticCenter {
    public static final class Item {
        private final String id;
        private final String severity;
        private final String code;
        private final String subjectId;
        private final String source;
        private final String resourceId;
        private final String location;
        private final String messageIndonesia;
        private final String suggestedFix;
        private final List<String> relatedDiagnostics;

        Item(
                String id,
                String severity,
                String code,
                String subjectId,
                String source,
                String resourceId,
                String location,
                String messageIndonesia,
                String suggestedFix,
                List<String> relatedDiagnostics
        ) {
            this.id = StableId.require(id, "diagnosticId");
            this.severity = requireSeverity(severity);
            this.code = requireText(code, "diagnosticCode");
            this.subjectId = StableId.require(subjectId, "subjectId");
            this.source = requireText(source, "source");
            this.resourceId = resourceId == null || resourceId.trim().isEmpty()
                    ? subjectId
                    : StableId.require(resourceId, "resourceId");
            this.location = requireText(location, "location");
            this.messageIndonesia =
                    requireText(messageIndonesia, "messageIndonesia");
            this.suggestedFix =
                    requireText(suggestedFix, "suggestedFix");
            List<String> related = new ArrayList<>();
            if (relatedDiagnostics != null) {
                for (String item : relatedDiagnostics) {
                    related.add(
                            StableId.require(
                                    item,
                                    "relatedDiagnosticId"
                            )
                    );
                }
            }
            this.relatedDiagnostics = Collections.unmodifiableList(
                    related
            );
        }

        public String id() { return id; }
        public String severity() { return severity; }
        public String code() { return code; }
        public String subjectId() { return subjectId; }
        public String source() { return source; }
        public String resourceId() { return resourceId; }
        public String location() { return location; }
        public String messageIndonesia() { return messageIndonesia; }
        public String suggestedFix() { return suggestedFix; }
        public List<String> relatedDiagnostics() {
            return relatedDiagnostics;
        }

        public boolean complete() {
            return id != null
                    && severity != null
                    && code != null
                    && subjectId != null
                    && source != null
                    && resourceId != null
                    && location != null
                    && messageIndonesia != null
                    && suggestedFix != null
                    && relatedDiagnostics != null;
        }
    }

    private final List<Item> items = new ArrayList<>();

    public synchronized void importRuntime(
            RuntimeDiagnostic diagnostic
    ) {
        if (diagnostic == null) return;
        DiagnosticCode code = diagnostic.code();
        items.add(new Item(
                "diagnostic." + (items.size() + 1),
                severity(code),
                code.name(),
                diagnostic.subjectId(),
                "runtime",
                diagnostic.subjectId(),
                "runtime-model",
                translate(code),
                suggestedFix(code),
                Collections.emptyList()
        ));
    }

    public synchronized void add(
            String id,
            String severity,
            String code,
            String subjectId,
            String messageIndonesia
    ) {
        add(
                id,
                severity,
                code,
                subjectId,
                "toolbox",
                subjectId,
                "unknown",
                messageIndonesia,
                "Periksa kontrak dan resource terkait.",
                Collections.emptyList()
        );
    }

    public synchronized void add(
            String id,
            String severity,
            String code,
            String subjectId,
            String source,
            String resourceId,
            String location,
            String messageIndonesia,
            String suggestedFix,
            List<String> relatedDiagnostics
    ) {
        items.add(new Item(
                id,
                severity,
                code,
                subjectId,
                source,
                resourceId,
                location,
                messageIndonesia,
                suggestedFix,
                relatedDiagnostics
        ));
    }

    public synchronized List<Item> all() {
        return Collections.unmodifiableList(
                new ArrayList<>(items)
        );
    }

    public synchronized boolean hasBlocking() {
        for (Item item : items) {
            if ("BLOCKING".equals(item.severity())
                    || "MEMBLOKIR".equals(item.severity())) {
                return true;
            }
        }
        return false;
    }

    private static String severity(DiagnosticCode code) {
        switch (code) {
            case COMPONENT_UNAVAILABLE:
            case ACTION_IMPLEMENTATION_MISSING:
            case CONTRACT_MISMATCH:
            case MISSING_ASSET:
            case PERMISSION_CONTRACT_MISSING:
            case SIGNING_IDENTITY_MISMATCH:
                return "BLOCKING";
            case STALE_WRITE:
            case CAPABILITY_INCOMPATIBLE:
            case LAYOUT_CONSTRAINT_CONFLICT:
                return "ERROR";
            default:
                return "WARNING";
        }
    }

    private static String translate(DiagnosticCode code) {
        switch (code) {
            case BROKEN_REFERENCE:
                return "Referensi rusak.";
            case BROKEN_DATA_REFERENCE:
                return "Referensi data rusak.";
            case BROKEN_NAVIGATION_REFERENCE:
                return "Referensi navigasi rusak.";
            case COMPONENT_UNAVAILABLE:
                return "Komponen tidak tersedia.";
            case ACTION_IMPLEMENTATION_MISSING:
                return "Implementasi aksi tidak tersedia.";
            case CONTRACT_MISMATCH:
                return "Kontrak tidak kompatibel.";
            case BINDING_AMBIGUOUS:
                return "Binding memiliki lebih dari satu target yang mungkin.";
            case BINDING_CYCLE_SUPPRESSED:
                return "Siklus binding dihentikan untuk mencegah loop.";
            case FLOW_LIMIT_EXCEEDED:
                return "Alur melewati batas eksekusi aman.";
            case MISSING_ASSET:
                return "Aset wajib tidak tersedia.";
            case PERMISSION_CONTRACT_MISSING:
                return "Kontrak izin yang diwajibkan tidak tersedia.";
            case LAYOUT_CONSTRAINT_CONFLICT:
                return "Constraint layout saling bertentangan.";
            case STALE_WRITE:
                return "Perubahan ditolak karena revisi sudah berubah.";
            case CAPABILITY_INCOMPATIBLE:
                return "Capability target tidak kompatibel.";
            case SIGNING_IDENTITY_MISMATCH:
                return "Identitas signing tidak cocok.";
            default:
                return "Diagnostik tidak dikenal.";
        }
    }

    private static String suggestedFix(DiagnosticCode code) {
        switch (code) {
            case MISSING_ASSET:
                return "Relink aset dengan Stable ID yang sama.";
            case PERMISSION_CONTRACT_MISSING:
                return "Tambahkan permission contract dari capability yang dipakai.";
            case STALE_WRITE:
                return "Muat ulang revisi terbaru lalu terapkan ulang perubahan.";
            case LAYOUT_CONSTRAINT_CONFLICT:
                return "Perbaiki constraint yang bertabrakan sebelum Save/Build.";
            case CAPABILITY_INCOMPATIBLE:
                return "Gunakan hanya capability yang diiklankan target.";
            case SIGNING_IDENTITY_MISMATCH:
                return "Gunakan certificate lineage baseline yang sah.";
            default:
                return "Periksa resource dan contract yang disebut diagnostic.";
        }
    }

    private static String requireSeverity(String value) {
        String v = requireText(value, "severity").toUpperCase(
                java.util.Locale.ROOT
        );
        if (!v.equals("INFO")
                && !v.equals("WARNING")
                && !v.equals("ERROR")
                && !v.equals("BLOCKING")
                && !v.equals("PERINGATAN")
                && !v.equals("MEMBLOKIR")) {
            throw new IllegalArgumentException(
                    "severity tidak dikenal"
            );
        }
        return v;
    }

    private static String requireText(
            String value,
            String label
    ) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " kosong");
        }
        String trimmed = value.trim();
        return trimmed.length() > 320
                ? trimmed.substring(0, 320)
                : trimmed;
    }
}
