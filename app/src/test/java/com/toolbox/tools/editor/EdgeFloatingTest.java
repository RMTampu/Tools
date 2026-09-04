package com.toolbox.tools.editor;

import org.junit.Test;

import java.util.EnumSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class EdgeFloatingTest {
    @Test
    public void edgeChangesContextAndOnlyShowsSupportedCapabilities() {
        EditorShellController shell = new EditorShellController(
                new BubbleController(new BubblePositionStore()),
                new EdgePanelFactory()
        );

        EdgePanelModel add = shell.edgePanel(
                VisualCapabilitySet.defaultEditable()
        );
        assertEquals("Tambah ke Layar", add.titleIndonesia());
        assertEquals(6, add.items().size());

        shell.selectObject("object.primary");
        VisualCapabilitySet limited = new VisualCapabilitySet(
                EnumSet.of(
                        VisualCapability.SIZE,
                        VisualCapability.CONTENT,
                        VisualCapability.LOCK
                )
        );
        EdgePanelModel selected = shell.edgePanel(limited);

        assertEquals("Edit Objek", selected.titleIndonesia());
        assertEquals(3, selected.items().size());
        assertEquals("Ukuran", selected.items().get(0).labelIndonesia());

        EdgePanelModel all = shell.edgePanel(
                VisualCapabilitySet.defaultEditable()
        );
        assertEquals(20, all.items().size());
        assertTrue(all.items().stream().anyMatch(
                item -> "Hubungkan Pengikatan Otomatis".equals(item.labelIndonesia())
        ));

        shell.activateFunction(EditorFunction.DATA);
        EdgePanelModel data = shell.edgePanel(limited);
        assertEquals("Data", data.titleIndonesia());
        assertTrue(data.items().size() >= 6);
        assertNull(shell.selectedObjectId());
    }

    @Test
    public void editOffClearsSelectionAndUsesQuickAccessContext() {
        EditorShellController shell = new EditorShellController(
                new BubbleController(new BubblePositionStore()),
                new EdgePanelFactory()
        );
        shell.selectObject("object.primary");
        shell.setEditEnabled(false);

        assertNull(shell.selectedObjectId());
        EdgePanelModel panel = shell.edgePanel(
                VisualCapabilitySet.defaultEditable()
        );
        assertEquals("Editor", panel.titleIndonesia());
        assertEquals("Edit NONAKTIF", panel.breadcrumb());
        assertEquals(4, panel.items().size());
    }

    @Test
    public void floatingEditorIsSinglePrimarySafeAndCloseDoesNotMutateWorkingState() {
        FloatingEditorController floating = new FloatingEditorController(
                new FloatingPlacementEngine()
        );
        EditorRect safe = new EditorRect(0, 0, 500, 800);
        EditorRect object = new EditorRect(180, 300, 320, 380);

        FloatingEditorState first = floating.open(
                "floating.size",
                "object.primary",
                safe,
                object,
                180,
                140
        );
        assertNotNull(first);
        EditorRect floatingRect = new EditorRect(
                first.position().x(),
                first.position().y(),
                first.position().x() + first.width(),
                first.position().y() + first.height()
        );
        assertFalse(floatingRect.intersects(object));

        FloatingEditorState second = floating.open(
                "floating.color",
                "object.primary",
                safe,
                object,
                180,
                140
        );
        assertEquals("floating.color", second.editorId());
        assertEquals("floating.color", floating.active().editorId());

        floating.drag(new EditorPoint(999, 999), safe);
        assertTrue(floating.active().position().x() <= 320);
        assertTrue(floating.active().position().y() <= 660);

        floating.pin(true);
        assertTrue(floating.active().pinned());

        floating.close();
        assertNull(floating.active());
    }
}
