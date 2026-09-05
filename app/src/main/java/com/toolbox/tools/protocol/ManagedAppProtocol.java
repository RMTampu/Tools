package com.toolbox.tools.protocol;

import com.toolbox.tools.core.StableId;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class ManagedAppProtocol {
    public static final int CURRENT_VERSION = 1;

    public enum Capability {
        UI, LOGIC, DATA, BINDING, ASSET
    }

    public enum RequestType {
        DESCRIBE,
        PREVIEW_PATCH,
        APPLY_PATCH,
        HEALTH,
        ROLLBACK
    }

    public static final class Descriptor {
        private final String packageName;
        private final int protocolVersion;
        private final Set<Capability> capabilities;
        private final String projectId;
        private final long revision;

        public Descriptor(
                String packageName,
                int protocolVersion,
                Set<Capability> capabilities,
                String projectId,
                long revision
        ) {
            if (packageName == null
                    || !packageName.matches(
                            "[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+"
                    )) {
                throw new IllegalArgumentException(
                        "package target tidak valid"
                );
            }
            if (protocolVersion < 1
                    || protocolVersion > CURRENT_VERSION) {
                throw new IllegalArgumentException(
                        "versi protocol tidak kompatibel"
                );
            }
            if (capabilities == null || capabilities.isEmpty()) {
                throw new IllegalArgumentException(
                        "target tanpa capability"
                );
            }
            this.packageName = packageName;
            this.protocolVersion = protocolVersion;
            this.capabilities = Collections.unmodifiableSet(
                    EnumSet.copyOf(capabilities)
            );
            this.projectId = StableId.require(
                    projectId,
                    "projectId"
            );
            if (revision < 0) {
                throw new IllegalArgumentException(
                        "revision target invalid"
                );
            }
            this.revision = revision;
        }

        public String packageName(){return packageName;}
        public int protocolVersion(){return protocolVersion;}
        public Set<Capability> capabilities(){return capabilities;}
        public String projectId(){return projectId;}
        public long revision(){return revision;}
    }

    public static final class Session {
        private final Descriptor target;
        private final Set<Capability> granted;
        private final String sessionId;

        Session(
                Descriptor target,
                Set<Capability> granted,
                String sessionId
        ) {
            this.target=target;
            this.granted=Collections.unmodifiableSet(
                    EnumSet.copyOf(granted)
            );
            this.sessionId=sessionId;
        }

        public Descriptor target(){return target;}
        public Set<Capability> granted(){return granted;}
        public String sessionId(){return sessionId;}

        public boolean can(RequestType type) {
            if (type == RequestType.DESCRIBE
                    || type == RequestType.HEALTH
                    || type == RequestType.ROLLBACK) {
                return true;
            }
            return !granted.isEmpty();
        }
    }

    public Session negotiate(
            Descriptor target,
            Set<Capability> requested
    ) {
        if (target == null
                || requested == null
                || requested.isEmpty()) {
            throw new IllegalArgumentException(
                    "negosiasi target tidak lengkap"
            );
        }
        EnumSet<Capability> granted =
                EnumSet.noneOf(Capability.class);
        for (Capability capability : requested) {
            if (target.capabilities().contains(capability)) {
                granted.add(capability);
            }
        }
        if (granted.isEmpty()) {
            throw new IllegalArgumentException(
                    "tidak ada capability yang disepakati"
            );
        }
        String sessionId = StableId.require(
                "session."
                        + target.packageName()
                        .toLowerCase(Locale.ROOT)
                        .replace('.', '_')
                        + "."
                        + target.revision(),
                "sessionId"
        );
        return new Session(target, granted, sessionId);
    }

    public Set<Capability> parseCapabilities(
            Iterable<String> values
    ) {
        LinkedHashSet<Capability> out =
                new LinkedHashSet<>();
        if (values == null) return out;
        for (String value : values) {
            if (value == null) continue;
            try {
                out.add(
                        Capability.valueOf(
                                value.trim()
                                        .toUpperCase(Locale.ROOT)
                        )
                );
            } catch (IllegalArgumentException ignored) {
                // Capability asing tidak dinegosiasikan.
            }
        }
        return Collections.unmodifiableSet(out);
    }
}
