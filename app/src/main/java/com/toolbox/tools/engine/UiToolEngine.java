package com.toolbox.tools.engine;

import com.toolbox.tools.core.EngineContract;
import com.toolbox.tools.editor.EditorEnvironment;
import com.toolbox.tools.library.LibraryManager;
import com.toolbox.tools.runtime.RuntimeEnvironment;
import java.util.Objects;

public final class UiToolEngine implements EngineContract {
    private final EditorEnvironment editor;
    private final RuntimeEnvironment runtime;
    private final LibraryManager library;

    public UiToolEngine(
            EditorEnvironment editor,
            RuntimeEnvironment runtime,
            LibraryManager library
    ) {
        this.editor = Objects.requireNonNull(editor, "editor");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.library = Objects.requireNonNull(library, "library");
    }

    @Override public String id() { return "engine.ui"; }
    @Override public boolean isReady() {
        return editor.shell() != null
                && editor.visualSession() != null
                && runtime.model() != null
                && library.components() != null;
    }

    public int jumlahLayar() { return runtime.model().screens().size(); }
    public int jumlahKomponen() { return library.components().allReady().size(); }
}
