package com.toolbox.tools.runtime;

import com.toolbox.tools.library.ComponentInstance;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RuntimeModelValidator {
    public List<RuntimeDiagnostic> validate(RuntimeEnvironment environment) {
        List<RuntimeDiagnostic> diagnostics = new ArrayList<>();
        SharedRuntimeModel model = environment.model();

        NavigationManager navigation = new NavigationManager(
                model,
                new ArrayList<>(model.routes().values())
        );
        diagnostics.addAll(navigation.validateRoutes());

        Map<String, ComponentInstance> instances = new LinkedHashMap<>();
        for (ScreenDefinition screen : model.screens().values()) {
            for (ComponentInstance instance : screen.components()) {
                instances.put(instance.instanceId(), instance);
            }
        }

        BindingValidator bindingValidator = new BindingValidator();
        for (BindingDefinition binding : model.bindings().values()) {
            DataSourceDefinition source = model.dataSources().get(binding.sourceId());
            ComponentInstance instance = instances.get(binding.targetInstanceId());
            if (!bindingValidator.isCompatible(
                    binding,
                    source,
                    instance,
                    environment.components())) {
                diagnostics.add(new RuntimeDiagnostic(
                        DiagnosticCode.CONTRACT_MISMATCH,
                        binding.bindingId(),
                        "Binding source/target contract incompatible"
                ));
            }
        }

        EventActionCompatibility eventCompatibility =
                new EventActionCompatibility();
        for (EventActionBinding binding : model.eventActionBindings()) {
            EventDefinition event = model.events().get(binding.eventId());
            ActionContract action = environment.actions().resolve(binding.actionId());
            if (!eventCompatibility.isCompatible(event, action)) {
                diagnostics.add(new RuntimeDiagnostic(
                        DiagnosticCode.CONTRACT_MISMATCH,
                        binding.bindingId(),
                        "Event/action contract incompatible"
                ));
            }
        }

        FlowValidator flowValidator = new FlowValidator();
        for (FlowGraph flow : model.flows().values()) {
            FlowValidationResult result = flowValidator.validate(flow);
            for (String issue : result.issues()) {
                diagnostics.add(new RuntimeDiagnostic(
                        DiagnosticCode.BROKEN_REFERENCE,
                        flow.flowId(),
                        issue
                ));
            }
        }

        return Collections.unmodifiableList(diagnostics);
    }
}
