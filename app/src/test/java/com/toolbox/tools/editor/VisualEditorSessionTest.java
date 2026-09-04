package com.toolbox.tools.editor;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class VisualEditorSessionTest {
    @Test
    public void groupedGestureIsOneUndoTransaction() {
        VisualEditorSession session = session();
        VisualEditTransaction gesture = new VisualEditTransaction(
                "gesture.resize.one",
                Arrays.asList(
                        new VisualEditOperation(
                                "object.primary",
                                VisualCapability.SIZE,
                                "property.width",
                                "240"
                        ),
                        new VisualEditOperation(
                                "object.primary",
                                VisualCapability.SIZE,
                                "property.height",
                                "72"
                        )
                )
        );

        session.apply(
                gesture,
                VisualCapabilitySet.defaultEditable()
        );

        assertEquals(1, session.undoCount());
        assertEquals(
                "240",
                session.object("object.primary")
                        .properties()
                        .get("property.width")
        );
        assertTrue(session.undo());
        assertFalse(
                session.object("object.primary")
                        .properties()
                        .containsKey("property.width")
        );
        assertTrue(session.redo());
        assertEquals(
                "72",
                session.object("object.primary")
                        .properties()
                        .get("property.height")
        );
    }

    @Test
    public void lockPreventsMutationAndRecordsDiagnostic() {
        VisualEditorSession session = session();
        session.setLocked(
                "object.primary",
                VisualCapability.POSITION,
                true
        );

        assertThrows(
                IllegalStateException.class,
                () -> session.apply(
                        new VisualEditTransaction(
                                "gesture.move.locked",
                                Collections.singletonList(
                                        new VisualEditOperation(
                                                "object.primary",
                                                VisualCapability.POSITION,
                                                "property.x",
                                                "40"
                                        )
                                )
                        ),
                        VisualCapabilitySet.defaultEditable()
                )
        );

        assertFalse(
                session.object("object.primary")
                        .properties()
                        .containsKey("property.x")
        );
        assertEquals(
                "editor.operation.locked",
                session.diagnostics().get(0).code()
        );
    }

    @Test
    public void unsupportedAndBrokenOperationsFailClosed() {
        VisualEditorSession session = session();
        VisualCapabilitySet contentOnly = new VisualCapabilitySet(
                java.util.EnumSet.of(VisualCapability.CONTENT)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> session.apply(
                        new VisualEditTransaction(
                                "gesture.unsupported",
                                Collections.singletonList(
                                        new VisualEditOperation(
                                                "object.primary",
                                                VisualCapability.COLOR,
                                                "property.color",
                                                "#ffffff"
                                        )
                                )
                        ),
                        contentOnly
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> session.apply(
                        new VisualEditTransaction(
                                "gesture.broken",
                                Collections.singletonList(
                                        new VisualEditOperation(
                                                "object.missing",
                                                VisualCapability.CONTENT,
                                                "property.text",
                                                "X"
                                        )
                                )
                        ),
                        contentOnly
                )
        );

        assertEquals(2, session.diagnostics().size());
    }

    @Test
    public void historyIsBounded() {
        VisualEditorSession session = session();
        for (int i = 0; i < VisualHistory.MAX_HISTORY + 10; i++) {
            session.apply(
                    new VisualEditTransaction(
                            "gesture.edit." + i,
                            Collections.singletonList(
                                    new VisualEditOperation(
                                            "object.primary",
                                            VisualCapability.CONTENT,
                                            "property.text",
                                            "Value " + i
                                    )
                            )
                    ),
                    VisualCapabilitySet.defaultEditable()
            );
        }
        assertEquals(VisualHistory.MAX_HISTORY, session.undoCount());
    }

    private static VisualEditorSession session() {
        VisualEditorSession session = new VisualEditorSession();
        session.addObject(new VisualObjectState(
                "object.primary",
                "component.button",
                Collections.singletonMap("property.text", "Awal")
        ));
        return session;
    }
}
