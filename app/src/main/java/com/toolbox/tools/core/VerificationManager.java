package com.toolbox.tools.core;

import com.toolbox.tools.library.LibraryItemType;
import com.toolbox.tools.library.LibraryKey;
import com.toolbox.tools.library.VersionNumber;

public final class VerificationManager {
    public VerificationResult verify(AppKernel kernel) {
        if (kernel == null) {
            return VerificationResult.fail("kernel missing");
        }
        if (kernel.state() != AppState.READY) {
            return VerificationResult.fail("kernel not ready");
        }
        if (!kernel.toolRegistry().contains("foundation")) {
            return VerificationResult.fail("foundation tool missing");
        }
        if (!kernel.engineManager().contains("foundation-engine")) {
            return VerificationResult.fail("foundation engine missing");
        }
        if (!"30".equals(kernel.configStore().get("targetApi", ""))) {
            return VerificationResult.fail("android api target mismatch");
        }
        if (!"arm64".equals(kernel.configStore().get("targetAbi", ""))) {
            return VerificationResult.fail("android abi target mismatch");
        }
        if (!"3".equals(kernel.configStore().get("tahap", ""))) {
            return VerificationResult.fail("tahap 3 configuration missing");
        }
        if (kernel.recoveryManager().isRecoveryRequired()) {
            return VerificationResult.fail("recovery required");
        }

        ProjectState project = kernel.projectManager().current();
        if (!"project.default".equals(project.projectId())) {
            return VerificationResult.fail("project identity mismatch");
        }
        if (project.schemaVersion() != ProjectState.CURRENT_SCHEMA_VERSION) {
            return VerificationResult.fail("project schema mismatch");
        }
        if (project.buildModelVersion() != ProjectState.CURRENT_BUILD_MODEL_VERSION) {
            return VerificationResult.fail("build model mismatch");
        }
        if (kernel.projectManager().accessStatus() != ProjectAccessStatus.FOLDER_MISSING
                && kernel.projectManager().accessStatus() != ProjectAccessStatus.PROJECT_OK) {
            return VerificationResult.fail("project access invalid");
        }

        Object component = kernel.libraryManager().resolveExact(
                new LibraryKey(
                        LibraryItemType.COMPONENT,
                        "component.button",
                        VersionNumber.parse("1.0.0")
                )
        );
        if (component == null) {
            return VerificationResult.fail("default component unavailable");
        }

        Object template = kernel.libraryManager().resolveExact(
                new LibraryKey(
                        LibraryItemType.TEMPLATE,
                        "template.screen.basic",
                        VersionNumber.parse("1.0.0")
                )
        );
        if (template == null) {
            return VerificationResult.fail("default template unavailable");
        }

        if (kernel.assetStore() == null) {
            return VerificationResult.fail("asset store unavailable");
        }

        return VerificationResult.pass("tahap 3 library foundation ready");
    }
}
