package io.toolbox.contracts.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class RuntimeContractsSelfTest {
    private RuntimeContractsSelfTest() {}

    public static void main(String[] args) throws Exception {
        validBundlePublishesAndLooksUpExactly();
        invalidStableIdFailsClosed();
        invalidVersionFailsClosed();
        selfDependencyFailsClosed();
        duplicateDeclaredIdsFailClosed();
        inputCollectionsAreDefensivelyCopied();
        duplicatePublishIsRejectedWithoutPartialMutation();
        crossDomainDuplicateIdFailsClosed();
        missingDependencyFailsClosed();
        satisfiedDependencyPublishes();
        providerMismatchFailsClosed();
        declarationMismatchFailsClosed();
        missingPermissionReferenceFailsClosed();
        missingCapabilityReferenceFailsClosed();
        missingEventReferenceFailsClosed();
        snapshotsAreImmutable();
        oldSnapshotRemainsStableAfterLaterPublication();
        concurrentPublicationsRemainConsistent();
        concurrentDuplicatePublicationCommitsExactlyOnce();
        System.out.println("PUBLIC_RUNTIME_CONTRACT_TESTS = PASS");
        System.out.println("TEST_CASES=19");
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

    private static void invalidVersionFailsClosed() {
        expectCode("CONTRACT_INVALID", () -> new Contracts.ToolContract(
                "tool.version.bad",
                "latest",
                "1.0.0",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "entry.version.bad"
        ));
    }

    private static void selfDependencyFailsClosed() {
        expectCode("CONTRACT_INVALID", () -> new Contracts.ToolContract(
                "tool.self.dependency",
                "1.0.0",
                "1.0.0",
                List.of("tool.self.dependency"),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "entry.self.dependency"
        ));
    }

    private static void duplicateDeclaredIdsFailClosed() {
        expectCode("DUPLICATE_ID", () -> new Contracts.ToolContract(
                "tool.duplicate.declaration",
                "1.0.0",
                "1.0.0",
                Collections.emptyList(),
                List.of("component.duplicate", "component.duplicate"),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "entry.duplicate.declaration"
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

    private static void crossDomainDuplicateIdFailsClosed() {
        String sharedId = "resource.shared.id";
        String toolId = "tool.cross.domain";
        Contracts.ComponentContract component = new Contracts.ComponentContract(
                sharedId,
                "1.0.0",
                "1.0.0",
                toolId,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "implementation.cross.domain"
        );
        Contracts.ActionContract action = new Contracts.ActionContract(
                sharedId,
                "1.0.0",
                "1.0.0",
                toolId,
                "schema.cross.input",
                "schema.cross.output",
                Collections.emptyList(),
                Collections.emptyList(),
                "execution.sync",
                "async.none",
                "timeout.none",
                "cancellation.none",
                "idempotency.none"
        );
        Contracts.ToolContract tool = new Contracts.ToolContract(
                toolId,
                "1.0.0",
                "1.0.0",
                Collections.emptyList(),
                List.of(sharedId),
                List.of(sharedId),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "entry.cross.domain"
        );
        Contracts.ToolBundle bundle = new Contracts.ToolBundle(
                tool,
                List.of(component),
                List.of(action),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        );
        ProductRegistry registry = new ProductRegistry();
        expectCode("DUPLICATE_ID", () -> registry.publish(bundle));
        check(registry.snapshot().totalEntries() == 0, "cross-domain duplicate must leave registry untouched");
    }

    private static void missingDependencyFailsClosed() {
        ProductRegistry registry = new ProductRegistry();
        expectCode("DEPENDENCY_MISSING", () -> registry.publish(
                emptyBundle("tool.dependent", List.of("tool.missing"))
        ));
        check(registry.snapshot().totalEntries() == 0, "missing dependency must leave empty registry");
    }

    private static void satisfiedDependencyPublishes() {
        ProductRegistry registry = new ProductRegistry();
        registry.publish(emptyBundle("tool.dependency.root", Collections.emptyList()));
        registry.publish(emptyBundle("tool.dependency.child", List.of("tool.dependency.root")));
        check(registry.snapshot().tools().size() == 2, "satisfied dependency should publish");
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
        check(registry.snapshot().totalEntries() == 0, "declaration mismatch must leave registry untouched");
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

    private static void missingCapabilityReferenceFailsClosed() {
        Contracts.ActionContract action = new Contracts.ActionContract(
                "action.missing.capability",
                "1.0.0",
                "1.0.0",
                "tool.capability.case",
                "schema.capability.input",
                "schema.capability.output",
                List.of("capability.not.published"),
                Collections.emptyList(),
                "execution.sync",
                "async.none",
                "timeout.none",
                "cancellation.none",
                "idempotency.none"
        );
        Contracts.ToolContract tool = new Contracts.ToolContract(
                "tool.capability.case",
                "1.0.0",
                "1.0.0",
                Collections.emptyList(),
                Collections.emptyList(),
                List.of(action.id()),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "entry.capability.case"
        );
        ProductRegistry registry = new ProductRegistry();
        expectCode("CAPABILITY_UNAVAILABLE", () -> registry.publish(new Contracts.ToolBundle(
                tool,
                Collections.emptyList(),
                List.of(action),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        )));
        check(registry.snapshot().totalEntries() == 0, "missing capability must leave registry untouched");
    }

    private static void missingEventReferenceFailsClosed() {
        String toolId = "tool.event.case";
        Contracts.ComponentContract component = new Contracts.ComponentContract(
                "component.missing.event",
                "1.0.0",
                "1.0.0",
                toolId,
                Collections.emptyList(),
                List.of("event.not.published"),
                Collections.emptyList(),
                Collections.emptyList(),
                "implementation.event.case"
        );
        Contracts.ToolContract tool = new Contracts.ToolContract(
                toolId,
                "1.0.0",
                "1.0.0",
                Collections.emptyList(),
                List.of(component.id()),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "entry.event.case"
        );
        ProductRegistry registry = new ProductRegistry();
        expectCode("CONTRACT_INVALID", () -> registry.publish(new Contracts.ToolBundle(
                tool,
                List.of(component),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        )));
        check(registry.snapshot().totalEntries() == 0, "missing event must leave registry untouched");
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

    private static void oldSnapshotRemainsStableAfterLaterPublication() {
        ProductRegistry registry = new ProductRegistry();
        ProductRegistry.RegistrySnapshot first = registry.publish(emptyBundle("tool.snapshot.first", Collections.emptyList()));
        registry.publish(emptyBundle("tool.snapshot.second", Collections.emptyList()));
        check(first.tools().size() == 1, "previous snapshot must remain point-in-time stable");
        check(registry.snapshot().tools().size() == 2, "current registry should contain later publication");
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

    private static void concurrentDuplicatePublicationCommitsExactlyOnce() throws Exception {
        final int workers = 16;
        ProductRegistry registry = new ProductRegistry();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger duplicateFailures = new AtomicInteger();
        AtomicReference<Throwable> unexpected = new AtomicReference<>();
        for (int i = 0; i < workers; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    registry.publish(emptyBundle("tool.concurrent.same", Collections.emptyList()));
                    successes.incrementAndGet();
                } catch (Contracts.ContractException expected) {
                    if ("DUPLICATE_ID".equals(expected.code())) duplicateFailures.incrementAndGet();
                    else unexpected.compareAndSet(null, expected);
                } catch (Throwable error) {
                    unexpected.compareAndSet(null, error);
                }
            });
        }
        start.countDown();
        pool.shutdown();
        check(pool.awaitTermination(20, TimeUnit.SECONDS), "concurrent duplicate test timeout");
        if (unexpected.get() != null) throw new AssertionError("unexpected concurrent duplicate failure", unexpected.get());
        check(successes.get() == 1, "exactly one duplicate publication must commit");
        check(duplicateFailures.get() == workers - 1, "all losing duplicate publications must fail closed");
        check(registry.snapshot().tools().size() == 1, "duplicate race must leave one committed tool");
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
