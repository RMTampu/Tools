package com.toolbox.tools.authoring;

import com.toolbox.tools.core.StableId;
import com.toolbox.tools.editor.EditorEnvironment;
import com.toolbox.tools.editor.EditorFunction;
import com.toolbox.tools.library.LibraryManager;
import com.toolbox.tools.runtime.RuntimeEnvironment;

import java.util.List;
import java.util.Objects;

public final class UnifiedAuthoringWorkspace {
    private final String workspaceId;
    private final RuntimeEnvironment runtime;
    private final EditorEnvironment editor;
    private final LibraryManager library;
    private final AuthoringSearchIndex search;
    private final AuthoringDraftStore drafts;
    private final TemplateAuthoringService templateAuthoring;
    private AuthoringSection activeSection = AuthoringSection.UI;

    public UnifiedAuthoringWorkspace(
            String workspaceId,
            RuntimeEnvironment runtime,
            EditorEnvironment editor,
            LibraryManager library,
            AuthoringSearchIndex search,
            AuthoringDraftStore drafts,
            TemplateAuthoringService templateAuthoring
    ) {
        this.workspaceId = StableId.require(workspaceId, "workspaceId");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.editor = Objects.requireNonNull(editor, "editor");
        this.library = Objects.requireNonNull(library, "library");
        this.search = Objects.requireNonNull(search, "search");
        this.drafts = Objects.requireNonNull(drafts, "drafts");
        this.templateAuthoring = Objects.requireNonNull(
                templateAuthoring,
                "templateAuthoring"
        );
        editor.shell().activateFunction(EditorFunction.UI);
    }

    public synchronized void activate(AuthoringSection section) {
        activeSection = Objects.requireNonNull(section, "section");
        editor.shell().activateFunction(toEditorFunction(section));
    }

    public synchronized AuthoringSection activeSection() {
        return activeSection;
    }

    public synchronized List<AuthoringSearchResult> search(
            String query,
            int limit
    ) {
        return search.search(new AuthoringSearchQuery(
                query,
                activeSection,
                null,
                limit
        ));
    }

    public synchronized List<AuthoringSearchResult> searchAll(
            String query,
            int limit
    ) {
        return search.search(new AuthoringSearchQuery(
                query,
                null,
                null,
                limit
        ));
    }

    public String workspaceId() { return workspaceId; }
    public RuntimeEnvironment runtime() { return runtime; }
    public EditorEnvironment editor() { return editor; }
    public LibraryManager library() { return library; }
    public AuthoringDraftStore drafts() { return drafts; }
    public TemplateAuthoringService templateAuthoring() { return templateAuthoring; }

    private static EditorFunction toEditorFunction(AuthoringSection section) {
        switch (section) {
            case UI: return EditorFunction.UI;
            case LOGIC: return EditorFunction.LOGIC;
            case DATA: return EditorFunction.DATA;
            case BINDING: return EditorFunction.BINDING;
            case ASSET: return EditorFunction.ASSET;
            default: throw new IllegalStateException("unknown authoring section");
        }
    }
}
