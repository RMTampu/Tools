package com.toolbox.tools.library;

import com.toolbox.tools.core.StableId;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class BindingContract {
    private final String defaultProfileId;
    private final Set<String> supportedBindingTypes;
    private final boolean deterministicAutoConnectOnly;

    public BindingContract(
            String defaultProfileId,
            Set<String> supportedBindingTypes,
            boolean deterministicAutoConnectOnly
    ) {
        this.defaultProfileId = StableId.require(
                defaultProfileId,
                "defaultProfileId"
        );
        LinkedHashSet<String> types = new LinkedHashSet<>();
        if (supportedBindingTypes != null) {
            for (String type : supportedBindingTypes) {
                types.add(StableId.require(type, "bindingType"));
            }
        }
        this.supportedBindingTypes = Collections.unmodifiableSet(types);
        this.deterministicAutoConnectOnly = deterministicAutoConnectOnly;
    }

    public String defaultProfileId() { return defaultProfileId; }
    public Set<String> supportedBindingTypes() { return supportedBindingTypes; }
    public boolean deterministicAutoConnectOnly() { return deterministicAutoConnectOnly; }
}
