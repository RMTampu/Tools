package com.toolbox.tools.build;

import com.toolbox.tools.core.AppKernel;
import com.toolbox.tools.core.ProjectState;
import com.toolbox.tools.library.AssetDescriptor;
import com.toolbox.tools.library.ComponentDefinition;
import com.toolbox.tools.library.TemplateDefinition;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public final class ApplicationIrBuilder {
    public ApplicationIr build(AppKernel kernel) {
        ProjectState project = kernel.projectManager().current();
        StringBuilder out = new StringBuilder();
        out.append("TBX_APPLICATION_IR_V1\n");
        out.append("project=")
                .append(project.projectId()).append('|')
                .append(project.schemaVersion()).append('|')
                .append(project.buildModelVersion()).append('|')
                .append(project.revision()).append('|')
                .append(project.lifecycle().name()).append('\n');

        for (Map.Entry<String, String> entry
                : new TreeMap<>(project.resources()).entrySet()) {
            out.append("resource|")
                    .append(entry.getKey()).append('|')
                    .append(sha256(entry.getValue())).append('\n');
        }

        for (Map.Entry<String, java.util.Set<String>> entry
                : new TreeMap<>(project.references()).entrySet()) {
            for (String target : new TreeSet<>(entry.getValue())) {
                out.append("reference|")
                        .append(entry.getKey()).append('|')
                        .append(target).append('\n');
            }
        }

        for (String dependency : new TreeSet<>(project.dependencyRefs())) {
            out.append("dependency|")
                    .append(dependency).append('\n');
        }

        for (String id : new TreeSet<>(
                kernel.runtimeEnvironment().model().screens().keySet())) {
            out.append("screen|").append(id).append('\n');
        }
        for (String id : new TreeSet<>(
                kernel.runtimeEnvironment().model().routes().keySet())) {
            out.append("route|").append(id).append('\n');
        }
        for (String id : new TreeSet<>(
                kernel.runtimeEnvironment().model().dataSources().keySet())) {
            out.append("data|").append(id).append('\n');
        }
        for (String id : new TreeSet<>(
                kernel.runtimeEnvironment().model().bindings().keySet())) {
            out.append("binding|").append(id).append('\n');
        }
        for (String id : new TreeSet<>(
                kernel.runtimeEnvironment().model().flows().keySet())) {
            out.append("flow|").append(id).append('\n');
        }
        for (String id : new TreeSet<>(
                kernel.runtimeEnvironment().model().events().keySet())) {
            out.append("event|").append(id).append('\n');
        }
        for (String id : new TreeSet<>(
                kernel.runtimeEnvironment().actions().all().keySet())) {
            out.append("action|").append(id).append('\n');
        }

        for (ComponentDefinition definition
                : kernel.libraryManager().components().allReady()) {
            out.append("component|")
                    .append(definition.componentId()).append('|')
                    .append(definition.version().toString()).append('\n');
        }
        for (AssetDescriptor descriptor
                : kernel.libraryManager().assets().allReady()) {
            out.append("asset|")
                    .append(descriptor.assetId()).append('|')
                    .append(descriptor.version().toString()).append('|')
                    .append(descriptor.sha256()).append('\n');
        }
        for (TemplateDefinition template
                : kernel.libraryManager().templates().allReady()) {
            out.append("template|")
                    .append(template.templateId()).append('|')
                    .append(template.version().toString()).append('\n');
        }

        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        counts.put("resources", project.resources().size());
        counts.put("references", referenceCount(project));
        counts.put("dependencies", project.dependencyRefs().size());
        counts.put("screens", kernel.runtimeEnvironment()
                .model().screens().size());
        counts.put("routes", kernel.runtimeEnvironment()
                .model().routes().size());
        counts.put("dataSources", kernel.runtimeEnvironment()
                .model().dataSources().size());
        counts.put("bindings", kernel.runtimeEnvironment()
                .model().bindings().size());
        counts.put("flows", kernel.runtimeEnvironment()
                .model().flows().size());
        counts.put("events", kernel.runtimeEnvironment()
                .model().events().size());
        counts.put("actions", kernel.runtimeEnvironment()
                .actions().all().size());
        counts.put("components", kernel.libraryManager()
                .components().allReady().size());
        counts.put("assets", kernel.libraryManager()
                .assets().allReady().size());
        counts.put("templates", kernel.libraryManager()
                .templates().allReady().size());

        String canonical = out.toString();
        return new ApplicationIr(
                ApplicationIr.CURRENT_IR_VERSION,
                project.projectId(),
                project.revision(),
                canonical,
                sha256(canonical),
                counts
        );
    }

    private static int referenceCount(ProjectState project) {
        int count = 0;
        for (java.util.Set<String> targets
                : project.references().values()) {
            count += targets.size();
        }
        return count;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );
            StringBuilder out = new StringBuilder();
            for (byte item : bytes) {
                out.append(String.format(
                        java.util.Locale.ROOT,
                        "%02x",
                        item
                ));
            }
            return out.toString();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }
}
