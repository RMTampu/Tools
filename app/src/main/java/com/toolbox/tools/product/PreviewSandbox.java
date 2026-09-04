package com.toolbox.tools.product;

import com.toolbox.tools.core.StableId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PreviewSandbox {
    public enum SideEffect {
        NONE,
        LOCAL_SAFE,
        NETWORK,
        UPLOAD,
        DELETE_EXTERNAL,
        PAYMENT,
        CREDENTIAL
    }

    private final Map<String, String> mockData = new LinkedHashMap<>();

    public synchronized void putMock(String id, String value) {
        mockData.put(StableId.require(id, "mockId"), value == null ? "" : value);
    }

    public synchronized Map<String, String> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(mockData));
    }

    public boolean mayExecuteInPreview(SideEffect sideEffect) {
        return sideEffect == SideEffect.NONE || sideEffect == SideEffect.LOCAL_SAFE;
    }

    public String simulate(SideEffect sideEffect) {
        if (mayExecuteInPreview(sideEffect)) return "EKSEKUSI_AMAN";
        return "DISIMULASIKAN_OLEH_SAFETY_GATE";
    }
}
