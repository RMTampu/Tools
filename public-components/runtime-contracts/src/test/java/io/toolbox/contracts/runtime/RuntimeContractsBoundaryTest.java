package io.toolbox.contracts.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RuntimeContractsBoundaryTest {
    private RuntimeContractsBoundaryTest() {}

    public static void main(String[] args) {
        oversizedStableIdFailsClosed();
        oversizedCollectionFailsClosed();
        nullBundleStructureFailsClosed();
        registryCapacityFailsClosedWithoutMutation();
        exactLookupDoesNotAliasSimilarId();
        System.out.println("PUBLIC_RUNTIME_CONTRACT_BOUNDARY_TESTS = PASS");
        System.out.println("BOUNDARY_TEST_CASES=5");
    }

    private static void oversizedStableIdFailsClosed() {
        String oversized = "tool." + "a".repeat(Contracts.MAX_STABLE_ID_LENGTH);
        expectCode("RESOURCE_LIMIT", () -> Contracts.requireStableId(oversized, "toolId"));
    }

    private static void oversizedCollectionFailsClosed() {
        ArrayList<String> dependencies = new ArrayList<>();
        for (int i = 0; i <= Contracts.MAX_COLLECTION_SIZE; i++) {
            dependencies.add("tool.dependency.t" + i);
        }
        expectCode("RESOURCE_LIMIT", () -> new Contracts.ToolContract(
                "tool.collection.boundary",
                "1.0.0",
                "1.0.0",
                dependencies,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "entry.collection.boundary"
        ));
    }

    private static void nullBundleStructureFailsClosed() {
        expectCode("CONTRACT_INVALID", () -> new Contracts.ToolBundle(
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        ));
    }

    private static void registryCapacityFailsClosedWithoutMutation() {
        ProductRegistry registry = new ProductRegistry();
        for (int i = 0; i < 15; i++) {
            registry.publish(componentBundle("tool.capacity.t" + i, "capacity.t" + i, 256));
        }
        int before = registry.snapshot().totalEntries();
        check(before == 3855, "capacity fixture expected 3855 entries before overflow challenge");
        expectCode("RESOURCE_LIMIT", () -> registry.publish(
                componentBundle("tool.capacity.overflow", "capacity.overflow", 241)
        ));
        check(registry.snapshot().totalEntries() == before, "capacity rejection must not mutate registry");
    }

    private static void exactLookupDoesNotAliasSimilarId() {
        ProductRegistry registry = new ProductRegistry();
        registry.publish(emptyBundle("tool.lookup.exact"));
        check(registry.tool("tool.lookup.exact").isPresent(), "exact ID must resolve");
        check(registry.tool("tool.lookup").isEmpty(), "prefix ID must not resolve as alias");
    }

    private static Contracts.ToolBundle componentBundle(String toolId, String namespace, int componentCount) {
        ArrayList<Contracts.ComponentContract> components = new ArrayList<>();
        ArrayList<String> componentIds = new ArrayList<>();
        for (int i = 0; i < componentCount; i++) {
            String id = "component." + namespace + ".c" + i;
            componentIds.add(id);
            components.add(new Contracts.ComponentContract(
                    id,
                    "1.0.0",
                    "1.0.0",
                    toolId,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    "implementation." + namespace + ".c" + i
            ));
        }
        Contracts.ToolContract tool = new Contracts.ToolContract(
                toolId,
                "1.0.0",
                "1.0.0",
                Collections.emptyList(),
                componentIds,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "entry." + namespace
        );
        return new Contracts.ToolBundle(
                tool,
                components,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        );
    }

    private static Contracts.ToolBundle emptyBundle(String toolId) {
        Contracts.ToolContract tool = new Contracts.ToolContract(
                toolId,
                "1.0.0",
                "1.0.0",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "entry.lookup.exact"
        );
        return new Contracts.ToolBundle(
                tool,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
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
