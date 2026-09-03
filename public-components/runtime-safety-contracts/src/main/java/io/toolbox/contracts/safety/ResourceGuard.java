package io.toolbox.contracts.safety;

import java.util.Objects;

/** Pure deterministic resource-pressure policy with no side effects. */
public final class ResourceGuard {
    private ResourceGuard() {}

    public static SafetyContracts.GuardDecision evaluate(
            SafetyContracts.ResourceBudget budget,
            SafetyContracts.ResourceSample sample
    ) {
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(sample, "sample");

        if (sample.memoryUnits() > budget.memoryUnits()
                || sample.workUnits() > budget.workUnits()
                || sample.concurrentOperations() > budget.concurrentOperations()) {
            return new SafetyContracts.GuardDecision(
                    SafetyContracts.GuardMode.REJECT,
                    "resource.limit.exceeded"
            );
        }

        if (atOrAbovePressure(sample.memoryUnits(), budget.memoryUnits())
                || atOrAbovePressure(sample.workUnits(), budget.workUnits())
                || atOrAbovePressure(sample.concurrentOperations(), budget.concurrentOperations())) {
            return new SafetyContracts.GuardDecision(
                    SafetyContracts.GuardMode.DEGRADE,
                    "resource.pressure"
            );
        }

        return new SafetyContracts.GuardDecision(
                SafetyContracts.GuardMode.ALLOW,
                "resource.normal"
        );
    }

    static boolean atOrAbovePressure(int used, int maximum) {
        // Inputs are contract-bounded, so both products stay far below int overflow.
        return used * 5 >= maximum * 4;
    }
}
