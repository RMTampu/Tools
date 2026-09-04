package com.toolbox.tools.authoring;

import com.toolbox.tools.core.StableId;
import com.toolbox.tools.library.AssetDependencyRef;
import com.toolbox.tools.library.DependencyRef;
import com.toolbox.tools.library.VersionNumber;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class TemplateAuthoringDraft {
    private final String draftId;
    private final String templateId;
    private final String labelIndonesia;
    private final VersionNumber version;
    private final Set<String> internalObjectIds;
    private final List<DependencyRef> componentDependencies;
    private final List<AssetDependencyRef> assetDependencies;

    public TemplateAuthoringDraft(
            String draftId,
            String templateId,
            String labelIndonesia,
            VersionNumber version,
            Set<String> internalObjectIds,
            List<DependencyRef> componentDependencies,
            List<AssetDependencyRef> assetDependencies
    ) {
        this.draftId = StableId.require(draftId, "draftId");
        this.templateId = StableId.require(templateId, "templateId");
        String label = Objects.requireNonNull(labelIndonesia, "labelIndonesia").trim();
        if (label.isEmpty() || label.length() > 120) {
            throw new IllegalArgumentException("template label invalid");
        }
        this.labelIndonesia = label;
        this.version = Objects.requireNonNull(version, "version");
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

    public String draftId() { return draftId; }
    public String templateId() { return templateId; }
    public String labelIndonesia() { return labelIndonesia; }
    public VersionNumber version() { return version; }
    public Set<String> internalObjectIds() { return internalObjectIds; }
    public List<DependencyRef> componentDependencies() { return componentDependencies; }
    public List<AssetDependencyRef> assetDependencies() { return assetDependencies; }
}
