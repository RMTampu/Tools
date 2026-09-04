package com.toolbox.tools.core;

import com.toolbox.tools.library.LibraryItemType;
import com.toolbox.tools.library.LibraryKey;
import com.toolbox.tools.library.VersionNumber;
import com.toolbox.tools.runtime.RenderTree;
import com.toolbox.tools.runtime.Renderer;
import com.toolbox.tools.runtime.RuntimeModelValidator;

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
        if (!"4".equals(kernel.configStore().get("tahap", ""))) {
            return VerificationResult.fail("tahap 4 configuration missing");
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

        if (kernel.libraryManager().resolveExact(
                new LibraryKey(
                        LibraryItemType.COMPONENT,
                        "component.button",
                        VersionNumber.parse("1.0.0")
                )
        ) == null) {
            return VerificationResult.fail("default component unavailable");
        }

        if (!new RuntimeModelValidator()
                .validate(kernel.runtimeEnvironment())
                .isEmpty()) {
            return VerificationResult.fail("runtime model diagnostics present");
        }

        RenderTree tree = new Renderer().materialize(
                kernel.runtimeEnvironment().model().screen("screen.home"),
                kernel.runtimeEnvironment().components()
        );
        if (tree.nodes().size() != 1
                || !tree.diagnostics().isEmpty()
                || !tree.nodes().get(0).available()) {
            return VerificationResult.fail("renderer materialization failed");
        }

        if (kernel.runtimeEnvironment().model().flows().isEmpty()
                || kernel.runtimeEnvironment().model().dataSources().isEmpty()
                || kernel.runtimeEnvironment().model().bindings().isEmpty()
                || kernel.runtimeEnvironment().actions().all().isEmpty()) {
            return VerificationResult.fail("tahap 4 runtime contracts incomplete");
        }

        return VerificationResult.pass("tahap 4 runtime model ready");
    }
}
