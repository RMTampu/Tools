package io.toolbox.stagea;

import io.toolbox.contracts.runtime.Contracts;
import io.toolbox.contracts.safety.SafetyContracts;

import java.util.Locale;
import java.util.Objects;

public final class DiagnosticMapper {
    private long nextSequence;
    public DiagnosticMapper() { this(0L); }
    public DiagnosticMapper(long initialSequence) {
        if (initialSequence < 0) throw new IllegalArgumentException("initialSequence must be non-negative");
        this.nextSequence = initialSequence;
    }
    public synchronized SafetyContracts.DiagnosticEvent fromFailure(String sourceId, Throwable failure) {
        Objects.requireNonNull(failure, "failure");
        String code = "unexpected.failure";
        SafetyContracts.Severity severity = SafetyContracts.Severity.ERROR;
        if (failure instanceof Contracts.ContractException) code = normalizeCode(((Contracts.ContractException) failure).code());
        else if (failure instanceof SafetyContracts.ContractException) code = normalizeCode(((SafetyContracts.ContractException) failure).code());
        else if (failure instanceof StageAContracts.StageAException) code = normalizeCode(((StageAContracts.StageAException) failure).code());
        if (code.contains("fatal") || code.contains("quarantine")) severity = SafetyContracts.Severity.FATAL;
        return event(sourceId, severity, code, "diagnostic.failure");
    }
    public synchronized SafetyContracts.DiagnosticEvent event(String sourceId, SafetyContracts.Severity severity, String code, String messageKey) {
        long sequence = nextSequence;
        if (nextSequence < Long.MAX_VALUE) nextSequence++;
        return new SafetyContracts.DiagnosticEvent("diagnostic.event", sourceId, severity, normalizeCode(code), messageKey, sequence);
    }
    public synchronized long nextSequence() { return nextSequence; }
    private static String normalizeCode(String raw) {
        String normalized = Objects.requireNonNull(raw, "raw").trim().toLowerCase(Locale.ROOT).replace('_', '.');
        return Contracts.requireStableId(normalized, "diagnosticCode");
    }
}
