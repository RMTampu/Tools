package com.toolbox.tools.authoring;

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
                        new DependencyResolver()
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
