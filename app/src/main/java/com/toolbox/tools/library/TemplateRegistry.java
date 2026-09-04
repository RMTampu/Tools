package com.toolbox.tools.library;

import com.toolbox.tools.core.StableId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public final class TemplateRegistry {
    private final Map<String, NavigableMap<VersionNumber, TemplateDefinition>> definitions =
            new LinkedHashMap<>();

    public synchronized void publishReady(
            TemplateDefinition template,
            DependencyResolver resolver,
            ComponentRegistry components,
            AssetRegistry assets
    ) {
        if (template.lifecycle() == CatalogLifecycle.ARCHIVED) {
            throw new IllegalArgumentException("archived template cannot become READY");
        }
        DependencyResolutionResult result =
                resolver.resolveTemplate(template, components, assets);
        if (!result.isPass()) {
            throw new IllegalArgumentException(
                    "template dependency validation failed: " + result.message()
            );
        }
        register(template.withLifecycle(CatalogLifecycle.READY));
    }

    public synchronized void register(TemplateDefinition template) {
        String id = StableId.require(template.templateId(), "templateId");
        NavigableMap<VersionNumber, TemplateDefinition> versions =
                definitions.computeIfAbsent(id, ignored -> new TreeMap<>());
        if (versions.containsKey(template.version())) {
            throw new IllegalArgumentException("template version already registered");
        }
        versions.put(template.version(), template);
    }

    public synchronized TemplateDefinition resolveExact(
            String templateId,
            VersionNumber version
    ) {
        NavigableMap<VersionNumber, TemplateDefinition> versions =
                definitions.get(StableId.require(templateId, "templateId"));
        return versions == null ? null : versions.get(version);
    }

    public synchronized List<TemplateDefinition> allReady() {
        List<TemplateDefinition> out = new ArrayList<>();
        for (NavigableMap<VersionNumber, TemplateDefinition> versions : definitions.values()) {
            for (TemplateDefinition template : versions.values()) {
                if (template.lifecycle() == CatalogLifecycle.READY) out.add(template);
            }
        }
        out.sort(Comparator.comparing(TemplateDefinition::templateId)
                .thenComparing(TemplateDefinition::version));
        return Collections.unmodifiableList(out);
    }
}
