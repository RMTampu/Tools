package io.toolbox.contracts.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class RuntimeContractsSelfTest {
    private RuntimeContractsSelfTest() {}

    public static void main(String[] args) throws Exception {
        validBundlePublishesAndLooksUpExactly();
        invalidStableIdFailsClosed();
        inputCollectionsAreDefensivelyCopied();
        duplicatePublishIsRejectedWithoutPartialMutation();
        missingDependencyFailsClosed();
        providerMismatchFailsClosed();
        declarationMismatchFailsClosed();
        missingPermissionReferenceFailsClosed();
        snapshotsAreImmutable();
        concurrentPublicationsRemainConsistent();
        System.out.println("PUBLIC_RUNTIME_CONTRACT_TESTS = PASS");
        System.out.println("TEST_CASES=10");
    }

    private static void validBundlePublishesAndLooksUpExactly() {
        ProductRegistry registry = new ProductRegistry();
        ProductRegistry.RegistrySnapshot snapshot = registry.publish(
                RuntimeContractsSimulator.demoBundle("tool.demo.editor", Collections.emptyList())
        );
        check(snapshot.totalEntries() == 6, "valid bundle total entry count");
        check(registry.tool("tool.demo.editor").isPresent(), "exact tool lookup");
        check(registry.component("component.demo.button").isPresent(), "exact component lookup");
        check(registry.action("action.demo.save").isPresent(), "exact action lookup");
    }

    private static void invalidStableIdFailsClosed() {
        expectCode("CONTRACT_INVALID", () -> new Contracts.ToolContract(
                "Tool Bad",
                "1.0.0",
                "1.0.0",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "entry.bad"
        ));
    }

    private static void inputCollectionsAreDefensivelyCopied() {
        ArrayList<String> dependencies = new ArrayList<>();
        Contracts.ToolContract tool = emptyBundle("tool.copy.safe", dependencies).tool();
        dependencies.add("tool.injected.after.validation");
        check(tool.dependencies().isEmpty(), "constructor must snapshot caller collections");
    }

    private static void duplicatePublishIsRejectedWithoutPartialMutation() {
        ProductRegistry registry = new ProductRegistry();
        registry.publish(RuntimeContractsSimulator.demoBundle("tool.demo.editor", Collections.emptyList()));
        int before = registry.snapshot().totalEntries();
        expectCode("DUPLICATE_ID", () -> registry.publish(
                RuntimeContractsSimulator.demoBundle("tool.demo.editor", Collections.emptyList())
        ));
        check(registry.snapshot().totalEntries() == before, "duplicate rejection must not mutate registry");
    }

    private static void missingDependencyFailsClosed() {
        ProductRegistry registry = new ProductRegistry();
        expectCode("DEPENDENCY_MISSING", () -> registry.publish(
                emptyBundle("tool.dependent", List.of("tool.missing"))
        ));
        check(registry.snapshot().totalEntries() == 0, "missing dependency must leave empty registry");
    }

    private static void providerMismatchFailsClosed() {
        Contracts.ComponentContract component = new Contracts.ComponentContract(
                "component.bad.provider",
                "1.0.0",
                "1.0.0",
                "tool.other",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "implementation.bad.provider"
        );
        Contracts.ToolContract tool = new Contracts.ToolContract(
                "tool.bad.provider",
                "1.0.0",
                "1.0.0",
                Collections.emptyList(),
                List.of(component.id()),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "entry.bad.provider"
        );
        Contracts.ToolBundle bundle = new Contracts.ToolBundle(
                tool,
                List.of(component),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        );
        ProductRegistry registry = new ProductRegistry();
        expectCode("PROVIDER_MISMATCH", () -> registry.publish(bundle));
        check(registry.snapshot().totalEntries() == 0, "provider mismatch must be atomic");
    }

    private static void declarationMismatchFailsClosed() {
        Contracts.ToolContract tool = new Contracts.ToolContract(
                "tool.bad.declaration",
                "1.0.0",
                "1.0.0",
                Collections.emptyList(),
                List.of("component.declared.missing"),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "entry.bad.declaration"
        );
        Contracts.ToolBundle bundle = new Contracts.ToolBundle(
                tool,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        );
        ProductRegistry registry = new ProductRegistry();
        expectCode("DECLARATION_MISMATCH", () -> registry.publish(bundle));
    }

    private static void missingPermissionReferenceFailsClosed() {
        Contracts.ActionContract action = new Contracts.ActionContract(
                "action.missing.permission",
                "1.0.0",
                "1.0.0",
                "tool.permission.case",
                "schema.input.none",
                "schema.output.none",
                Collections.emptyList(),
                List.of("permission.not.published"),
                "execution.sync",
                "async.none",
                "timeout.not.applicable",
                "cancellation.not.applicable",
                "idempotency.none"
        );
        Contracts.ToolContract tool = new Contracts.ToolContract(
                "tool.permission.case",
                "1.0.0",
                "1.0.0",
                Collections.emptyList(),
                Collections.emptyList(),
                List.of(action.id()),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "entry.permission.case"
        );
        Contracts.ToolBundle bundle = new Contracts.ToolBundle(
                tool,
                Collections.emptyList(),
                List.of(action),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        );
        ProductRegistry registry = new ProductRegistry();
        expectCode("PERMISSION_REFERENCE_MISSING", () -> registry.publish(bundle));
        check(registry.snapshot().totalEntries() == 0, "missing permission must not partially publish action/tool");
    }

    private static void snapshotsAreImmutable() {
        ProductRegistry registry = new ProductRegistry();
        ProductRegistry.RegistrySnapshot snapshot = registry.publish(emptyBundle("tool.snapshot", Collections.emptyList()));
        boolean blocked = false;
        try {
            snapshot.tools().clear();
        } catch (UnsupportedOperationException expected) {
            blocked = true;
        }
        check(blocked, "snapshot maps must be immutable");
        check(registry.snapshot().tools().size() == 1, "snapshot mutation attempt must not alter registry");
    }

    private static void concurrentPublicationsRemainConsistent() throws Exception {
        final int workers = 24;
        ProductRegistry registry = new ProductRegistry();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        for (int i = 0; i < workers; i++) {
            final int index = i;
            pool.submit(() -> {
                try {
                    start.await();
                    registry.publish(emptyBundle("tool.concurrent.t" + index, Collections.emptyList()));
                } catch (Throwable error) {
                    failure.compareAndSet(null, error);
                }
            });
        }
        start.countDown();
        pool.shutdown();
        check(pool.awaitTermination(20, TimeUnit.SECONDS), "concurrent test timeout");
        if (failure.get() != null) throw new AssertionError("concurrent publication failed", failure.get());
        check(registry.snapshot().tools().size() == workers, "all concurrent tools must commit exactly once");
        check(registry.snapshot().totalEntries() == workers, "no partial extra entries under concurrency");
    }

    private static Contracts.ToolBundle emptyBundle(String toolId, List<String> dependencies) {
        Contracts.ToolContract tool = new Contracts.ToolContract(
                toolId,
                "1.0.0",
                "1.0.0",
                dependencies,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "entry.empty.tool"
        );
        return new Contracts.ToolBundle(
                tool,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        );
    }

    private static void expectCode(String code, Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected ContractException code=" + code);
        } catch (Contracts.ContractException expected) {
            check(code.equals(expected.code()), "expected code=" + code + " actual=" + expected.code());
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
