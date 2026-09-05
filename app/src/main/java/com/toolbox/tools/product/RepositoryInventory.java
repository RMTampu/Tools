package com.toolbox.tools.product;

import com.toolbox.tools.core.StableId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RepositoryInventory {
    public enum Type { COMPONENT, CAPABILITY, ACTION, ASSET, PERMISSION, ENGINE, IMPLEMENTATION }

    public static final class Entry {
        private final String id;
        private final Type type;
        private final String implementation;
        private final String owner;

        Entry(String id, Type type, String implementation, String owner) {
            this.id = id;
            this.type = type;
            this.implementation = implementation;
            this.owner = owner;
        }

        public String id() { return id; }
        public Type type() { return type; }
        public String implementation() { return implementation; }
        public String owner() { return owner; }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public RepositoryInventory() {
        register("component.button", Type.COMPONENT, "ComponentDefinition", "tool.ui");
        register("component.text", Type.COMPONENT, "ComponentDefinition", "tool.ui");
        register("capability.ui.edit", Type.CAPABILITY, "VisualEditorSession", "tool.ui");
        register("capability.logic.flow", Type.CAPABILITY, "FlowGraph", "tool.logic");
        register("capability.data.provider", Type.CAPABILITY, "RuntimeEnvironment", "tool.data");
        register("capability.binding", Type.CAPABILITY, "BindingRuntime", "tool.binding");
        register("capability.asset.import", Type.CAPABILITY, "AssetStore", "tool.asset");
        register("action.navigate", Type.ACTION, "NavigationManager", "tool.logic");
        register("action.binding.write", Type.ACTION, "BindingRuntime", "tool.binding");
        register("asset.theme.dark.neon", Type.ASSET, "BuiltinAssetCatalog", "tool.asset");
        register("asset.external.media", Type.ASSET, "ExternalAssetGateway", "tool.asset");
        register("permission.storage.tree", Type.PERMISSION, "SafProjectAccessGateway", "foundation");
        register("permission.external.asset", Type.PERMISSION, "ExternalAssetGateway", "foundation");
        register("engine.ui", Type.ENGINE, "UiToolEngine", "tool.ui");
        register("engine.logic", Type.ENGINE, "LogicToolEngine", "tool.logic");
        register("engine.data", Type.ENGINE, "DataToolEngine", "tool.data");
        register("engine.binding", Type.ENGINE, "BindingToolEngine", "tool.binding");
        register("engine.asset", Type.ENGINE, "AssetToolEngine", "tool.asset");
        register("implementation.project.store", Type.IMPLEMENTATION, "SafProjectStore", "foundation");
        register("implementation.freeze", Type.IMPLEMENTATION, "FreezeEngine", "foundation");
        register("implementation.evolution", Type.IMPLEMENTATION, "EvolutionManager", "foundation");
        register("implementation.safe.mode", Type.IMPLEMENTATION, "SafeModeController", "foundation");
    }

    public synchronized void register(
            String id,
            Type type,
            String implementation,
            String owner
    ) {
        String stable = StableId.require(id, "inventoryId");
        if (entries.containsKey(stable)) throw new IllegalArgumentException("inventory duplicate");
        if (type == null || implementation == null || implementation.trim().isEmpty()
                || owner == null || owner.trim().isEmpty()) {
            throw new IllegalArgumentException("inventory incomplete");
        }
        entries.put(stable, new Entry(stable, type, implementation.trim(), owner.trim()));
    }

    public synchronized Entry require(String id) {
        Entry e = entries.get(StableId.require(id, "inventoryId"));
        if (e == null) throw new IllegalArgumentException("inventory entry unavailable");
        return e;
    }

    public synchronized List<Entry> byType(Type type) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries.values()) if (e.type == type) out.add(e);
        return Collections.unmodifiableList(out);
    }

    public synchronized Set<Type> coveredTypes() {
        LinkedHashSet<Type> out = new LinkedHashSet<>();
        for (Entry e : entries.values()) out.add(e.type);
        return Collections.unmodifiableSet(out);
    }

    public synchronized boolean complete() {
        return coveredTypes().containsAll(java.util.Arrays.asList(Type.values()))
                && entries.size() >= 20;
    }

    public synchronized Map<String,String> machineReadable() {
        LinkedHashMap<String,String> out = new LinkedHashMap<>();
        for (Entry e : entries.values()) {
            out.put(e.id, e.type.name()+"|"+e.implementation+"|"+e.owner);
        }
        return Collections.unmodifiableMap(out);
    }
}
