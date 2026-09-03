package io.toolbox.stagea.android;

import io.toolbox.contracts.safety.SafetyContracts;
import io.toolbox.stagea.StageAContracts;

public final class NormalizedResourceMath {
    public static final int NORMALIZED_BUDGET = 10_000;

    private NormalizedResourceMath() {}

    public static int normalizedUsage(long usedKiB, long capacityKiB, boolean forceReject) {
        if (usedKiB < 0 || capacityKiB <= 0) {
            throw new StageAContracts.StageAException("resource.measurement.invalid", "Invalid resource measurement");
        }
        if (forceReject) return NORMALIZED_BUDGET + 1;
        long scaled;
        if (usedKiB > Long.MAX_VALUE / NORMALIZED_BUDGET) {
            scaled = SafetyContracts.MAX_SAMPLE;
        } else {
            scaled = (usedKiB * NORMALIZED_BUDGET + capacityKiB - 1) / capacityKiB;
        }
        if (scaled < 0) scaled = 0;
        if (scaled > SafetyContracts.MAX_SAMPLE) scaled = SafetyContracts.MAX_SAMPLE;
        return (int) scaled;
    }

    public static int requireBudget(int value, String field) {
        if (value < 1 || value > SafetyContracts.MAX_BUDGET) {
            throw new StageAContracts.StageAException("resource.profile.invalid", field + " is outside resource contract");
        }
        return value;
    }

    public static int requireSample(int value, String field) {
        if (value < 0 || value > SafetyContracts.MAX_SAMPLE) {
            throw new StageAContracts.StageAException("resource.profile.invalid", field + " is outside resource contract");
        }
        return value;
    }
}
