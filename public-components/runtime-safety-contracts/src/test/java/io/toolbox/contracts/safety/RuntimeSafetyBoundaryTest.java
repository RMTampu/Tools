package io.toolbox.contracts.safety;

public final class RuntimeSafetyBoundaryTest {
    private RuntimeSafetyBoundaryTest() {}

    public static void main(String[] args) {
        int cases = 0;
        pressureBelowThresholdAllows(); cases++;
        pressureAtThresholdDegrades(); cases++;
        pressureAtBudgetDegrades(); cases++;
        pressureAboveBudgetRejects(); cases++;
        minimumValidCapacityWorks(); cases++;
        maximumValidCapacityWorks(); cases++;
        zeroCapacityFailsClosed(); cases++;
        overMaximumCapacityFailsClosed(); cases++;
        maximumBudgetAndSampleRemainDefined(); cases++;
        overMaximumBudgetFailsClosed(); cases++;
        overMaximumSampleFailsClosed(); cases++;
        maximumStableIdLengthAccepted(); cases++;
        overMaximumStableIdFailsClosed(); cases++;
        negativeSequenceFailsClosed(); cases++;

        System.out.println("PUBLIC_RUNTIME_SAFETY_BOUNDARY_TESTS = PASS");
        System.out.println("BOUNDARY_TEST_CASES=" + cases);
    }

    private static void pressureBelowThresholdAllows() {
        check(mode(79, 100) == SafetyContracts.GuardMode.ALLOW, "79/100 must ALLOW");
    }

    private static void pressureAtThresholdDegrades() {
        check(mode(80, 100) == SafetyContracts.GuardMode.DEGRADE, "80/100 must DEGRADE");
    }

    private static void pressureAtBudgetDegrades() {
        check(mode(100, 100) == SafetyContracts.GuardMode.DEGRADE, "100/100 must DEGRADE");
    }

    private static void pressureAboveBudgetRejects() {
        check(mode(101, 100) == SafetyContracts.GuardMode.REJECT, "101/100 must REJECT");
    }

    private static void minimumValidCapacityWorks() {
        DiagnosticBuffer buffer = new DiagnosticBuffer(1);
        buffer.record(event(1));
        buffer.record(event(2));
        check(buffer.size() == 1 && buffer.droppedCount() == 1, "capacity one bounded");
    }

    private static void maximumValidCapacityWorks() {
        DiagnosticBuffer buffer = new DiagnosticBuffer(SafetyContracts.MAX_DIAGNOSTIC_CAPACITY);
        check(buffer.capacity() == SafetyContracts.MAX_DIAGNOSTIC_CAPACITY, "maximum capacity accepted");
    }

    private static void zeroCapacityFailsClosed() {
        expectCode("RESOURCE_LIMIT", () -> new DiagnosticBuffer(0));
    }

    private static void overMaximumCapacityFailsClosed() {
        expectCode("RESOURCE_LIMIT", () -> new DiagnosticBuffer(SafetyContracts.MAX_DIAGNOSTIC_CAPACITY + 1));
    }

    private static void maximumBudgetAndSampleRemainDefined() {
        SafetyContracts.ResourceBudget budget = new SafetyContracts.ResourceBudget(
                SafetyContracts.MAX_BUDGET,
                SafetyContracts.MAX_BUDGET,
                SafetyContracts.MAX_BUDGET
        );
        SafetyContracts.ResourceSample sample = new SafetyContracts.ResourceSample(
                SafetyContracts.MAX_SAMPLE,
                0,
                0
        );
        check(ResourceGuard.evaluate(budget, sample).mode() == SafetyContracts.GuardMode.REJECT,
                "max sample over budget rejects deterministically");
    }

    private static void overMaximumBudgetFailsClosed() {
        expectCode("RESOURCE_LIMIT", () -> new SafetyContracts.ResourceBudget(
                SafetyContracts.MAX_BUDGET + 1, 1, 1));
    }

    private static void overMaximumSampleFailsClosed() {
        expectCode("RESOURCE_LIMIT", () -> new SafetyContracts.ResourceSample(
                SafetyContracts.MAX_SAMPLE + 1, 0, 0));
    }

    private static void maximumStableIdLengthAccepted() {
        String id = "a" + "b".repeat(SafetyContracts.MAX_STABLE_ID_LENGTH - 1);
        check(id.equals(SafetyContracts.requireStableId(id, "id")), "max stable id accepted");
    }

    private static void overMaximumStableIdFailsClosed() {
        String id = "a" + "b".repeat(SafetyContracts.MAX_STABLE_ID_LENGTH);
        expectCode("RESOURCE_LIMIT", () -> SafetyContracts.requireStableId(id, "id"));
    }

    private static void negativeSequenceFailsClosed() {
        expectCode("CONTRACT_INVALID", () -> new SafetyContracts.DiagnosticEvent(
                "diagnostic.negative",
                "runtime.safety.boundary",
                SafetyContracts.Severity.ERROR,
                "diagnostic.negative",
                "diagnostic.negative.message",
                -1
        ));
    }

    private static SafetyContracts.GuardMode mode(int used, int maximum) {
        return ResourceGuard.evaluate(
                new SafetyContracts.ResourceBudget(maximum, 100, 10),
                new SafetyContracts.ResourceSample(used, 0, 0)
        ).mode();
    }

    private static SafetyContracts.DiagnosticEvent event(long sequence) {
        return new SafetyContracts.DiagnosticEvent(
                "diagnostic.boundary",
                "runtime.safety.boundary",
                SafetyContracts.Severity.INFO,
                "diagnostic.boundary",
                "diagnostic.boundary.message",
                sequence
        );
    }

    private static void expectCode(String expected, Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected failure code " + expected);
        } catch (SafetyContracts.ContractException error) {
            check(expected.equals(error.code()), "expected=" + expected + " actual=" + error.code());
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
