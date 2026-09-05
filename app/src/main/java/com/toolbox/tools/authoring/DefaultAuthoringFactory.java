package com.toolbox.tools.authoring;

import com.toolbox.tools.core.MemoryVisibleWorkspaceStore;
import com.toolbox.tools.core.VisibleWorkspaceStore;
import com.toolbox.tools.editor.EditorEnvironment;
import com.toolbox.tools.library.DependencyResolver;
import com.toolbox.tools.library.LibraryManager;
import com.toolbox.tools.runtime.RuntimeEnvironment;

public final class DefaultAuthoringFactory {
    private DefaultAuthoringFactory() {
    }

    public static UnifiedAuthoringWorkspace create(
            RuntimeEnvironment runtime,
            EditorEnvironment editor,
            LibraryManager library
    ) {
        return create(
                runtime,
                editor,
                library,
                new MemoryVisibleWorkspaceStore()
        );
    }

    public static UnifiedAuthoringWorkspace create(
            RuntimeEnvironment runtime,
            EditorEnvironment editor,
            LibraryManager library,
            VisibleWorkspaceStore visibleWorkspace
    ) {
        AuthoringDraftStore drafts = new AuthoringDraftStore();
        AuthoringSearchIndex search = new AuthoringSearchIndex(
                library,
                runtime
        );
        TemplateAuthoringService templateService =
                new TemplateAuthoringService(
                        drafts,
                        library.templates(),
                        library.components(),
                        library.assets(),
                        new DependencyResolver(),
                        new TemplateArchiveStore(visibleWorkspace)
                );
        return new UnifiedAuthoringWorkspace(
                "authoring.workspace.default",
                runtime,
                editor,
                library,
                search,
                drafts,
                templateService
        );
    }
}
