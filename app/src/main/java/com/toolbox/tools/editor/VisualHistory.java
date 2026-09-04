package com.toolbox.tools.editor;

import java.util.ArrayDeque;
import java.util.Deque;

final class VisualHistory {
    static final int MAX_HISTORY = 64;

    private final Deque<VisualHistoryEntry> undo = new ArrayDeque<>();
    private final Deque<VisualHistoryEntry> redo = new ArrayDeque<>();

    synchronized void push(VisualHistoryEntry entry) {
        undo.addLast(entry);
        while (undo.size() > MAX_HISTORY) {
            undo.removeFirst();
        }
        redo.clear();
    }

    synchronized VisualHistoryEntry undo() {
        if (undo.isEmpty()) return null;
        VisualHistoryEntry entry = undo.removeLast();
        redo.addLast(entry);
        return entry;
    }

    synchronized VisualHistoryEntry redo() {
        if (redo.isEmpty()) return null;
        VisualHistoryEntry entry = redo.removeLast();
        undo.addLast(entry);
        return entry;
    }

    synchronized int undoSize() { return undo.size(); }
    synchronized int redoSize() { return redo.size(); }
}
