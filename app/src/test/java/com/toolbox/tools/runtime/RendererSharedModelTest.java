package com.toolbox.tools.runtime;

import com.toolbox.tools.library.ComponentInstance;
import com.toolbox.tools.library.DefaultLibraryFactory;
import com.toolbox.tools.library.LibraryManager;
import com.toolbox.tools.library.VersionNumber;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public final class RendererSharedModelTest {
    @Test
    public void rendererIsDerivedAndDoesNotCloneSourceScreen() {
        LibraryManager library = DefaultLibraryFactory.create();
        ComponentInstance instance = new ComponentInstance(
                "instance.one",
                "component.button",
                VersionNumber.parse("1.0.0"),
                Collections.singletonMap("property.text", "Simpan")
        );
        ScreenDefinition screen = new ScreenDefinition(
                "screen.one",
                "Layar Satu",
                Collections.singletonList(instance)
        );
        Map<String, ScreenDefinition> screens = new LinkedHashMap<>();
        screens.put(screen.screenId(), screen);
        SharedRuntimeModel model = new SharedRuntimeModel(
                screens,
                screen.screenId()
        );

        RenderTree first = new Renderer().materialize(
                model.screen("screen.one"),
                library.components()
        );
        RenderTree second = new Renderer().materialize(
                model.screen("screen.one"),
                library.components()
        );

        assertNotSame(first, second);
        assertEquals("screen.one", first.screenId());
        assertEquals(1, first.nodes().size());
        assertEquals("Simpan", first.nodes().get(0).properties().get("property.text"));
        assertTrue(first.diagnostics().isEmpty());
        assertEquals(screen, model.screen("screen.one"));
    }

    @Test
    public void missingExactComponentProducesDiagnosticWithoutDeletingInstance() {
        LibraryManager library = DefaultLibraryFactory.create();
        ComponentInstance missing = new ComponentInstance(
                "instance.missing",
                "component.button",
                VersionNumber.parse("9.0.0"),
                Collections.emptyMap()
        );
        ScreenDefinition screen = new ScreenDefinition(
                "screen.one",
                "Layar Satu",
                Collections.singletonList(missing)
        );

        RenderTree tree = new Renderer().materialize(
                screen,
                library.components()
        );

        assertEquals(1, tree.nodes().size());
        assertFalse(tree.nodes().get(0).available());
        assertEquals(1, tree.diagnostics().size());
        assertEquals(
                DiagnosticCode.COMPONENT_UNAVAILABLE,
                tree.diagnostics().get(0).code()
        );
        assertEquals("instance.missing", tree.nodes().get(0).instanceId());
    }
}
