package io.toolbox.stagea;

import io.toolbox.contracts.safety.DiagnosticBuffer;
import io.toolbox.contracts.safety.SafetyContracts;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public final class StageAFoundationPropertyTest {
    private StageAFoundationPropertyTest() {}
    public static void main(String[] args) throws Exception {
        int availabilityCases = availabilityCrossProduct();
        int resourceCases = resourceBoundarySweep();
        int concurrencyCases = concurrentAdmissionDeterminism();
        int diagnosticCases = diagnosticRetention();
        System.out.println("PUBLIC_STAGE_A_FOUNDATION_PROPERTY_TESTS = PASS");
        System.out.println("AVAILABILITY_CROSS_PRODUCT_CASES=" + availabilityCases);
        System.out.println("RESOURCE_BOUNDARY_CASES=" + resourceCases);
        System.out.println("CONCURRENT_ADMISSION_CASES=" + concurrencyCases);
        System.out.println("DIAGNOSTIC_RETENTION_CASES=" + diagnosticCases);
    }
    private static int availabilityCrossProduct() {
        int cases = 0;
        for (StageAContracts.Availability permission : StageAContracts.Availability.values()) {
            for (StageAContracts.Availability capability : StageAContracts.Availability.values()) {
                StageATestFixture f = new StageATestFixture(); f.permissions.state = permission; f.capabilities.state = capability;
                StageAContracts.AdmissionMode actual = f.guard.evaluate(StageATestFixture.ACTION_ID).mode();
                StageAContracts.AdmissionMode expected = permission == StageAContracts.Availability.AVAILABLE && capability == StageAContracts.Availability.AVAILABLE ? StageAContracts.AdmissionMode.ALLOW : StageAContracts.AdmissionMode.REJECT;
                if (actual != expected) throw new AssertionError("availability mismatch"); cases++;
            }
        }
        return cases;
    }
    private static int resourceBoundarySweep() {
        int cases = 0;
        for (int memory = 0; memory <= 110; memory++) {
            StageATestFixture f = new StageATestFixture(); f.resources.sample = new SafetyContracts.ResourceSample(memory, 10, 1);
            StageAContracts.AdmissionMode actual = f.guard.evaluate(StageATestFixture.ACTION_ID).mode();
            StageAContracts.AdmissionMode expected = memory > 100 ? StageAContracts.AdmissionMode.REJECT : (memory >= 80 ? StageAContracts.AdmissionMode.DEGRADE : StageAContracts.AdmissionMode.ALLOW);
            if (actual != expected) throw new AssertionError("resource mismatch memory=" + memory); cases++;
        }
        return cases;
    }
    private static int concurrentAdmissionDeterminism() throws Exception {
        StageATestFixture f = new StageATestFixture(); int threads = 8; int iterations = 1000;
        CountDownLatch ready = new CountDownLatch(threads), start = new CountDownLatch(1), done = new CountDownLatch(threads);
        AtomicReference<Throwable> failure = new AtomicReference<>(); List<Thread> workers = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Thread worker = new Thread(() -> {
                ready.countDown();
                try { start.await(); for (int j = 0; j < iterations; j++) if (f.guard.evaluate(StageATestFixture.ACTION_ID).mode() != StageAContracts.AdmissionMode.ALLOW) throw new AssertionError("non-deterministic admission"); }
                catch (Throwable t) { failure.compareAndSet(null, t); }
                finally { done.countDown(); }
            }, "stage-a-guard-" + i);
            workers.add(worker); worker.start();
        }
        ready.await(); start.countDown(); done.await(); if (failure.get() != null) throw new AssertionError(failure.get()); return threads * iterations;
    }
    private static int diagnosticRetention() {
        DiagnosticBuffer buffer = new DiagnosticBuffer(4); DiagnosticMapper mapper = new DiagnosticMapper();
        for (int i = 0; i < 10; i++) buffer.record(mapper.event("demo.tool", SafetyContracts.Severity.INFO, "diagnostic.sample", "diagnostic.sample"));
        if (buffer.size() != 4 || buffer.droppedCount() != 6L) throw new AssertionError("diagnostic retention mismatch"); return 10;
    }
}
