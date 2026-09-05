package com.toolbox.tools.product;

import com.toolbox.tools.core.AppKernel;
import com.toolbox.tools.runtime.BindingDefinition;
import com.toolbox.tools.runtime.BindingMode;
import com.toolbox.tools.runtime.RuntimeEnvironment;
import com.toolbox.tools.runtime.SharedRuntimeModel;
import com.toolbox.tools.runtime.ValueType;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;

import static org.junit.Assert.*;

public final class BindingAutoConnectServiceTest {
    @Test
    public void explicitRuntimeBindingConnectsDeterministically() {
        AppKernel kernel = AppKernel.createDefault();
        BindingAutoConnectService service =
                kernel.productServices().bindingAutoConnect();

        BindingAutoConnectService.Result result =
                service.connect(
                        "instance.home.primary",
                        "property.text"
                );

        assertEquals(
                BindingAutoConnectService.Status.CONNECTED,
                result.status()
        );
        assertEquals(1, result.candidates().size());
        assertEquals(
                "data.items",
                result.candidates().get(0).sourceId()
        );
        assertEquals(
                "field.title",
                result.candidates().get(0).sourceFieldId()
        );
        assertEquals(
                "auto",
                kernel.projectManager()
                        .current()
                        .resources()
                        .get("binding.ui.home.primary.mode")
        );
        assertTrue(
                result.report().contains("POLICY=DETERMINISTIC")
        );
    }

    @Test
    public void multipleDeclaredTargetsAreBlockedWithoutGuessing() {
        AppKernel kernel = AppKernel.createDefault();
        RuntimeEnvironment original =
                kernel.runtimeEnvironment();

        ArrayList<BindingDefinition> bindings =
                new ArrayList<>(
                        original.model().bindings().values()
                );
        bindings.add(new BindingDefinition(
                "binding.home.id",
                "data.items",
                "field.id",
                "instance.home.primary",
                "property.text",
                ValueType.REFERENCE,
                BindingMode.ONE_WAY
        ));

        SharedRuntimeModel model = new SharedRuntimeModel(
                original.model().screens(),
                original.model().startScreenId(),
                new ArrayList<>(
                        original.model().routes().values()
                ),
                new ArrayList<>(
                        original.model().dataSources().values()
                ),
                bindings,
                new ArrayList<>(
                        original.model().flows().values()
                ),
                new ArrayList<>(
                        original.model().events().values()
                ),
                original.model().eventActionBindings()
        );
        RuntimeEnvironment ambiguousRuntime =
                new RuntimeEnvironment(
                        model,
                        original.actions(),
                        original.components()
                );
        DiagnosticCenter diagnostics =
                new DiagnosticCenter();
        BindingAutoConnectService service =
                new BindingAutoConnectService(
                        ambiguousRuntime,
                        kernel.projectManager(),
                        diagnostics
                );

        int before = kernel.projectManager()
                .current()
                .resources()
                .size();
        BindingAutoConnectService.Result result =
                service.connect(
                        "instance.home.primary",
                        "property.text"
                );

        assertEquals(
                BindingAutoConnectService.Status.AMBIGUOUS,
                result.status()
        );
        assertEquals(2, result.candidates().size());
        assertEquals(
                before,
                kernel.projectManager()
                        .current()
                        .resources()
                        .size()
        );
        assertTrue(
                result.report().contains("POLICY=NO_GUESS")
        );
        assertTrue(
                diagnostics.all()
                        .stream()
                        .anyMatch(item ->
                                "BINDING_AMBIGUOUS".equals(
                                        item.code()
                                )
                        )
        );
    }

    @Test
    public void disconnectRemovesGeneratedBinding() {
        AppKernel kernel = AppKernel.createDefault();
        BindingAutoConnectService service =
                kernel.productServices().bindingAutoConnect();
        assertTrue(
                service.connect(
                        "instance.home.primary",
                        "property.text"
                ).connected()
        );

        service.disconnect(
                "instance.home.primary",
                "property.text"
        );

        assertEquals(
                "none",
                kernel.projectManager()
                        .current()
                        .resources()
                        .get("binding.ui.home.primary.mode")
        );
        assertFalse(
                kernel.projectManager()
                        .current()
                        .resources()
                        .keySet()
                        .stream()
                        .anyMatch(key ->
                                key.startsWith(
                                        "binding.autoconnect."
                                )
                        )
        );
    }
}
