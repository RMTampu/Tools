package com.toolbox.tools.editor;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class BubbleShellTest {
    @Test
    public void bubbleIsBoundedAndStoresPerOrientation() {
        BubblePositionStore store = new BubblePositionStore();
        BubbleController bubble = new BubbleController(store);
        EditorRect portrait = new EditorRect(0, 0, 400, 800);
        EditorRect landscape = new EditorRect(0, 0, 800, 400);

        EditorPoint p = bubble.drag(
                Orientation.PORTRAIT,
                new EditorPoint(999, -50),
                portrait,
                56
        );
        EditorPoint l = bubble.drag(
                Orientation.LANDSCAPE,
                new EditorPoint(100, 200),
                landscape,
                56
        );

        assertEquals(new EditorPoint(344, 0), p);
        assertEquals(new EditorPoint(100, 200), l);
        assertEquals(
                new EditorPoint(344, 0),
                bubble.position(Orientation.PORTRAIT, portrait, 56)
        );
        assertEquals(
                new EditorPoint(100, 200),
                bubble.position(Orientation.LANDSCAPE, landscape, 56)
        );
    }

    @Test
    public void tapTogglesPanelAndEmergencyResetClosesIt() {
        BubbleController bubble = new BubbleController(
                new BubblePositionStore()
        );
        EditorShellController shell = new EditorShellController(
                bubble,
                new EdgePanelFactory()
        );

        assertTrue(bubble.tap());
        assertTrue(bubble.panelOpen());
        shell.emergencyReset();
        assertFalse(bubble.panelOpen());
        assertEquals(EditorFunction.UI, shell.activeFunction());
        assertEquals(EditorMode.EDIT, shell.mode());
        assertTrue(shell.editEnabled());
    }

    @Test
    public void previewHidesOverlayAndLiveRequiresCapability() {
        EditorShellController shell = new EditorShellController(
                new BubbleController(new BubblePositionStore()),
                new EdgePanelFactory()
        );

        shell.setMode(EditorMode.PREVIEW);
        assertFalse(shell.editorOverlayVisible());

        shell.setMode(EditorMode.EDIT);
        assertTrue(shell.editorOverlayVisible());

        assertThrows(
                IllegalStateException.class,
                () -> shell.setMode(EditorMode.LIVE)
        );
        shell.setLiveCapability(true);
        shell.setMode(EditorMode.LIVE);
        assertEquals(EditorMode.LIVE, shell.mode());

        shell.setLiveCapability(false);
        assertEquals(EditorMode.EDIT, shell.mode());
    }
}
