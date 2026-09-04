package com.toolbox.tools.library;

import java.util.ArrayList;
import java.util.List;

public final class ComponentValidator {
    public ComponentValidationResult validate(
            ComponentDefinition definition,
            AssetRegistry assetRegistry,
            ComponentRegistry componentRegistry
    ) {
        List<String> errors = new ArrayList<>();
        if (definition == null) {
            errors.add("COMPONENT_MISSING");
            return ComponentValidationResult.of(errors);
        }

        if (definition.iconAssetId() != null
                && !assetRegistry.hasAnyVersion(definition.iconAssetId())) {
            errors.add("ICON_ASSET_MISSING:" + definition.iconAssetId());
        }

        for (String assetId : definition.assetRequirements()) {
            if (!assetRegistry.hasAnyVersion(assetId)) {
                errors.add("ASSET_DEPENDENCY_MISSING:" + assetId);
            }
        }

        for (DependencyRef dependency : definition.dependencies()) {
            if (!componentRegistry.hasCompatible(
                    dependency.dependencyId(),
                    dependency.versionRange())) {
                if (dependency.required()) {
                    errors.add("COMPONENT_DEPENDENCY_MISSING:" + dependency.dependencyId());
                }
            }
        }

        return ComponentValidationResult.of(errors);
    }
}
