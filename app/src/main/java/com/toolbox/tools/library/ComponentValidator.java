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

        if (definition.labelIndonesia().trim().isEmpty()) {
            errors.add("COMPONENT_LABEL_INDONESIA_MISSING");
        }
        if (!definition.stateContract().stateIds().contains("state.normal")) {
            errors.add("COMPONENT_NORMAL_STATE_MISSING");
        }

        if (definition.iconAssetId() != null
                && assetRegistry.latestReady(definition.iconAssetId()) == null) {
            errors.add("ICON_ASSET_MISSING:" + definition.iconAssetId());
        }

        for (AssetDependencyRef dependency : definition.assetDependencies()) {
            if (dependency.required()
                    && !assetRegistry.hasCompatible(
                    dependency.assetId(),
                    dependency.versionRange())) {
                errors.add("ASSET_DEPENDENCY_MISSING_OR_INCOMPATIBLE:"
                        + dependency.assetId());
            }
        }

        for (DependencyRef dependency : definition.dependencies()) {
            if (dependency.required()
                    && !componentRegistry.hasCompatible(
                    dependency.dependencyId(),
                    dependency.versionRange())) {
                errors.add("COMPONENT_DEPENDENCY_MISSING_OR_INCOMPATIBLE:"
                        + dependency.dependencyId());
            }
        }

        return ComponentValidationResult.of(errors);
    }
}
