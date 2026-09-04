package com.toolbox.tools.library;

import com.toolbox.tools.core.StableId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TemplateInstantiationPlan {
    private final String templateId;
    private final VersionNumber templateVersion;
    private final Map<String, String> identityMap;

    public TemplateInstantiationPlan(
            TemplateDefinition template,
            String insertionId
    ) {
        String prefix = StableId.require(insertionId, "insertionId");
        this.templateId = template.templateId();
        this.templateVersion = template.version();
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        for (String oldId : template.internalObjectIds()) {
            String generated = StableId.require(
                    prefix + "." + oldId,
                    "generatedObjectId"
            );
            map.put(oldId, generated);
        }
        this.identityMap = Collections.unmodifiableMap(map);
    }

    public String templateId() { return templateId; }
    public VersionNumber templateVersion() { return templateVersion; }
    public Map<String, String> identityMap() { return identityMap; }
}
