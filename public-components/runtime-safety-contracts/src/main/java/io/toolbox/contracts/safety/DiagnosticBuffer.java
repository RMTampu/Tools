package io.toolbox.contracts.safety;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Bounded, insertion-ordered in-memory diagnostics with drop-oldest overflow. */
public final class DiagnosticBuffer {
    private final int capacity;
    private final ArrayDeque<SafetyContracts.DiagnosticEvent> events = new ArrayDeque<>();
    private long droppedCount;

    public DiagnosticBuffer(int capacity) {
        if (capacity < 1 || capacity > SafetyContracts.MAX_DIAGNOSTIC_CAPACITY) {
            throw new SafetyContracts.ContractException(
                    "RESOURCE_LIMIT",
                    "capacity must be within 1.." + SafetyContracts.MAX_DIAGNOSTIC_CAPACITY
            );
        }
        this.capacity = capacity;
    }

    public synchronized void record(SafetyContracts.DiagnosticEvent event) {
        Objects.requireNonNull(event, "event");
        if (events.size() == capacity) {
            events.removeFirst();
            if (droppedCount < Long.MAX_VALUE) {
                droppedCount++;
            }
        }
        events.addLast(event);
    }

    public synchronized List<SafetyContracts.DiagnosticEvent> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    public synchronized int size() {
        return events.size();
    }

    public int capacity() {
        return capacity;
    }

    public synchronized long droppedCount() {
        return droppedCount;
    }
}
