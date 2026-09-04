package com.toolbox.tools.repair;

import com.toolbox.tools.core.ProjectManager;
import com.toolbox.tools.core.ProjectState;
import com.toolbox.tools.core.ProjectValidationResult;
import com.toolbox.tools.core.ProjectValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RepairPlanValidator {
    private final ProjectValidator projectValidator = new ProjectValidator();

    public RepairValidationResult validate(
            ProjectManager projectManager,
            RepairPlan plan
    ) {
        List<RepairDiagnostic> diagnostics = new ArrayList<>();
        ProjectState current = projectManager.current();

        if (!current.projectId().equals(plan.projectId())) {
            diagnostics.add(new RepairDiagnostic(
                    "repair.project.identity",
                    "Project identity tidak cocok"
            ));
        }
        if (projectManager.hasUnsavedChanges()) {
            diagnostics.add(new RepairDiagnostic(
                    "repair.project.dirty",
                    "Project harus tersimpan sebelum staging"
            ));
        }
        if (projectManager.savedRevision() != plan.baseRevision()) {
            diagnostics.add(new RepairDiagnostic(
                    "repair.revision.stale",
                    "Base revision repair tidak cocok"
            ));
        }

        for (String id : plan.upserts().keySet()) {
            if (isProtected(id)) {
                diagnostics.add(new RepairDiagnostic(
                        "repair.protected.core",
                        "Safety/recovery core dilindungi"
                ));
            }
        }
        for (String id : plan.deletes()) {
            if (isProtected(id)) {
                diagnostics.add(new RepairDiagnostic(
                        "repair.protected.core",
                        "Safety/recovery core dilindungi"
                ));
            }
        }

        ProjectState preview = current;
        try {
            for (String id : plan.deletes()) {
                preview = preview.withoutResource(id);
            }
            for (Map.Entry<String, String> entry : plan.upserts().entrySet()) {
                preview = preview.withResource(
                        entry.getKey(),
                        entry.getValue()
                );
            }
            ProjectValidationResult validation =
                    projectValidator.validate(preview);
            if (!validation.isPass()) {
                diagnostics.add(new RepairDiagnostic(
                        "repair.project.invalid",
                        validation.message()
                ));
            }
        } catch (RuntimeException error) {
            diagnostics.add(new RepairDiagnostic(
                    "repair.preview.invalid",
                    error.getMessage() == null
                            ? "Repair preview invalid"
                            : error.getMessage()
            ));
        }

        return new RepairValidationResult(diagnostics);
    }

    private static boolean isProtected(String resourceId) {
        return resourceId.startsWith("kernel.")
                || resourceId.startsWith("recovery.")
                || resourceId.startsWith("safety.")
                || resourceId.startsWith("security.");
    }
}
