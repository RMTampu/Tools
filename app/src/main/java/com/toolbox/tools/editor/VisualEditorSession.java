package com.toolbox.tools.editor;

import com.toolbox.tools.core.StableId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class VisualEditorSession {
    private final Map<String, VisualObjectState> objects =
            new LinkedHashMap<>();
    private final Map<String, VisualLockSet> locks =
            new LinkedHashMap<>();
    private final VisualHistory history = new VisualHistory();
    private final List<EditorDiagnostic> diagnostics = new ArrayList<>();
    private String selectedObjectId;

    public synchronized void addObject(VisualObjectState object) {
        Objects.requireNonNull(object, "object");
        if (objects.put(object.objectId(), object) != null) {
            throw new IllegalArgumentException("duplicate object id");
        }
        locks.put(object.objectId(), new VisualLockSet());
    }

    public synchronized void select(String objectId) {
        String id = StableId.require(objectId, "objectId");
        if (!objects.containsKey(id)) {
            diagnostics.add(new EditorDiagnostic(
                    "editor.object.missing",
                    id,
                    "Object tidak tersedia"
            ));
            throw new IllegalArgumentException("object unavailable");
        }
        selectedObjectId = id;
    }

    public synchronized void clearSelection() {
        selectedObjectId = null;
    }

    public synchronized void setLocked(
            String objectId,
            VisualCapability capability,
            boolean locked
    ) {
        VisualLockSet set = lockSet(objectId);
        set.setLocked(capability, locked);
    }

    public synchronized void apply(
            VisualEditTransaction transaction,
            VisualCapabilitySet capabilities
    ) {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(capabilities, "capabilities");
        Map<String, VisualObjectState> before =
                new LinkedHashMap<>(objects);

        for (VisualEditOperation operation : transaction.operations()) {
            VisualObjectState current = objects.get(operation.objectId());
            if (current == null) {
                diagnostics.add(new EditorDiagnostic(
                        "editor.operation.broken",
                        operation.objectId(),
                        "Target object tidak tersedia"
                ));
                throw new IllegalArgumentException("BROKEN_OPERATION");
            }
            if (!capabilities.supports(operation.capability())) {
                diagnostics.add(new EditorDiagnostic(
                        "editor.operation.unsupported",
                        operation.objectId(),
                        "Capability tidak didukung"
                ));
                throw new IllegalArgumentException("UNSUPPORTED_OPERATION");
            }
            if (lockSet(operation.objectId()).isLocked(operation.capability())) {
                diagnostics.add(new EditorDiagnostic(
                        "editor.operation.locked",
                        operation.objectId(),
                        "Area edit terkunci"
                ));
                throw new IllegalStateException("LOCKED_OPERATION");
            }

            objects.put(
                    operation.objectId(),
                    current.withProperty(
                            operation.propertyId(),
                            operation.value()
                    )
            );
        }

        history.push(new VisualHistoryEntry(
                transaction.transactionId(),
                before,
                new LinkedHashMap<>(objects)
        ));
    }

    public synchronized boolean undo() {
        VisualHistoryEntry entry = history.undo();
        if (entry == null) return false;
        replace(entry.before());
        return true;
    }

    public synchronized boolean redo() {
        VisualHistoryEntry entry = history.redo();
        if (entry == null) return false;
        replace(entry.after());
        return true;
    }

    public synchronized VisualObjectState object(String objectId) {
        return objects.get(StableId.require(objectId, "objectId"));
    }

    public synchronized Map<String, VisualObjectState> objects() {
        return Collections.unmodifiableMap(
                new LinkedHashMap<>(objects)
        );
    }

    public synchronized String selectedObjectId() {
        return selectedObjectId;
    }

    public synchronized int undoCount() {
        return history.undoSize();
    }

    public synchronized int redoCount() {
        return history.redoSize();
    }

    public synchronized List<EditorDiagnostic> diagnostics() {
        return Collections.unmodifiableList(
                new ArrayList<>(diagnostics)
        );
    }

    private VisualLockSet lockSet(String objectId) {
        String id = StableId.require(objectId, "objectId");
        VisualLockSet set = locks.get(id);
        if (set == null) {
            throw new IllegalArgumentException("object unavailable");
        }
        return set;
    }

    private void replace(Map<String, VisualObjectState> next) {
        objects.clear();
        objects.putAll(next);
        locks.keySet().retainAll(objects.keySet());
        for (String id : objects.keySet()) {
            if (!locks.containsKey(id)) {
                locks.put(id, new VisualLockSet());
            }
        }
        if (selectedObjectId != null
                && !objects.containsKey(selectedObjectId)) {
            selectedObjectId = null;
        }
    }
}
