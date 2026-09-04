package com.toolbox.tools.library;

import java.util.ArrayList;
import java.util.List;

public final class DependencyResolver {
    public DependencyResolutionResult resolveComponent(
            ComponentDefinition definition,
            ComponentRegistry components,
            AssetRegistry assets
    ) {
        List<String> issues = new ArrayList<>();
        for (DependencyRef dependency : definition.dependencies()) {
            if (dependency.required()
                    && !components.hasCompatible(
                    dependency.dependencyId(),
                    dependency.versionRange())) {
                issues.add("COMPONENT_DEPENDENCY_MISSING_OR_INCOMPATIBLE:"
                        + dependency.dependencyId());
            }
        }
        for (String assetId : definition.assetRequirements()) {
            if (!assets.hasAnyVersion(assetId)) {
                issues.add("ASSET_DEPENDENCY_MISSING:" + assetId);
            }
        }
        return new DependencyResolutionResult(issues);
    }

    public DependencyResolutionResult resolveTemplate(
            TemplateDefinition template,
            ComponentRegistry components,
            AssetRegistry assets
    ) {
        List<String> issues = new ArrayList<>();
        for (DependencyRef dependency : template.componentDependencies()) {
            if (dependency.required()
                    && !components.hasCompatible(
                    dependency.dependencyId(),
                    dependency.versionRange())) {
                issues.add("COMPONENT_DEPENDENCY_MISSING_OR_INCOMPATIBLE:"
                        + dependency.dependencyId());
            }
        }
        for (AssetDependencyRef dependency : template.assetDependencies()) {
            if (dependency.required()
                    && !assets.hasCompatible(
                    dependency.assetId(),
                    dependency.versionRange())) {
                issues.add("ASSET_DEPENDENCY_MISSING_OR_INCOMPATIBLE:"
                        + dependency.assetId());
            }
        }
        return new DependencyResolutionResult(issues);
    }
}
