package com.toolbox.tools.product;

import com.toolbox.tools.authoring.DefaultAuthoringFactory;
import com.toolbox.tools.authoring.TemplateAuthoringDraft;
import com.toolbox.tools.authoring.UnifiedAuthoringWorkspace;
import com.toolbox.tools.core.AppKernel;
import com.toolbox.tools.core.FileVisibleWorkspaceStore;
import com.toolbox.tools.core.VisibleWorkspaceStore;
import com.toolbox.tools.editor.DefaultEditorFactory;
import com.toolbox.tools.library.DefaultLibraryFactory;
import com.toolbox.tools.library.DependencyRef;
import com.toolbox.tools.library.LibraryManager;
import com.toolbox.tools.library.VersionNumber;
import com.toolbox.tools.library.VersionRange;
import com.toolbox.tools.runtime.DefaultRuntimeFactory;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;

import static org.junit.Assert.*;

public final class VisibleStorageIntegrationTest {
    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void exportsSnapshotsAndTemplatesArePhysicallyVisible()
            throws Exception {
        File projectRoot = temp.newFolder("project");
        File assetRoot = temp.newFolder("asset-cache");
        AppKernel kernel = AppKernel.createPersistent(
                projectRoot,
                assetRoot
        );
        if (kernel.projectManager().savedRevision() <= 0) {
            kernel.projectManager().save();
        }

        VisibleArtifactManager.Record exported =
                kernel.productServices()
                        .visibleArtifacts()
                        .exportCurrent();
        VisibleArtifactManager.Record snapshot =
                kernel.productServices()
                        .visibleArtifacts()
                        .snapshotCurrent("test");

        assertTrue(
                kernel.visibleWorkspaceStore().exists(
                        VisibleWorkspaceStore.Area.EXPORTS,
                        exported.fileName()
                )
        );
        assertTrue(
                kernel.visibleWorkspaceStore().exists(
                        VisibleWorkspaceStore.Area.SNAPSHOTS,
                        snapshot.fileName()
                )
        );
        assertTrue(exported.sha256().matches("[0-9a-f]{64}"));
        assertTrue(snapshot.sha256().matches("[0-9a-f]{64}"));

        File templateRoot = temp.newFolder("template-visible");
        FileVisibleWorkspaceStore visible =
                new FileVisibleWorkspaceStore(templateRoot);
        LibraryManager library = DefaultLibraryFactory.create();
        UnifiedAuthoringWorkspace workspace =
                DefaultAuthoringFactory.create(
                        DefaultRuntimeFactory.create(
                                library.components()
                        ),
                        DefaultEditorFactory.create(),
                        library,
                        visible
                );

        TemplateAuthoringDraft draft =
                new TemplateAuthoringDraft(
                        "draft.template.visible",
                        "template.visible",
                        "Template Visible",
                        VersionNumber.parse("1.0.0"),
                        new LinkedHashSet<>(
                                Collections.singletonList(
                                        "object.visible"
                                )
                        ),
                        Collections.singletonList(
                                new DependencyRef(
                                        "component.button",
                                        VersionRange.majorCompatible(
                                                VersionNumber.parse(
                                                        "1.0.0"
                                                )
                                        ),
                                        true
                                )
                        ),
                        Collections.emptyList()
                );
        workspace.templateAuthoring().create(draft);
        workspace.templateAuthoring().publish(draft);

        assertEquals(
                1,
                visible.list(
                        VisibleWorkspaceStore.Area.TEMPLATES
                ).size()
        );
        assertTrue(
                visible.list(
                        VisibleWorkspaceStore.Area.TEMPLATES
                ).get(0).endsWith(".tbxt")
        );
    }

    @Test
    public void autoRepairExecutesOnlyDeterministicOperations() {
        AppKernel kernel = AppKernel.createDefault();

        kernel.productServices().cache().put(
                "cache.test.disposable",
                2048,
                CacheManager.Priority.TEMP
        );

        AutoRepairEngine.RepairResult applied =
                kernel.productServices()
                        .autoRepair()
                        .applyDeterministic(
                                Arrays.asList(
                                        AutoRepairEngine.RepairType
                                                .CLEAR_DISPOSABLE_CACHE,
                                        AutoRepairEngine.RepairType
                                                .REBUILD_DERIVED_INDEX,
                                        AutoRepairEngine.RepairType
                                                .REGENERATE_DERIVED_MANIFEST
                                )
                        );

        assertTrue(applied.isPass());
        assertEquals(3, applied.applied().size());
        assertFalse(
                kernel.productServices()
                        .cache()
                        .snapshot()
                        .containsKey("cache.test.disposable")
        );
        assertNotNull(
                kernel.productServices()
                        .autoRepair()
                        .lastDerivedManifestSha256()
        );

        AutoRepairEngine.RepairResult guarded =
                kernel.productServices()
                        .autoRepair()
                        .applyDeterministic(
                                Collections.singletonList(
                                        AutoRepairEngine.RepairType
                                                .REMAP_EXACT_ID_CONFLICT
                                )
                        );
        assertFalse(guarded.isPass());
        assertTrue(
                guarded.rejected().get(0)
                        .contains("EXACT_INPUT_REQUIRED")
        );
        assertFalse(
                kernel.productServices()
                        .autoRepair()
                        .mayGuessBusinessLogic()
        );
        assertFalse(
                kernel.productServices()
                        .autoRepair()
                        .mayDeleteUserData()
        );
    }
}
