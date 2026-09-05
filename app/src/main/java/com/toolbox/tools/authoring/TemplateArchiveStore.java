package com.toolbox.tools.authoring;

import com.toolbox.tools.core.VisibleWorkspaceStore;
import com.toolbox.tools.library.AssetDependencyRef;
import com.toolbox.tools.library.DependencyRef;
import com.toolbox.tools.library.TemplateDefinition;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

public final class TemplateArchiveStore {
    private final VisibleWorkspaceStore visible;

    public TemplateArchiveStore(VisibleWorkspaceStore visible) {
        this.visible = Objects.requireNonNull(visible, "visible");
    }

    public String fileName(TemplateDefinition template) {
        return template.templateId().replace('.', '_')
                + "-"
                + template.version().toString().replace('.', '_')
                + ".tbxt";
    }

    public void publish(TemplateDefinition template) throws IOException {
        Objects.requireNonNull(template, "template");
        byte[] bytes = encode(template)
                .getBytes(StandardCharsets.UTF_8);
        String name = fileName(template);
        visible.write(
                VisibleWorkspaceStore.Area.TEMPLATES,
                name,
                bytes
        );
        byte[] roundTrip = visible.read(
                VisibleWorkspaceStore.Area.TEMPLATES,
                name
        );
        if (!java.util.Arrays.equals(bytes, roundTrip)) {
            throw new IOException(
                    "template visible archive verification failed"
            );
        }
    }

    public void delete(TemplateDefinition template) throws IOException {
        visible.delete(
                VisibleWorkspaceStore.Area.TEMPLATES,
                fileName(template)
        );
    }

    public boolean exists(TemplateDefinition template) throws IOException {
        return visible.exists(
                VisibleWorkspaceStore.Area.TEMPLATES,
                fileName(template)
        );
    }

    public String encode(TemplateDefinition template) {
        StringBuilder out = new StringBuilder();
        out.append("TBX_TEMPLATE_V1\n");
        out.append("id=").append(template.templateId()).append('\n');
        out.append("label=").append(escape(template.labelIndonesia())).append('\n');
        out.append("version=").append(template.version()).append('\n');
        out.append("lifecycle=").append(template.lifecycle().name()).append('\n');

        for (String id : new TreeSet<>(template.internalObjectIds())) {
            out.append("object=").append(id).append('\n');
        }

        List<String> components = new ArrayList<>();
        for (DependencyRef dependency
                : template.componentDependencies()) {
            components.add(
                    dependency.dependencyId()
                            + "@"
                            + dependency.versionRange()
                            + "|required="
                            + dependency.required()
            );
        }
        Collections.sort(components);
        for (String value : components) {
            out.append("component=")
                    .append(value)
                    .append('\n');
        }

        List<String> assets = new ArrayList<>();
        for (AssetDependencyRef dependency
                : template.assetDependencies()) {
            assets.add(
                    dependency.assetId()
                            + "@"
                            + dependency.versionRange()
                            + "|required="
                            + dependency.required()
            );
        }
        Collections.sort(assets);
        for (String value : assets) {
            out.append("asset=")
                    .append(value)
                    .append('\n');
        }
        return out.toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("=", "\\=");
    }
}
