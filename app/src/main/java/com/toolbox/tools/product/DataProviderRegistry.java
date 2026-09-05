package com.toolbox.tools.product;

import com.toolbox.tools.core.StableId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DataProviderRegistry {
    public enum Kind { DATABASE, API, FILE, FORM, RUNTIME, ACTION }
    public enum Capability { READ, WRITE, PAGE, STREAM, TRANSACTION }

    public static final class Provider {
        private final String id;
        private final Kind kind;
        private final Set<Capability> capabilities;

        Provider(String id, Kind kind, Set<Capability> capabilities) {
            this.id = id;
            this.kind = kind;
            this.capabilities = Collections.unmodifiableSet(
                    EnumSet.copyOf(capabilities)
            );
        }

        public String id() { return id; }
        public Kind kind() { return kind; }
        public Set<Capability> capabilities() { return capabilities; }
    }

    public static final class Window {
        private final int first;
        private final int last;
        private final int total;
        private final int preload;

        Window(int first, int last, int total, int preload) {
            this.first = first;
            this.last = last;
            this.total = total;
            this.preload = preload;
        }

        public int first() { return first; }
        public int last() { return last; }
        public int total() { return total; }
        public int preload() { return preload; }
        public int materializedCount() {
            return total == 0 ? 0 : last - first + 1;
        }
    }

    private final Map<String, Provider> providers = new LinkedHashMap<>();

    public DataProviderRegistry() {
        register(
                "provider.database",
                Kind.DATABASE,
                EnumSet.of(
                        Capability.READ,
                        Capability.WRITE,
                        Capability.PAGE,
                        Capability.TRANSACTION
                )
        );
        register(
                "provider.api",
                Kind.API,
                EnumSet.of(
                        Capability.READ,
                        Capability.WRITE,
                        Capability.PAGE,
                        Capability.STREAM
                )
        );
        register(
                "provider.file",
                Kind.FILE,
                EnumSet.of(
                        Capability.READ,
                        Capability.WRITE,
                        Capability.STREAM
                )
        );
        register(
                "provider.form",
                Kind.FORM,
                EnumSet.of(Capability.READ, Capability.WRITE)
        );
        register(
                "provider.runtime",
                Kind.RUNTIME,
                EnumSet.of(Capability.READ, Capability.STREAM)
        );
        register(
                "provider.action",
                Kind.ACTION,
                EnumSet.of(Capability.WRITE)
        );
    }

    public synchronized void register(
            String id,
            Kind kind,
            Set<Capability> capabilities
    ) {
        String stable = StableId.require(id, "providerId");
        if (kind == null || capabilities == null || capabilities.isEmpty()) {
            throw new IllegalArgumentException("provider contract incomplete");
        }
        if (providers.containsKey(stable)) {
            throw new IllegalArgumentException("provider duplicate");
        }
        providers.put(
                stable,
                new Provider(stable, kind, capabilities)
        );
    }

    public synchronized Provider require(String id) {
        Provider provider = providers.get(
                StableId.require(id, "providerId")
        );
        if (provider == null) {
            throw new IllegalArgumentException("provider unavailable");
        }
        return provider;
    }

    public synchronized List<Provider> byKind(Kind kind) {
        List<Provider> out = new ArrayList<>();
        for (Provider provider : providers.values()) {
            if (provider.kind() == kind) out.add(provider);
        }
        return Collections.unmodifiableList(out);
    }

    public synchronized boolean complete() {
        for (Kind kind : Kind.values()) {
            if (byKind(kind).isEmpty()) return false;
        }
        return true;
    }

    public Window window(
            int total,
            int firstVisible,
            int visibleCount,
            int preload
    ) {
        if (total < 0
                || firstVisible < 0
                || visibleCount < 1
                || preload < 0) {
            throw new IllegalArgumentException("viewport invalid");
        }
        if (total == 0) return new Window(0, -1, 0, preload);
        int first = Math.max(0, firstVisible - preload);
        int last = Math.min(
                total - 1,
                firstVisible + visibleCount + preload - 1
        );
        return new Window(first, last, total, preload);
    }
}
