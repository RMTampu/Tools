package io.toolbox.contracts.safety;

import java.util.Random;

public final class RuntimeSafetyPropertyTest {
    private static final int RESOURCE_CASES = 5_000;

    private RuntimeSafetyPropertyTest() {}

    public static void main(String[] args) {
        exhaustiveRecoveryTransitionReference();
        differentialResourceGuardReference();
        metamorphicResourceScaling();
        diagnosticRetentionProperty();

        System.out.println("PUBLIC_RUNTIME_SAFETY_PROPERTY_TESTS = PASS");
        System.out.println("RECOVERY_TRANSITION_CASES="
                + (SafetyContracts.RecoveryState.values().length * SafetyContracts.RecoveryEvent.values().length));
        System.out.println("RESOURCE_DIFFERENTIAL_CASES=" + RESOURCE_CASES);
        System.out.println("PROPERTY_TEST = PASS");
    }

    private static void exhaustiveRecoveryTransitionReference() {
        for (SafetyContracts.RecoveryState state : SafetyContracts.RecoveryState.values()) {
            for (SafetyContracts.RecoveryEvent event : SafetyContracts.RecoveryEvent.values()) {
                SafetyContracts.RecoveryState expected = referenceTransition(state, event);
                RecoveryMachine machine = new RecoveryMachine(state);
                if (expected == null) {
                    try {
                        machine.apply(event);
                        throw new AssertionError("expected illegal transition state=" + state + " event=" + event);
                    } catch (SafetyContracts.ContractException error) {
                        check("ILLEGAL_TRANSITION".equals(error.code()), "wrong illegal-transition code");
                        check(machine.state() == state, "illegal transition mutated state");
                    }
                } else {
                    SafetyContracts.Transition transition = machine.apply(event);
                    check(transition.previous() == state, "previous mismatch state=" + state + " event=" + event);
                    check(transition.event() == event, "event mismatch");
                    check(transition.next() == expected, "next mismatch state=" + state + " event=" + event);
                    check(machine.state() == expected, "machine state mismatch");
                }
            }
        }
    }

    private static void differentialResourceGuardReference() {
        Random random = new Random(0x5A17E5L);
        for (int i = 0; i < RESOURCE_CASES; i++) {
            int memoryBudget = 1 + random.nextInt(100_000);
            int workBudget = 1 + random.nextInt(100_000);
            int operationBudget = 1 + random.nextInt(10_000);
            int memoryUsed = random.nextInt(memoryBudget + Math.min(memoryBudget, 50_000) + 1);
            int workUsed = random.nextInt(workBudget + Math.min(workBudget, 50_000) + 1);
            int operationUsed = random.nextInt(operationBudget + Math.min(operationBudget, 5_000) + 1);

            SafetyContracts.GuardMode expected = referenceGuard(
                    memoryBudget, workBudget, operationBudget,
                    memoryUsed, workUsed, operationUsed
            );
            SafetyContracts.GuardMode actual = ResourceGuard.evaluate(
                    new SafetyContracts.ResourceBudget(memoryBudget, workBudget, operationBudget),
                    new SafetyContracts.ResourceSample(memoryUsed, workUsed, operationUsed)
            ).mode();
            check(actual == expected, "guard differential mismatch case=" + i);
        }
    }

    private static void metamorphicResourceScaling() {
        Random random = new Random(0xC0FFEE12L);
        for (int i = 0; i < 1_000; i++) {
            int budget = 1 + random.nextInt(1_000);
            int used = random.nextInt(budget + 501);
            int scale = 2 + random.nextInt(4);
            SafetyContracts.GuardMode original = ResourceGuard.evaluate(
                    new SafetyContracts.ResourceBudget(budget, budget, budget),
                    new SafetyContracts.ResourceSample(used, used, used)
            ).mode();
            SafetyContracts.GuardMode scaled = ResourceGuard.evaluate(
                    new SafetyContracts.ResourceBudget(budget * scale, budget * scale, budget * scale),
                    new SafetyContracts.ResourceSample(used * scale, used * scale, used * scale)
            ).mode();
            check(original == scaled, "scale relation mismatch case=" + i);
        }
    }

    private static void diagnosticRetentionProperty() {
        for (int capacity = 1; capacity <= 32; capacity++) {
            DiagnosticBuffer buffer = new DiagnosticBuffer(capacity);
            int inserted = capacity * 3 + 1;
            for (int i = 0; i < inserted; i++) {
                buffer.record(new SafetyContracts.DiagnosticEvent(
                        "diagnostic.property",
                        "runtime.safety.property",
                        SafetyContracts.Severity.INFO,
                        "diagnostic.property",
                        "diagnostic.property.message",
                        i
                ));
            }
            check(buffer.size() == capacity, "retained size capacity=" + capacity);
            check(buffer.droppedCount() == inserted - capacity, "drop count capacity=" + capacity);
            long firstExpected = inserted - capacity;
            for (int index = 0; index < capacity; index++) {
                check(buffer.snapshot().get(index).sequence() == firstExpected + index,
                        "retention order capacity=" + capacity + " index=" + index);
            }
        }
    }

    private static SafetyContracts.GuardMode referenceGuard(
            int memoryBudget,
            int workBudget,
            int operationBudget,
            int memoryUsed,
            int workUsed,
            int operationUsed
    ) {
        if (memoryUsed > memoryBudget || workUsed > workBudget || operationUsed > operationBudget) {
            return SafetyContracts.GuardMode.REJECT;
        }
        if (pressure(memoryUsed, memoryBudget)
                || pressure(workUsed, workBudget)
                || pressure(operationUsed, operationBudget)) {
            return SafetyContracts.GuardMode.DEGRADE;
        }
        return SafetyContracts.GuardMode.ALLOW;
    }

    private static boolean pressure(int used, int maximum) {
        return ((long) used) * 5L >= ((long) maximum) * 4L;
    }

    private static SafetyContracts.RecoveryState referenceTransition(
            SafetyContracts.RecoveryState state,
            SafetyContracts.RecoveryEvent event
    ) {
        if (state == SafetyContracts.RecoveryState.QUARANTINED) {
            return SafetyContracts.RecoveryState.QUARANTINED;
        }
        if (event == SafetyContracts.RecoveryEvent.FATAL_FAILURE) {
            return SafetyContracts.RecoveryState.QUARANTINED;
        }

        switch (state) {
            case NORMAL:
                if (event == SafetyContracts.RecoveryEvent.RESOURCE_PRESSURE) return SafetyContracts.RecoveryState.DEGRADED;
                if (event == SafetyContracts.RecoveryEvent.RESOURCE_NORMAL) return SafetyContracts.RecoveryState.NORMAL;
                if (event == SafetyContracts.RecoveryEvent.FAILURE_REQUIRES_RECOVERY) return SafetyContracts.RecoveryState.RECOVERY_REQUIRED;
                return null;
            case DEGRADED:
                if (event == SafetyContracts.RecoveryEvent.RESOURCE_PRESSURE) return SafetyContracts.RecoveryState.DEGRADED;
                if (event == SafetyContracts.RecoveryEvent.RESOURCE_NORMAL) return SafetyContracts.RecoveryState.NORMAL;
                if (event == SafetyContracts.RecoveryEvent.FAILURE_REQUIRES_RECOVERY) return SafetyContracts.RecoveryState.RECOVERY_REQUIRED;
                if (event == SafetyContracts.RecoveryEvent.ENTER_SAFE_MODE) return SafetyContracts.RecoveryState.SAFE_MODE;
                return null;
            case RECOVERY_REQUIRED:
                if (event == SafetyContracts.RecoveryEvent.RESOURCE_PRESSURE
                        || event == SafetyContracts.RecoveryEvent.RESOURCE_NORMAL
                        || event == SafetyContracts.RecoveryEvent.FAILURE_REQUIRES_RECOVERY) {
                    return SafetyContracts.RecoveryState.RECOVERY_REQUIRED;
                }
                if (event == SafetyContracts.RecoveryEvent.ENTER_SAFE_MODE) return SafetyContracts.RecoveryState.SAFE_MODE;
                if (event == SafetyContracts.RecoveryEvent.RECOVERY_SUCCEEDED) return SafetyContracts.RecoveryState.NORMAL;
                if (event == SafetyContracts.RecoveryEvent.RECOVERY_FAILED) return SafetyContracts.RecoveryState.QUARANTINED;
                return null;
            case SAFE_MODE:
                if (event == SafetyContracts.RecoveryEvent.RESOURCE_PRESSURE
                        || event == SafetyContracts.RecoveryEvent.RESOURCE_NORMAL
                        || event == SafetyContracts.RecoveryEvent.FAILURE_REQUIRES_RECOVERY
                        || event == SafetyContracts.RecoveryEvent.ENTER_SAFE_MODE) {
                    return SafetyContracts.RecoveryState.SAFE_MODE;
                }
                if (event == SafetyContracts.RecoveryEvent.RECOVERY_SUCCEEDED) return SafetyContracts.RecoveryState.NORMAL;
                if (event == SafetyContracts.RecoveryEvent.RECOVERY_FAILED) return SafetyContracts.RecoveryState.QUARANTINED;
                return null;
            default:
                throw new AssertionError("unhandled state " + state);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
