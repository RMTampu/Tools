package com.toolbox.tools.authoring;

import com.toolbox.tools.editor.DefaultEditorFactory;
import com.toolbox.tools.library.DependencyRef;
import com.toolbox.tools.library.DefaultLibraryFactory;
import com.toolbox.tools.library.LibraryManager;
import com.toolbox.tools.library.TemplateDefinition;
import com.toolbox.tools.library.TemplateInstantiationPlan;
import com.toolbox.tools.library.VersionNumber;
import com.toolbox.tools.library.VersionRange;
import com.toolbox.tools.runtime.DefaultRuntimeFactory;
import com.toolbox.tools.runtime.RuntimeEnvironment;

import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class TemplateAuthoringServiceTest {
    @Test
    public void previewDoesNotMutateMasterAndPublishIsExactVersion() {
        LibraryManager library = DefaultLibraryFactory.create();
        RuntimeEnvironment runtime =
                DefaultRuntimeFactory.create(library.components());
        UnifiedAuthoringWorkspace workspace =
                DefaultAuthoringFactory.create(
                        runtime,
                        DefaultEditorFactory.create(),
                        library
                );

        TemplateAuthoringDraft draft = validDraft(
                "draft.template.card",
                "template.card",
                "Kartu Tahap 6"
        );
        workspace.templateAuthoring().create(draft);

        assertNull(library.templates().resolveExact(
                "template.card",
                VersionNumber.parse("1.0.0")
        ));

        TemplateInstantiationPlan preview =
                workspace.templateAuthoring().preview(
                        draft,
                        "insert.card"
                );
        assertEquals(
                "insert.card.object.card",
                preview.identityMap().get("object.card")
        );
        assertNull(library.templates().resolveExact(
                "template.card",
                VersionNumber.parse("1.0.0")
        ));
        assertEquals(
                DraftLifecycle.VALIDATED,
                workspace.drafts().get("draft.template.card").lifecycle()
        );

        TemplateDefinition published =
                workspace.templateAuthoring().publish(draft);
        assertNotNull(published);
        assertNotNull(library.templates().resolveExact(
                "template.card",
                VersionNumber.parse("1.0.0")
        ));
        assertEquals(
                DraftLifecycle.PUBLISHED,
                workspace.drafts().get("draft.template.card").lifecycle()
        );

        List<AuthoringSearchResult> results =
                workspace.searchAll("template.card", 20);
        assertFalse(results.isEmpty());
        assertEquals(AuthoringItemKind.TEMPLATE, results.get(0).kind());

        assertThrows(
                IllegalArgumentException.class,
                () -> workspace.templateAuthoring().publish(draft)
        );
    }

    @Test
    public void missingDependencyFailsClosedAndDoesNotPublish() {
        LibraryManager library = DefaultLibraryFactory.create();
        UnifiedAuthoringWorkspace workspace =
                DefaultAuthoringFactory.create(
                        DefaultRuntimeFactory.create(library.components()),
                        DefaultEditorFactory.create(),
                        library
                );
        TemplateAuthoringDraft draft = new TemplateAuthoringDraft(
                "draft.template.missing",
                "template.missing",
                "Template Dependency Hilang",
                VersionNumber.parse("1.0.0"),
                new LinkedHashSet<>(
                        Collections.singletonList("object.missing")
                ),
                Collections.singletonList(
                        new DependencyRef(
                                "component.not.available",
                                VersionRange.majorCompatible(
                                        VersionNumber.parse("1.0.0")
                                ),
                                true
                        )
                ),
                Collections.emptyList()
        );
        workspace.templateAuthoring().create(draft);

        TemplateAuthoringValidation validation =
                workspace.templateAuthoring().validate(draft);

        assertFalse(validation.isPass());
        assertTrue(validation.message().contains(
                "COMPONENT_DEPENDENCY_MISSING_OR_INCOMPATIBLE"
        ));
        assertNull(library.templates().resolveExact(
                "template.missing",
                VersionNumber.parse("1.0.0")
        ));
        assertEquals(
                DraftLifecycle.DRAFT,
                workspace.drafts()
                        .get("draft.template.missing")
                        .lifecycle()
        );
    }

    private static TemplateAuthoringDraft validDraft(
            String draftId,
            String templateId,
            String label
    ) {
        return new TemplateAuthoringDraft(
                draftId,
                templateId,
                label,
                VersionNumber.parse("1.0.0"),
                new LinkedHashSet<>(
                        Collections.singletonList("object.card")
                ),
                Collections.singletonList(
                        new DependencyRef(
                                "component.button",
                                VersionRange.majorCompatible(
                                        VersionNumber.parse("1.0.0")
                                ),
                                true
                        )
                ),
                Collections.emptyList()
        );
    }
}
