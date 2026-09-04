package com.toolbox.tools.library;

import com.toolbox.tools.core.StableId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class TemplateDefinition {
    private final String templateId;
    private final String labelIndonesia;
    private final VersionNumber version;
    private final CatalogLifecycle lifecycle;
    private final Set<String> internalObjectIds;
    private final List<DependencyRef> componentDependencies;
    private final List<AssetDependencyRef> assetDependencies;

    public TemplateDefinition(
            String templateId,
            String labelIndonesia,
            VersionNumber version,
            CatalogLifecycle lifecycle,
            Set<String> internalObjectIds,
            List<DependencyRef> componentDependencies,
            List<AssetDependencyRef> assetDependencies
    ) {
        this.templateId = StableId.require(templateId, "templateId");
        this.labelIndonesia = requireLabel(labelIndonesia);
        this.version = Objects.requireNonNull(version, "version");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");

        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (internalObjectIds != null) {
            for (String id : internalObjectIds) {
                ids.add(StableId.require(id, "internalObjectId"));
            }
        }
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("template requires internal objects");
        }
        this.internalObjectIds = Collections.unmodifiableSet(ids);
        this.componentDependencies = Collections.unmodifiableList(
                componentDependencies == null
                        ? new ArrayList<>()
                        : new ArrayList<>(componentDependencies)
        );
        this.assetDependencies = Collections.unmodifiableList(
                assetDependencies == null
                        ? new ArrayList<>()
                        : new ArrayList<>(assetDependencies)
        );
    }

    private static String requireLabel(String value) {
        Objects.requireNonNull(value, "labelIndonesia");
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > 120) {
            throw new IllegalArgumentException("template label invalid");
        }
        return trimmed;
    }

    public String templateId() { return templateId; }
    public String labelIndonesia() { return labelIndonesia; }
    public VersionNumber version() { return version; }
    public CatalogLifecycle lifecycle() { return lifecycle; }
    public Set<String> internalObjectIds() { return internalObjectIds; }
    public List<DependencyRef> componentDependencies() { return componentDependencies; }
    public List<AssetDependencyRef> assetDependencies() { return assetDependencies; }

    public TemplateDefinition withLifecycle(CatalogLifecycle next) {
        return new TemplateDefinition(
                templateId,
                labelIndonesia,
                version,
                next,
                internalObjectIds,
                componentDependencies,
                assetDependencies
        );
    }
}
