package com.toolbox.tools.authoring;

import com.toolbox.tools.editor.DefaultEditorFactory;
import com.toolbox.tools.editor.EditorEnvironment;
import com.toolbox.tools.library.DefaultLibraryFactory;
import com.toolbox.tools.library.LibraryManager;
import com.toolbox.tools.runtime.DefaultRuntimeFactory;
import com.toolbox.tools.runtime.RuntimeEnvironment;

import org.junit.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class UnifiedAuthoringSearchTest {
    @Test
    public void allFiveSectionsShareSameRuntimeAndOnlyOneIsActive() {
        LibraryManager library = DefaultLibraryFactory.create();
        RuntimeEnvironment runtime =
                DefaultRuntimeFactory.create(library.components());
        EditorEnvironment editor = DefaultEditorFactory.create();
        UnifiedAuthoringWorkspace workspace =
                DefaultAuthoringFactory.create(runtime, editor, library);

        assertSame(runtime, workspace.runtime());
        assertSame(editor, workspace.editor());

        for (AuthoringSection section : AuthoringSection.values()) {
            workspace.activate(section);
            assertEquals(section, workspace.activeSection());
        }
    }

    @Test
    public void unifiedSearchIsStableBoundedFilteredAndDoesNotExecute() {
        LibraryManager library = DefaultLibraryFactory.create();
        RuntimeEnvironment runtime =
                DefaultRuntimeFactory.create(library.components());
        UnifiedAuthoringWorkspace workspace =
                DefaultAuthoringFactory.create(
                        runtime,
                        DefaultEditorFactory.create(),
                        library
                );

        int actionsBefore = runtime.actions().all().size();
        List<AuthoringSearchResult> first =
                workspace.searchAll("", 100);
        List<AuthoringSearchResult> second =
                workspace.searchAll("", 100);

        assertFalse(first.isEmpty());
        assertTrue(first.size() <= AuthoringSearchQuery.MAX_RESULTS);
        assertEquals(keys(first), keys(second));
        assertEquals(actionsBefore, runtime.actions().all().size());

        List<AuthoringSearchResult> stableId =
                workspace.searchAll("component.button", 20);
        assertFalse(stableId.isEmpty());
        assertEquals(
                AuthoringItemKind.COMPONENT,
                stableId.get(0).kind()
        );

        AuthoringSearchIndex index = new AuthoringSearchIndex(
                library,
                runtime
        );
        List<AuthoringSearchResult> logic = index.search(
                new AuthoringSearchQuery(
                        "",
                        AuthoringSection.LOGIC,
                        EnumSet.of(
                                AuthoringItemKind.FLOW,
                                AuthoringItemKind.ACTION,
                                AuthoringItemKind.EVENT
                        ),
                        20
                )
        );
        assertFalse(logic.isEmpty());
        for (AuthoringSearchResult result : logic) {
            assertTrue(
                    result.kind() == AuthoringItemKind.FLOW
                            || result.kind() == AuthoringItemKind.ACTION
                            || result.kind() == AuthoringItemKind.EVENT
            );
        }

        assertThrows(
                IllegalArgumentException.class,
                () -> new AuthoringSearchQuery(
                        repeat("x", AuthoringSearchQuery.MAX_QUERY_LENGTH + 1),
                        null,
                        null,
                        20
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new AuthoringSearchQuery("", null, null, 101)
        );
    }

    private static java.util.List<String> keys(
            List<AuthoringSearchResult> results
    ) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (AuthoringSearchResult result : results) {
            out.add(result.stableKey());
        }
        return out;
    }

    private static String repeat(String value, int count) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < count; i++) out.append(value);
        return out.toString();
    }
}
