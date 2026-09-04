package com.toolbox.tools.library;

import com.toolbox.tools.core.DigestUtils;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public final class ComponentManifest {
    public static final int SCHEMA_VERSION = 1;

    private final String componentId;
    private final VersionNumber version;
    private final int schemaVersion;
    private final String implementationRef;
    private final String checksum;

    private ComponentManifest(
            String componentId,
            VersionNumber version,
            int schemaVersion,
            String implementationRef,
            String checksum
    ) {
        this.componentId = componentId;
        this.version = version;
        this.schemaVersion = schemaVersion;
        this.implementationRef = implementationRef;
        this.checksum = checksum;
    }

    public static ComponentManifest from(ComponentDefinition definition) {
        String canonical = canonical(definition);
        return new ComponentManifest(
                definition.componentId(),
                definition.version(),
                SCHEMA_VERSION,
                definition.implementationRef(),
                DigestUtils.sha256(canonical.getBytes(StandardCharsets.UTF_8))
        );
    }

    public boolean verifies(ComponentDefinition definition) {
        return componentId.equals(definition.componentId())
                && version.equals(definition.version())
                && implementationRef.equals(definition.implementationRef())
                && checksum.equals(
                DigestUtils.sha256(
                        canonical(definition).getBytes(StandardCharsets.UTF_8)
                )
        );
    }

    public String componentId() { return componentId; }
    public VersionNumber version() { return version; }
    public int schemaVersion() { return schemaVersion; }
    public String implementationRef() { return implementationRef; }
    public String checksum() { return checksum; }

    private static String canonical(ComponentDefinition definition) {
        StringBuilder out = new StringBuilder();
        out.append("componentId=").append(definition.componentId()).append('\n');
        out.append("labelId=").append(definition.labelId()).append('\n');
        out.append("labelIndonesia=").append(definition.labelIndonesia()).append('\n');
        out.append("categoryId=").append(definition.categoryId()).append('\n');
        out.append("iconAssetId=")
                .append(definition.iconAssetId() == null ? "" : definition.iconAssetId())
                .append('\n');
        out.append("version=").append(definition.version()).append('\n');
        out.append("lifecycle=").append(definition.lifecycle()).append('\n');
        out.append("implementationRef=").append(definition.implementationRef()).append('\n');

        for (Map.Entry<String, PropertyContract> entry :
                new TreeMap<>(definition.properties()).entrySet()) {
            PropertyContract p = entry.getValue();
            out.append("property=").append(p.propertyId())
                    .append('|').append(p.type())
                    .append('|').append(p.nullable())
                    .append('|').append(p.editable())
                    .append('|').append(p.defaultValue() == null ? "" : p.defaultValue())
                    .append('|').append(String.join(",", new TreeSet<>(p.enumValues())))
                    .append('\n');
        }

        for (Map.Entry<String, EventContract> entry :
                new TreeMap<>(definition.events()).entrySet()) {
            EventContract e = entry.getValue();
            out.append("event=").append(e.eventId())
                    .append('|')
                    .append(String.join(",", new TreeSet<>(e.compatibleActionTypes())))
                    .append('\n');
        }

        out.append("states=")
                .append(String.join(",", new TreeSet<>(definition.stateContract().stateIds())))
                .append('\n');
        out.append("bindingProfile=")
                .append(definition.bindingContract().defaultProfileId())
                .append('\n');
        out.append("bindingTypes=")
                .append(String.join(
                        ",",
                        new TreeSet<>(definition.bindingContract().supportedBindingTypes())
                ))
                .append('\n');
        out.append("bindingDeterministic=")
                .append(definition.bindingContract().deterministicAutoConnectOnly())
                .append('\n');
        out.append("accessibility=")
                .append(definition.accessibilityContract().roleId())
                .append('|').append(definition.accessibilityContract().labelRequired())
                .append('|').append(definition.accessibilityContract().focusableByDefault())
                .append('\n');
        out.append("capabilities=")
                .append(String.join(",", new TreeSet<>(definition.capabilityRequirements())))
                .append('\n');

        List<String> assetDeps = new ArrayList<>();
        for (AssetDependencyRef d : definition.assetDependencies()) {
            assetDeps.add(
                    d.assetId()
                            + "|"
                            + d.versionRange().minInclusive()
                            + "|"
                            + d.versionRange().maxExclusive()
                            + "|"
                            + d.required()
            );
        }
        Collections.sort(assetDeps);
        for (String item : assetDeps) out.append("assetDependency=").append(item).append('\n');

        List<String> componentDeps = new ArrayList<>();
        for (DependencyRef d : definition.dependencies()) {
            componentDeps.add(
                    d.dependencyId()
                            + "|"
                            + d.versionRange().minInclusive()
                            + "|"
                            + d.versionRange().maxExclusive()
                            + "|"
                            + d.required()
            );
        }
        Collections.sort(componentDeps);
        for (String item : componentDeps) {
            out.append("componentDependency=").append(item).append('\n');
        }
        return out.toString();
    }
}
