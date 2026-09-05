package com.toolbox.tools.product;

import com.toolbox.tools.core.StableId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class BackgroundTaskManager {
    public enum State {
        QUEUED,
        RUNNING,
        PAUSED,
        SUCCESS,
        FAILED,
        CANCELLED
    }

    public enum DataType {
        NONE,
        TEXT,
        NUMBER,
        BOOLEAN,
        OBJECT,
        LIST
    }

    public enum Constraint {
        NETWORK,
        BATTERY_NOT_LOW,
        CHARGING,
        STORAGE_NOT_LOW
    }

    public enum ExecutionClass {
        CPU,
        IO,
        NETWORK
    }

    public static final class TaskSpec {
        private final String id;
        private final DataType inputType;
        private final DataType resultType;
        private final int maxRetries;
        private final long timeoutMs;
        private final boolean cancellable;
        private final Set<Constraint> constraints;
        private final ExecutionClass executionClass;

        public TaskSpec(
                String id,
                DataType inputType,
                DataType resultType,
                int maxRetries,
                long timeoutMs,
                boolean cancellable,
                Set<Constraint> constraints,
                ExecutionClass executionClass
        ) {
            this.id = StableId.require(id, "taskId");
            this.inputType = Objects.requireNonNull(
                    inputType,
                    "inputType"
            );
            this.resultType = Objects.requireNonNull(
                    resultType,
                    "resultType"
            );
            if (maxRetries < 0 || maxRetries > 8) {
                throw new IllegalArgumentException(
                        "retry background task tidak valid"
                );
            }
            if (timeoutMs < 1_000L
                    || timeoutMs > 24L * 60L * 60L * 1_000L) {
                throw new IllegalArgumentException(
                        "timeout background task tidak valid"
                );
            }
            this.maxRetries = maxRetries;
            this.timeoutMs = timeoutMs;
            this.cancellable = cancellable;
            this.constraints = Collections.unmodifiableSet(
                    constraints == null || constraints.isEmpty()
                            ? EnumSet.noneOf(Constraint.class)
                            : EnumSet.copyOf(constraints)
            );
            this.executionClass = Objects.requireNonNull(
                    executionClass,
                    "executionClass"
            );
        }

        public String id() { return id; }
        public DataType inputType() { return inputType; }
        public DataType resultType() { return resultType; }
        public int maxRetries() { return maxRetries; }
        public long timeoutMs() { return timeoutMs; }
        public boolean cancellable() { return cancellable; }
        public Set<Constraint> constraints() { return constraints; }
        public ExecutionClass executionClass() { return executionClass; }
        public boolean screenIndependent() { return true; }
    }

    public static final class Task {
        private final TaskSpec spec;
        private final State state;
        private final int progress;
        private final int attempts;
        private final long queuedAt;
        private final long startedAt;
        private final String input;
        private final String result;
        private final String error;

        Task(
                TaskSpec spec,
                State state,
                int progress,
                int attempts,
                long queuedAt,
                long startedAt,
                String input,
                String result,
                String error
        ) {
            this.spec = spec;
            this.state = state;
            this.progress = progress;
            this.attempts = attempts;
            this.queuedAt = queuedAt;
            this.startedAt = startedAt;
            this.input = input;
            this.result = result;
            this.error = error;
        }

        public String id() { return spec.id(); }
        public TaskSpec spec() { return spec; }
        public State state() { return state; }
        public int progress() { return progress; }
        public int attempts() { return attempts; }
        public long queuedAt() { return queuedAt; }
        public long startedAt() { return startedAt; }
        public String input() { return input; }
        public String result() { return result; }
        public String error() { return error; }

        public boolean timedOut(long nowMs) {
            return state == State.RUNNING
                    && startedAt > 0
                    && nowMs - startedAt > spec.timeoutMs();
        }
    }

    private final Map<String, TaskSpec> specs =
            new LinkedHashMap<>();
    private final Map<String, Task> tasks =
            new LinkedHashMap<>();

    public BackgroundTaskManager() {
        register(new TaskSpec(
                "task.generic",
                DataType.NONE,
                DataType.NONE,
                0,
                60_000,
                true,
                Collections.emptySet(),
                ExecutionClass.IO
        ));
    }

    public synchronized void register(TaskSpec spec) {
        Objects.requireNonNull(spec, "spec");
        if (specs.containsKey(spec.id())) {
            throw new IllegalArgumentException(
                    "task spec sudah ada"
            );
        }
        specs.put(spec.id(), spec);
    }

    public synchronized TaskSpec requireSpec(String id) {
        TaskSpec spec = specs.get(
                StableId.require(id, "taskId")
        );
        if (spec == null) {
            throw new IllegalArgumentException(
                    "task spec tidak tersedia"
            );
        }
        return spec;
    }

    /**
     * Backward-compatible queue. It uses a complete generic task contract.
     */
    public synchronized void queue(String id) {
        String stable = StableId.require(id, "taskId");
        if (!specs.containsKey(stable)) {
            register(new TaskSpec(
                    stable,
                    DataType.NONE,
                    DataType.NONE,
                    0,
                    60_000,
                    true,
                    Collections.emptySet(),
                    ExecutionClass.IO
            ));
        }
        queue(stable, null);
    }

    public synchronized void queue(
            String id,
            String typedInput
    ) {
        TaskSpec spec = requireSpec(id);
        if (tasks.containsKey(spec.id())) {
            throw new IllegalArgumentException(
                    "tugas sudah ada"
            );
        }
        validateValue(
                spec.inputType(),
                typedInput,
                "input"
        );
        tasks.put(
                spec.id(),
                new Task(
                        spec,
                        State.QUEUED,
                        0,
                        0,
                        System.currentTimeMillis(),
                        0,
                        typedInput,
                        null,
                        null
                )
        );
    }

    public synchronized void start(String id) {
        Task task = requireTask(id);
        if (task.state() != State.QUEUED
                && task.state() != State.PAUSED) {
            throw new IllegalStateException(
                    "task tidak dapat dimulai dari "
                            + task.state()
            );
        }
        tasks.put(
                task.id(),
                copy(
                        task,
                        State.RUNNING,
                        task.progress(),
                        task.attempts() + 1,
                        task.startedAt() == 0
                                ? System.currentTimeMillis()
                                : task.startedAt(),
                        task.result(),
                        task.error()
                )
        );
    }

    public synchronized void progress(
            String id,
            int progress
    ) {
        Task task = requireTask(id);
        if (task.state() != State.RUNNING) {
            throw new IllegalStateException(
                    "progress hanya saat RUNNING"
            );
        }
        if (progress < 0 || progress > 100) {
            throw new IllegalArgumentException(
                    "progress tidak valid"
            );
        }
        tasks.put(
                task.id(),
                copy(
                        task,
                        State.RUNNING,
                        progress,
                        task.attempts(),
                        task.startedAt(),
                        task.result(),
                        task.error()
                )
        );
    }

    public synchronized void pause(String id) {
        transitionRunning(id, State.PAUSED);
    }

    public synchronized void succeed(
            String id,
            String typedResult
    ) {
        Task task = requireTask(id);
        if (task.state() != State.RUNNING) {
            throw new IllegalStateException(
                    "success hanya saat RUNNING"
            );
        }
        validateValue(
                task.spec().resultType(),
                typedResult,
                "result"
        );
        tasks.put(
                task.id(),
                copy(
                        task,
                        State.SUCCESS,
                        100,
                        task.attempts(),
                        task.startedAt(),
                        typedResult,
                        null
                )
        );
    }

    public synchronized void fail(
            String id,
            String error
    ) {
        Task task = requireTask(id);
        if (task.state() != State.RUNNING) {
            throw new IllegalStateException(
                    "fail hanya saat RUNNING"
            );
        }
        String reason = error == null
                ? "UNKNOWN"
                : error.trim();
        if (task.attempts() <= task.spec().maxRetries()) {
            tasks.put(
                    task.id(),
                    copy(
                            task,
                            State.QUEUED,
                            0,
                            task.attempts(),
                            0,
                            null,
                            reason
                    )
            );
        } else {
            tasks.put(
                    task.id(),
                    copy(
                            task,
                            State.FAILED,
                            task.progress(),
                            task.attempts(),
                            task.startedAt(),
                            null,
                            reason
                    )
            );
        }
    }

    public synchronized void cancel(String id) {
        Task task = requireTask(id);
        if (!task.spec().cancellable()) {
            throw new IllegalStateException(
                    "task tidak dapat dibatalkan"
            );
        }
        if (task.state() == State.SUCCESS
                || task.state() == State.FAILED
                || task.state() == State.CANCELLED) {
            return;
        }
        tasks.put(
                task.id(),
                copy(
                        task,
                        State.CANCELLED,
                        task.progress(),
                        task.attempts(),
                        task.startedAt(),
                        task.result(),
                        task.error()
                )
        );
    }

    public synchronized List<String> enforceTimeouts(
            long nowMs
    ) {
        List<String> timedOut = new ArrayList<>();
        for (Task task : new ArrayList<>(tasks.values())) {
            if (!task.timedOut(nowMs)) continue;
            timedOut.add(task.id());
            fail(task.id(), "TIMEOUT");
        }
        return Collections.unmodifiableList(timedOut);
    }

    /**
     * Legacy state update API retained for old call sites/tests.
     */
    public synchronized void update(
            String id,
            State state,
            int progress
    ) {
        Task task = requireTask(id);
        Objects.requireNonNull(state, "state");
        if (progress < 0 || progress > 100) {
            throw new IllegalArgumentException(
                    "progress tidak valid"
            );
        }
        tasks.put(
                task.id(),
                copy(
                        task,
                        state,
                        progress,
                        task.attempts(),
                        state == State.RUNNING
                                && task.startedAt() == 0
                                ? System.currentTimeMillis()
                                : task.startedAt(),
                        task.result(),
                        task.error()
                )
        );
    }

    public synchronized Task requireTask(String id) {
        Task task = tasks.get(
                StableId.require(id, "taskId")
        );
        if (task == null) {
            throw new IllegalArgumentException(
                    "tugas tidak tersedia"
            );
        }
        return task;
    }

    public synchronized List<Task> all() {
        return Collections.unmodifiableList(
                new ArrayList<>(tasks.values())
        );
    }

    public synchronized List<TaskSpec> specs() {
        return Collections.unmodifiableList(
                new ArrayList<>(specs.values())
        );
    }

    public synchronized boolean completeContract() {
        if (specs.isEmpty()) return false;
        for (TaskSpec spec : specs.values()) {
            if (!spec.screenIndependent()
                    || spec.timeoutMs() < 1_000
                    || spec.maxRetries() < 0
                    || spec.executionClass() == null
                    || spec.constraints() == null) {
                return false;
            }
        }
        return true;
    }

    private void transitionRunning(
            String id,
            State target
    ) {
        Task task = requireTask(id);
        if (task.state() != State.RUNNING) {
            throw new IllegalStateException(
                    "transition hanya dari RUNNING"
            );
        }
        tasks.put(
                task.id(),
                copy(
                        task,
                        target,
                        task.progress(),
                        task.attempts(),
                        task.startedAt(),
                        task.result(),
                        task.error()
                )
        );
    }

    private static Task copy(
            Task task,
            State state,
            int progress,
            int attempts,
            long startedAt,
            String result,
            String error
    ) {
        return new Task(
                task.spec(),
                state,
                progress,
                attempts,
                task.queuedAt(),
                startedAt,
                task.input(),
                result,
                error
        );
    }

    private static void validateValue(
            DataType type,
            String value,
            String label
    ) {
        if (type == DataType.NONE) {
            if (value != null && !value.isEmpty()) {
                throw new IllegalArgumentException(
                        label + " harus NONE"
                );
            }
            return;
        }
        if (value == null) {
            throw new IllegalArgumentException(
                    label + " wajib"
            );
        }
        switch (type) {
            case NUMBER:
                try {
                    Double.parseDouble(value);
                } catch (NumberFormatException error) {
                    throw new IllegalArgumentException(
                            label + " NUMBER tidak valid"
                    );
                }
                break;
            case BOOLEAN:
                if (!"true".equals(value)
                        && !"false".equals(value)) {
                    throw new IllegalArgumentException(
                            label + " BOOLEAN tidak valid"
                    );
                }
                break;
            default:
                // TEXT/OBJECT/LIST are declarative serialized values.
                break;
        }
    }
}
