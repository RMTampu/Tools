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
        private final String messageIndonesia;

        Item(
                String id,
                String severity,
                String code,
                String subjectId,
                String messageIndonesia
        ) {
            this.id = id;
            this.severity = severity;
            this.code = code;
            this.subjectId = subjectId;
            this.messageIndonesia = messageIndonesia;
        }

        public String id() { return id; }
        public String severity() { return severity; }
        public String code() { return code; }
        public String subjectId() { return subjectId; }
        public String messageIndonesia() { return messageIndonesia; }
    }

    private final List<Item> items = new ArrayList<>();

    public synchronized void importRuntime(RuntimeDiagnostic diagnostic) {
        if (diagnostic == null) return;
        items.add(new Item(
                "diagnostic." + (items.size() + 1),
                severity(diagnostic.code()),
                diagnostic.code().name(),
                diagnostic.subjectId(),
                translate(diagnostic.code())
        ));
    }

    public synchronized void add(
            String id,
            String severity,
            String code,
            String subjectId,
            String messageIndonesia
    ) {
        items.add(new Item(
                StableId.require(id, "diagnosticId"),
                severity,
                code,
                StableId.require(subjectId, "subjectId"),
                messageIndonesia
        ));
    }

    public synchronized List<Item> all() {
        return Collections.unmodifiableList(new ArrayList<>(items));
    }

    public synchronized boolean hasBlocking() {
        for (Item item : items) {
            if ("MEMBLOKIR".equals(item.severity())) return true;
        }
        return false;
    }

    private static String severity(DiagnosticCode code) {
        switch (code) {
            case COMPONENT_UNAVAILABLE:
            case ACTION_IMPLEMENTATION_MISSING:
            case CONTRACT_MISMATCH:
                return "MEMBLOKIR";
            default:
                return "PERINGATAN";
        }
    }

    private static String translate(DiagnosticCode code) {
        switch (code) {
            case BROKEN_REFERENCE: return "Referensi rusak.";
            case BROKEN_DATA_REFERENCE: return "Referensi data rusak.";
            case BROKEN_NAVIGATION_REFERENCE: return "Referensi navigasi rusak.";
            case COMPONENT_UNAVAILABLE: return "Komponen tidak tersedia.";
            case ACTION_IMPLEMENTATION_MISSING: return "Implementasi aksi tidak tersedia.";
            case CONTRACT_MISMATCH: return "Kontrak tidak kompatibel.";
            case BINDING_AMBIGUOUS: return "Binding memiliki lebih dari satu target yang mungkin.";
            case BINDING_CYCLE_SUPPRESSED: return "Siklus binding dihentikan untuk mencegah loop.";
            case FLOW_LIMIT_EXCEEDED: return "Alur melewati batas eksekusi aman.";
            default: return "Diagnostik tidak dikenal.";
        }
    }
}
