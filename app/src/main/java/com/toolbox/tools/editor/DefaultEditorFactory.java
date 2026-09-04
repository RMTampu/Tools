package com.toolbox.tools.editor;

import java.util.Collections;

public final class DefaultEditorFactory {
    private DefaultEditorFactory() {
    }

    public static EditorEnvironment create() {
        BubbleController bubble = new BubbleController(
                new BubblePositionStore()
        );
        EditorShellController shell = new EditorShellController(
                bubble,
                new EdgePanelFactory()
        );
        VisualEditorSession session = new VisualEditorSession();
        session.addObject(new VisualObjectState(
                "object.home.primary",
                "component.button",
                Collections.singletonMap(
                        "property.text",
                        "Buka Detail"
                )
        ));
        return new EditorEnvironment(
                shell,
                new FloatingEditorController(
                        new FloatingPlacementEngine()
                ),
                session
        );
    }
}
