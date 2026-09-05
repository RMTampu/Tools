package com.toolbox.tools.product;

import com.toolbox.tools.core.StableId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class PermissionManager {
    public enum Phase {
        INSTALL_TIME,
        RUNTIME,
        SPECIAL_ACCESS,
        OPTIONAL
    }

    public static final class PermissionSpec {
        private final String permissionId;
        private final String capabilityId;
        private final Phase phase;
        private final boolean required;
        private final String failurePathId;

        public PermissionSpec(
                String permissionId,
                String capabilityId,
                Phase phase,
                boolean required,
                String failurePathId
        ) {
            this.permissionId = StableId.require(
                    permissionId,
                    "permissionId"
            );
            this.capabilityId = StableId.require(
                    capabilityId,
                    "capabilityId"
            );
            this.phase = Objects.requireNonNull(phase, "phase");
            this.required = required;
            this.failurePathId = StableId.require(
                    failurePathId,
                    "failurePathId"
            );
        }

        public String permissionId() { return permissionId; }
        public String capabilityId() { return capabilityId; }
        public Phase phase() { return phase; }
        public boolean required() { return required; }
        public String failurePathId() { return failurePathId; }
    }

    public static final class Failure {
        private final String permissionId;
        private final String capabilityId;
        private final Phase phase;
        private final String failurePathId;

        Failure(PermissionSpec spec) {
            permissionId = spec.permissionId();
            capabilityId = spec.capabilityId();
            phase = spec.phase();
            failurePathId = spec.failurePathId();
        }

        public String permissionId() { return permissionId; }
        public String capabilityId() { return capabilityId; }
        public Phase phase() { return phase; }
        public String failurePathId() { return failurePathId; }
    }

    private final Map<String, PermissionSpec> specs =
            new LinkedHashMap<>();
    private final Set<String> activeCapabilities =
            new LinkedHashSet<>();
    private final Set<String> explicitlyRequired =
            new LinkedHashSet<>();
    private final Set<String> granted =
            new LinkedHashSet<>();

    public synchronized void register(PermissionSpec spec) {
        Objects.requireNonNull(spec, "spec");
        PermissionSpec previous = specs.put(
                spec.permissionId(),
                spec
        );
        if (previous != null
                && (!previous.capabilityId()
                        .equals(spec.capabilityId())
                    || previous.phase() != spec.phase()
                    || previous.required() != spec.required()
                    || !previous.failurePathId()
                        .equals(spec.failurePathId()))) {
            specs.put(previous.permissionId(), previous);
            throw new IllegalArgumentException(
                    "permission contract conflict"
            );
        }
    }

    public synchronized void activateCapability(
            String capabilityId,
            boolean active
    ) {
        String id = StableId.require(
                capabilityId,
                "capabilityId"
        );
        if (active) activeCapabilities.add(id);
        else activeCapabilities.remove(id);
    }

    /**
     * Legacy explicit-require API remains supported. If no full spec was
     * registered, a deterministic runtime contract is created instead of
     * leaving an untyped permission entry.
     */
    public synchronized void require(String permissionId) {
        String id = StableId.require(
                permissionId,
                "permissionId"
        );
        explicitlyRequired.add(id);
        if (!specs.containsKey(id)) {
            register(new PermissionSpec(
                    id,
                    "capability.explicit." + id.replace('.', '_'),
                    Phase.RUNTIME,
                    true,
                    "failure.permission." + id.replace('.', '_')
            ));
            activeCapabilities.add(
                    specs.get(id).capabilityId()
            );
        }
    }

    public synchronized void setGranted(
            String permissionId,
            boolean value
    ) {
        String id = StableId.require(
                permissionId,
                "permissionId"
        );
        if (!specs.containsKey(id)
                && !explicitlyRequired.contains(id)) {
            throw new IllegalArgumentException(
                    "permission tidak mempunyai contract"
            );
        }
        if (value) granted.add(id);
        else granted.remove(id);
    }

    public synchronized Set<String> required() {
        LinkedHashSet<String> out =
                new LinkedHashSet<>(explicitlyRequired);
        for (PermissionSpec spec : specs.values()) {
            if (spec.required()
                    && activeCapabilities.contains(
                            spec.capabilityId()
                    )) {
                out.add(spec.permissionId());
            }
        }
        return Collections.unmodifiableSet(out);
    }

    public synchronized Set<String> missing() {
        LinkedHashSet<String> out =
                new LinkedHashSet<>(required());
        out.removeAll(granted);
        return Collections.unmodifiableSet(out);
    }

    public synchronized List<Failure> failures() {
        List<Failure> out = new ArrayList<>();
        for (String permissionId : missing()) {
            PermissionSpec spec = specs.get(permissionId);
            if (spec == null) {
                throw new IllegalStateException(
                        "missing permission contract:"
                                + permissionId
                );
            }
            out.add(new Failure(spec));
        }
        return Collections.unmodifiableList(out);
    }

    public synchronized List<PermissionSpec> byPhase(
            Phase phase
    ) {
        Objects.requireNonNull(phase, "phase");
        List<PermissionSpec> out = new ArrayList<>();
        for (PermissionSpec spec : specs.values()) {
            if (spec.phase() == phase
                    && (activeCapabilities.contains(
                            spec.capabilityId()
                        )
                        || explicitlyRequired.contains(
                            spec.permissionId()
                        ))) {
                out.add(spec);
            }
        }
        return Collections.unmodifiableList(out);
    }

    public synchronized Set<String> activeCapabilities() {
        return Collections.unmodifiableSet(
                new LinkedHashSet<>(activeCapabilities)
        );
    }

    public synchronized List<PermissionSpec> specs() {
        return Collections.unmodifiableList(
                new ArrayList<>(specs.values())
        );
    }

    public synchronized boolean isReady() {
        return missing().isEmpty();
    }

    public synchronized boolean completeContract() {
        if (specs.isEmpty()) return false;
        for (PermissionSpec spec : specs.values()) {
            if (spec.permissionId() == null
                    || spec.capabilityId() == null
                    || spec.phase() == null
                    || spec.failurePathId() == null) {
                return false;
            }
        }
        for (String id : required()) {
            if (!specs.containsKey(id)) return false;
        }
        for (Failure failure : failures()) {
            if (failure.failurePathId() == null) return false;
        }
        return true;
    }
}
