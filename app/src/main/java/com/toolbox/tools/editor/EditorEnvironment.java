package com.toolbox.tools.editor;

import java.util.Objects;

public final class EditorEnvironment {
    private final EditorShellController shell;
    private final FloatingEditorController floatingEditor;
    private final VisualEditorSession visualSession;

    public EditorEnvironment(
            EditorShellController shell,
            FloatingEditorController floatingEditor,
            VisualEditorSession visualSession
    ) {
        this.shell = Objects.requireNonNull(shell, "shell");
        this.floatingEditor = Objects.requireNonNull(
                floatingEditor,
                "floatingEditor"
        );
        this.visualSession = Objects.requireNonNull(
                visualSession,
                "visualSession"
        );
    }

    public EditorShellController shell() { return shell; }
    public FloatingEditorController floatingEditor() { return floatingEditor; }
    public VisualEditorSession visualSession() { return visualSession; }
}
