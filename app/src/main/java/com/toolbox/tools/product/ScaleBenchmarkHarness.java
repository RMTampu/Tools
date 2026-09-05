package com.toolbox.tools.product;

import com.toolbox.tools.core.ProjectCodec;
import com.toolbox.tools.core.ProjectLifecycle;
import com.toolbox.tools.core.ProjectState;
import com.toolbox.tools.core.ProjectValidationResult;
import com.toolbox.tools.core.ProjectValidator;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class ScaleBenchmarkHarness {
    public enum ScaleClass { SMALL, MEDIUM, LARGE, STRESS }

    public static final class Profile {
        private final int screens;
        private final int objects;
        private final int bindings;
        private final int assets;
        private final long decodedAssetBytes;
        private final int logicNodes;
        private final int components;
        private final int dependencies;
        private final int viewportObjects;

        Profile(
                int screens,
                int objects,
                int bindings,
                int assets,
                long decodedAssetBytes,
                int logicNodes,
                int components,
                int dependencies,
                int viewportObjects
        ) {
            this.screens=screens;
            this.objects=objects;
            this.bindings=bindings;
            this.assets=assets;
            this.decodedAssetBytes=decodedAssetBytes;
            this.logicNodes=logicNodes;
            this.components=components;
            this.dependencies=dependencies;
            this.viewportObjects=viewportObjects;
        }

        public int screens(){return screens;}
        public int objects(){return objects;}
        public int bindings(){return bindings;}
        public int assets(){return assets;}
        public long decodedAssetBytes(){return decodedAssetBytes;}
        public int logicNodes(){return logicNodes;}
        public int components(){return components;}
        public int dependencies(){return dependencies;}
        public int viewportObjects(){return viewportObjects;}
    }

    public static final class Result {
        private final ScaleClass scaleClass;
        private final boolean withinBudget;
        private final long estimatedWorkingBytes;
        private final int visibleNodes;
        private final long encodedProjectBytes;
        private final int resourceCount;
        private final int referenceCount;
        private final int dependencyCount;
        private final ResourceGuard.Pressure pressure;
        private final boolean roundTripEqual;

        Result(
                ScaleClass scaleClass,
                boolean withinBudget,
                long estimatedWorkingBytes,
                int visibleNodes,
                long encodedProjectBytes,
                int resourceCount,
                int referenceCount,
                int dependencyCount,
                ResourceGuard.Pressure pressure,
                boolean roundTripEqual
        ) {
            this.scaleClass = scaleClass;
            this.withinBudget = withinBudget;
            this.estimatedWorkingBytes = estimatedWorkingBytes;
            this.visibleNodes = visibleNodes;
            this.encodedProjectBytes = encodedProjectBytes;
            this.resourceCount = resourceCount;
            this.referenceCount = referenceCount;
            this.dependencyCount = dependencyCount;
            this.pressure = pressure;
            this.roundTripEqual = roundTripEqual;
        }

        public ScaleClass scaleClass() { return scaleClass; }
        public boolean withinBudget() { return withinBudget; }
        public long estimatedWorkingBytes() { return estimatedWorkingBytes; }
        public int visibleNodes() { return visibleNodes; }
        public long encodedProjectBytes() { return encodedProjectBytes; }
        public int resourceCount() { return resourceCount; }
        public int referenceCount() { return referenceCount; }
        public int dependencyCount() { return dependencyCount; }
        public ResourceGuard.Pressure pressure() { return pressure; }
        public boolean roundTripEqual() { return roundTripEqual; }
    }

    public Profile profile(ScaleClass scale) {
        if (scale == null) throw new NullPointerException("scale");
        switch (scale) {
            case SMALL:
                return new Profile(
                        2, 80, 30, 10,
                        2L * 1024L * 1024L,
                        30, 10, 8, 40
                );
            case MEDIUM:
                return new Profile(
                        8, 600, 300, 60,
                        8L * 1024L * 1024L,
                        180, 40, 64, 100
                );
            case LARGE:
                return new Profile(
                        24, 2200, 1200, 240,
                        24L * 1024L * 1024L,
                        600, 120, 256, 160
                );
            case STRESS:
            default:
                return new Profile(
                        32, 2800, 1600, 300,
                        64L * 1024L * 1024L,
                        700, 140, 512, 180
                );
        }
    }

    /**
     * Materializes a real ProjectState with screens, objects, bindings,
     * assets, logic graph nodes, components and dependency graph entries.
     * The dataset is then validated, encoded, decoded and pushed through
     * viewport/memory-pressure policy. This is deliberately not a
     * formula-only maximum estimate.
     */
    public Result runActual(
            ScaleClass scale,
            long budgetBytes
    ) {
        if (budgetBytes < 32L * 1024L * 1024L) {
            throw new IllegalArgumentException("budget terlalu kecil");
        }
        Profile p = profile(scale);

        LinkedHashMap<String,String> resources =
                new LinkedHashMap<>();
        LinkedHashMap<String,Set<String>> references =
                new LinkedHashMap<>();
        LinkedHashSet<String> dependencies =
                new LinkedHashSet<>();

        for (int i=0;i<p.screens();i++) {
            resources.put(
                    "ui.screen.scale_" + i,
                    "container=FREE|orientation=adaptive"
            );
        }
        for (int i=0;i<p.objects();i++) {
            resources.put(
                    "ui.object.scale_" + i,
                    "type=button|screen="
                            + (i % p.screens())
                            + "|text=Objek "
                            + i
            );
        }
        for (int i=0;i<p.assets();i++) {
            resources.put(
                    "asset.scale_" + i,
                    "kind=IMAGE|sha="
                            + fixedHex(i)
            );
        }
        for (int i=0;i<p.logicNodes();i++) {
            resources.put(
                    "logic.node.scale_" + i,
                    i % 3 == 0
                            ? "type=EVENT"
                            : i % 3 == 1
                            ? "type=BRANCH"
                            : "type=ACTION"
            );
            if (i + 1 < p.logicNodes()) {
                references.put(
                        "logic.node.scale_" + i,
                        singleton(
                                "logic.node.scale_" + (i + 1)
                        )
                );
            }
        }
        for (int i=0;i<p.components();i++) {
            resources.put(
                    "component.scale_" + i,
                    "version=1|category=scale"
            );
        }

        for (int i=0;i<p.bindings();i++) {
            String source =
                    "ui.object.scale_" + (i % p.objects());
            String target =
                    "ui.object.scale_"
                            + ((i + 1) % p.objects());
            references.computeIfAbsent(
                    source,
                    ignored -> new LinkedHashSet<>()
            ).add(target);
        }

        for (int i=0;i<p.dependencies();i++) {
            dependencies.add("dependency.scale_" + i);
        }

        ProjectState project = ProjectState.restore(
                "project.scale." + scale.name().toLowerCase(),
                ProjectState.CURRENT_SCHEMA_VERSION,
                ProjectState.CURRENT_BUILD_MODEL_VERSION,
                1,
                ProjectLifecycle.ACTIVE,
                resources,
                references,
                dependencies
        );
        ProjectValidationResult validation =
                new ProjectValidator().validate(project);
        if (!validation.isPass()) {
            throw new IllegalStateException(
                    "scale project validation failed:"
                            + validation.message()
            );
        }

        ProjectCodec codec = new ProjectCodec();
        String encoded = codec.encode(project);
        ProjectState decoded = codec.decode(encoded);
        boolean roundTrip = project.equals(decoded);

        ResourceGuard guard = new ResourceGuard();
        guard.setMemoryBudgetBytes(budgetBytes);
        guard.configureScreen(
                "screen.scale",
                Math.min(
                        budgetBytes,
                        96L * 1024L * 1024L
                ),
                180,
                4
        );
        guard.enterScreen("screen.scale");

        long modelBytes =
                encoded.getBytes(StandardCharsets.UTF_8).length;
        long working =
                modelBytes
                        + p.decodedAssetBytes()
                        + p.viewportObjects() * 2048L;
        ResourceGuard.Pressure pressure = guard.sample(
                "screen.scale",
                working,
                p.viewportObjects(),
                Math.min(4, p.assets())
        );
        guard.applyPressure(pressure);

        boolean withinBudget =
                working <= budgetBytes
                        && roundTrip
                        && guard.invariantPass()
                        && project.resources().size()
                            <= ProjectState.MAX_RESOURCES
                        && countReferences(references)
                            <= ProjectState.MAX_REFERENCES
                        && dependencies.size()
                            <= ProjectState.MAX_DEPENDENCIES;

        return new Result(
                scale,
                withinBudget,
                working,
                p.viewportObjects(),
                modelBytes,
                project.resources().size(),
                countReferences(references),
                project.dependencyRefs().size(),
                pressure,
                roundTrip
        );
    }

    /**
     * Compatibility helper retained for callers that need a quick estimate.
     */
    public Result estimate(
            ScaleClass scale,
            int totalObjects,
            int visibleObjects,
            long decodedAssetBytes,
            long budgetBytes
    ) {
        if (totalObjects < 0
                || visibleObjects < 0
                || visibleObjects > totalObjects) {
            throw new IllegalArgumentException(
                    "jumlah objek tidak valid"
            );
        }
        if (decodedAssetBytes < 0 || budgetBytes <= 0) {
            throw new IllegalArgumentException("budget tidak valid");
        }
        long model = Math.min(totalObjects, 20_000) * 160L;
        long renderer = visibleObjects * 2048L;
        long estimated = model + renderer + decodedAssetBytes;
        return new Result(
                scale,
                estimated <= budgetBytes,
                estimated,
                visibleObjects,
                0,
                totalObjects,
                0,
                0,
                estimated > budgetBytes
                        ? ResourceGuard.Pressure.CRITICAL
                        : ResourceGuard.Pressure.NORMAL,
                true
        );
    }

    private static int countReferences(
            Map<String,Set<String>> references
    ) {
        int total = 0;
        for (Set<String> value : references.values()) {
            total += value.size();
        }
        return total;
    }

    private static Set<String> singleton(String value) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        out.add(value);
        return out;
    }

    private static String fixedHex(int value) {
        String unit = String.format(
                java.util.Locale.ROOT,
                "%08x",
                value
        );
        StringBuilder out = new StringBuilder();
        while (out.length() < 64) out.append(unit);
        return out.substring(0, 64);
    }
}
