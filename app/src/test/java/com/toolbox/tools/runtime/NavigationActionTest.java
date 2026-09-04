package com.toolbox.tools.runtime;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class NavigationActionTest {
    @Test
    public void stableNavigationUsesLightweightBackStackAndTypedParameters() {
        Map<String, ScreenDefinition> screens = new LinkedHashMap<>();
        screens.put("screen.home", new ScreenDefinition(
                "screen.home",
                "Beranda",
                Collections.emptyList()
        ));
        screens.put("screen.detail", new ScreenDefinition(
                "screen.detail",
                "Detail",
                Collections.emptyList()
        ));
        NavigationRoute route = new NavigationRoute(
                "route.detail",
                "screen.detail",
                Collections.singletonMap(
                        "parameter.item",
                        ValueType.REFERENCE
                )
        );
        SharedRuntimeModel model = new SharedRuntimeModel(
                screens,
                "screen.home"
        );
        NavigationManager manager = new NavigationManager(
                model,
                Collections.singletonList(route)
        );

        BackStackEntry detail = manager.navigate(
                "route.detail",
                Collections.singletonMap("parameter.item", "item.one")
        );

        assertEquals("screen.detail", detail.screenId());
        assertEquals(1, manager.backStackSnapshot().size());
        assertEquals("screen.home", manager.back().screenId());
        assertThrows(
                IllegalArgumentException.class,
                () -> manager.navigate("route.detail", Collections.emptyMap())
        );
    }

    @Test
    public void brokenNavigationReferenceIsExplicitDiagnostic() {
        Map<String, ScreenDefinition> screens = new LinkedHashMap<>();
        screens.put("screen.home", new ScreenDefinition(
                "screen.home",
                "Beranda",
                Collections.emptyList()
        ));
        SharedRuntimeModel model = new SharedRuntimeModel(
                screens,
                "screen.home"
        );
        NavigationManager manager = new NavigationManager(
                model,
                Collections.singletonList(
                        new NavigationRoute(
                                "route.missing",
                                "screen.missing",
                                Collections.emptyMap()
                        )
                )
        );

        assertEquals(1, manager.validateRoutes().size());
        assertEquals(
                DiagnosticCode.BROKEN_NAVIGATION_REFERENCE,
                manager.validateRoutes().get(0).code()
        );
    }

    @Test
    public void eventActionCompatibilityIsTypedAndCompositeOrderIsStable() {
        EventDefinition event = new EventDefinition(
                "event.submit",
                Collections.singletonMap("payload.id", ValueType.REFERENCE)
        );
        ActionContract compatible = new ActionContract(
                "action.submit",
                Collections.singletonMap("payload.id", ValueType.REFERENCE),
                Collections.emptyMap(),
                null,
                ExecutionMode.ASYNC,
                5000,
                true,
                true
        );
        ActionContract incompatible = new ActionContract(
                "action.wrong",
                Collections.singletonMap("payload.id", ValueType.NUMBER),
                Collections.emptyMap(),
                null,
                ExecutionMode.SYNC,
                0,
                false,
                false
        );

        assertTrue(new EventActionCompatibility().isCompatible(event, compatible));
        assertFalse(new EventActionCompatibility().isCompatible(event, incompatible));

        CompositeAction composite = new CompositeAction(
                "action.composite",
                Arrays.asList("action.first", "action.second"),
                "condition.success",
                "action.failure",
                "action.fallback",
                "action.compensate"
        );
        assertEquals(
                Arrays.asList("action.first", "action.second"),
                composite.orderedActionIds()
        );
    }
}
