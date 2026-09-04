package com.toolbox.tools.core;

import com.toolbox.tools.editor.EdgePanelModel;
import com.toolbox.tools.editor.EditorMode;
import com.toolbox.tools.editor.VisualCapabilitySet;
import com.toolbox.tools.editor.VisualEditOperation;
import com.toolbox.tools.editor.VisualEditTransaction;
import com.toolbox.tools.library.LibraryItemType;
import com.toolbox.tools.library.LibraryKey;
import com.toolbox.tools.library.VersionNumber;
import com.toolbox.tools.runtime.RenderTree;
import com.toolbox.tools.runtime.Renderer;
import com.toolbox.tools.runtime.RuntimeModelValidator;

import java.util.Collections;

public final class VerificationManager {
    public VerificationResult verify(AppKernel kernel) {
        if (kernel == null) {
            return VerificationResult.fail("kernel missing");
        }
        if (kernel.state() != AppState.READY) {
            return VerificationResult.fail("kernel not ready");
        }
        if (!kernel.toolRegistry().contains("foundation")
                || !kernel.engineManager().contains("foundation-engine")) {
            return VerificationResult.fail("foundation unavailable");
        }
        if (!"30".equals(kernel.configStore().get("targetApi", ""))
                || !"arm64".equals(kernel.configStore().get("targetAbi", ""))) {
            return VerificationResult.fail("android target mismatch");
        }
        if (!"5".equals(kernel.configStore().get("tahap", ""))) {
            return VerificationResult.fail("tahap 5 configuration missing");
        }
        if (kernel.recoveryManager().isRecoveryRequired()) {
            return VerificationResult.fail("recovery required");
        }

        ProjectState project = kernel.projectManager().current();
        if (!"project.default".equals(project.projectId())
                || project.schemaVersion() != ProjectState.CURRENT_SCHEMA_VERSION
                || project.buildModelVersion()
                != ProjectState.CURRENT_BUILD_MODEL_VERSION) {
            return VerificationResult.fail("project contract mismatch");
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

        if (kernel.editorEnvironment() == null
                || kernel.editorEnvironment().visualSession()
                .object("object.home.primary") == null) {
            return VerificationResult.fail("editor working state missing");
        }

        EdgePanelModel addPanel = kernel.editorEnvironment()
                .shell()
                .edgePanel(VisualCapabilitySet.defaultEditable());
        if (!"Tambah ke Layar".equals(addPanel.titleIndonesia())
                || addPanel.items().isEmpty()) {
            return VerificationResult.fail("edge add panel missing");
        }

        kernel.editorEnvironment().shell().selectObject(
                "object.home.primary"
        );
        EdgePanelModel editPanel = kernel.editorEnvironment()
                .shell()
                .edgePanel(VisualCapabilitySet.defaultEditable());
        if (!"Edit Object".equals(editPanel.titleIndonesia())
                || editPanel.items().size() < 10) {
            return VerificationResult.fail("capability edge panel incomplete");
        }

        kernel.editorEnvironment().visualSession().apply(
                new VisualEditTransaction(
                        "verification.edit",
                        Collections.singletonList(
                                new VisualEditOperation(
                                        "object.home.primary",
                                        com.toolbox.tools.editor.VisualCapability.CONTENT,
                                        "property.text",
                                        "Buka Detail"
                                )
                        )
                ),
                VisualCapabilitySet.defaultEditable()
        );
        if (!kernel.editorEnvironment().visualSession().undo()) {
            return VerificationResult.fail("editor undo unavailable");
        }
        if (!kernel.editorEnvironment().visualSession().redo()) {
            return VerificationResult.fail("editor redo unavailable");
        }

        kernel.editorEnvironment().shell().setMode(EditorMode.PREVIEW);
        if (kernel.editorEnvironment().shell().editorOverlayVisible()) {
            return VerificationResult.fail("preview overlay visible");
        }
        kernel.editorEnvironment().shell().setMode(EditorMode.EDIT);

        return VerificationResult.pass("tahap 5 visual editor ready");
    }
}
