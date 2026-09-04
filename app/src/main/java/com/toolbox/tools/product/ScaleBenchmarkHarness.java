package com.toolbox.tools.product;

public final class ScaleBenchmarkHarness {
    public enum ScaleClass { SMALL, MEDIUM, LARGE, STRESS }

    public static final class Result {
        private final ScaleClass scaleClass;
        private final boolean withinBudget;
        private final long estimatedWorkingBytes;
        private final int visibleNodes;

        Result(
                ScaleClass scaleClass,
                boolean withinBudget,
                long estimatedWorkingBytes,
                int visibleNodes
        ) {
            this.scaleClass = scaleClass;
            this.withinBudget = withinBudget;
            this.estimatedWorkingBytes = estimatedWorkingBytes;
            this.visibleNodes = visibleNodes;
        }

        public ScaleClass scaleClass() { return scaleClass; }
        public boolean withinBudget() { return withinBudget; }
        public long estimatedWorkingBytes() { return estimatedWorkingBytes; }
        public int visibleNodes() { return visibleNodes; }
    }

    public Result estimate(
            ScaleClass scale,
            int totalObjects,
            int visibleObjects,
            long decodedAssetBytes,
            long budgetBytes
    ) {
        if (totalObjects < 0 || visibleObjects < 0 || visibleObjects > totalObjects) {
            throw new IllegalArgumentException("jumlah objek tidak valid");
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
                visibleObjects
        );
    }
}
