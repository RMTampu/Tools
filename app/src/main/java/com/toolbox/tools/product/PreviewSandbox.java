package com.toolbox.tools.product;

import com.toolbox.tools.core.StableId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    public enum DataState {
        SAMPLE,
        LOADING,
        ERROR,
        EMPTY,
        LIST,
        SIMULATED_ACTION_RESULT
    }

    public static final class PreviewData {
        private final String id;
        private final DataState state;
        private final Map<String, String> values;
        private final List<Map<String, String>> rows;
        private final String message;

        PreviewData(
                String id,
                DataState state,
                Map<String, String> values,
                List<Map<String, String>> rows,
                String message
        ) {
            this.id = id;
            this.state = state;
            this.values = Collections.unmodifiableMap(
                    new LinkedHashMap<>(values)
            );
            List<Map<String, String>> safeRows =
                    new ArrayList<>();
            for (Map<String, String> row : rows) {
                safeRows.add(Collections.unmodifiableMap(
                        new LinkedHashMap<>(row)
                ));
            }
            this.rows = Collections.unmodifiableList(safeRows);
            this.message = message == null ? "" : message;
        }

        public String id() { return id; }
        public DataState state() { return state; }
        public Map<String, String> values() { return values; }
        public List<Map<String, String>> rows() { return rows; }
        public String message() { return message; }

        public boolean productionWritable() {
            return false;
        }
    }

    private final Map<String, String> mockData =
            new LinkedHashMap<>();
    private final Map<String, PreviewData> scenarios =
            new LinkedHashMap<>();

    public synchronized void putMock(
            String id,
            String value
    ) {
        mockData.put(
                StableId.require(id, "mockId"),
                value == null ? "" : value
        );
    }

    public synchronized void putScenario(
            String id,
            DataState state,
            Map<String, String> values,
            List<Map<String, String>> rows,
            String message
    ) {
        String stable = StableId.require(id, "previewScenarioId");
        Objects.requireNonNull(state, "state");

        Map<String, String> safeValues =
                values == null
                        ? Collections.emptyMap()
                        : values;
        List<Map<String, String>> safeRows =
                rows == null
                        ? Collections.emptyList()
                        : rows;

        switch (state) {
            case LOADING:
            case EMPTY:
                if (!safeRows.isEmpty()) {
                    throw new IllegalArgumentException(
                            "LOADING/EMPTY tidak boleh membawa rows"
                    );
                }
                break;
            case ERROR:
                if (message == null || message.trim().isEmpty()) {
                    throw new IllegalArgumentException(
                            "ERROR preview wajib message"
                    );
                }
                break;
            case LIST:
                if (safeRows.isEmpty()) {
                    throw new IllegalArgumentException(
                            "LIST preview wajib rows"
                    );
                }
                break;
            case SIMULATED_ACTION_RESULT:
                if (message == null || message.trim().isEmpty()) {
                    throw new IllegalArgumentException(
                            "simulated result wajib message"
                    );
                }
                break;
            case SAMPLE:
            default:
                break;
        }

        scenarios.put(
                stable,
                new PreviewData(
                        stable,
                        state,
                        safeValues,
                        safeRows,
                        message
                )
        );
    }

    public synchronized PreviewData scenario(String id) {
        PreviewData value = scenarios.get(
                StableId.require(id, "previewScenarioId")
        );
        if (value == null) {
            throw new IllegalArgumentException(
                    "preview scenario tidak tersedia"
            );
        }
        return value;
    }

    public synchronized Map<String, PreviewData> scenarios() {
        return Collections.unmodifiableMap(
                new LinkedHashMap<>(scenarios)
        );
    }

    public synchronized Map<String, String> snapshot() {
        return Collections.unmodifiableMap(
                new LinkedHashMap<>(mockData)
        );
    }

    public boolean mayExecuteInPreview(
            SideEffect sideEffect
    ) {
        return sideEffect == SideEffect.NONE
                || sideEffect == SideEffect.LOCAL_SAFE;
    }

    public String simulate(SideEffect sideEffect) {
        Objects.requireNonNull(sideEffect, "sideEffect");
        if (mayExecuteInPreview(sideEffect)) {
            return "EKSEKUSI_AMAN";
        }
        return "DISIMULASIKAN_OLEH_SAFETY_GATE";
    }

    public synchronized PreviewData simulateAction(
            String scenarioId,
            SideEffect sideEffect,
            String resultMessage
    ) {
        String outcome = simulate(sideEffect);
        String message = resultMessage == null
                || resultMessage.trim().isEmpty()
                ? outcome
                : outcome + " • " + resultMessage.trim();
        putScenario(
                scenarioId,
                DataState.SIMULATED_ACTION_RESULT,
                Collections.singletonMap(
                        "sideEffect",
                        sideEffect.name()
                ),
                Collections.emptyList(),
                message
        );
        return scenario(scenarioId);
    }

    public synchronized boolean completeContract() {
        java.util.EnumSet<DataState> covered =
                java.util.EnumSet.noneOf(DataState.class);
        for (PreviewData item : scenarios.values()) {
            covered.add(item.state());
            if (item.productionWritable()) return false;
        }
        return covered.containsAll(
                java.util.EnumSet.allOf(DataState.class)
        );
    }
}
