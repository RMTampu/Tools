package com.toolbox.tools.product;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AutoRepairEngine {
    public enum RepairType {
        REBUILD_DERIVED_INDEX,
        REBUILD_DEPENDENCY_GRAPH,
        CLEAR_DISPOSABLE_CACHE,
        REMAP_EXACT_ID_CONFLICT,
        RELINK_EXACT_STABLE_ID,
        REGENERATE_DERIVED_MANIFEST
    }

    public static final class RepairResult {
        private final List<RepairType> applied;
        private final List<String> rejected;

        RepairResult(List<RepairType> applied, List<String> rejected) {
            this.applied = Collections.unmodifiableList(applied);
            this.rejected = Collections.unmodifiableList(rejected);
        }

        public List<RepairType> applied() { return applied; }
        public List<String> rejected() { return rejected; }
        public boolean isPass() { return rejected.isEmpty(); }
    }

    public RepairResult applyDeterministic(List<RepairType> requested) {
        List<RepairType> applied = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        if (requested == null) return new RepairResult(applied, rejected);
        for (RepairType type : requested) {
            if (type == null) {
                rejected.add("REPAIR_TYPE_NULL");
            } else {
                applied.add(type);
            }
        }
        return new RepairResult(applied, rejected);
    }

    public boolean mayGuessBusinessLogic() { return false; }
    public boolean mayDeleteUserData() { return false; }
}
