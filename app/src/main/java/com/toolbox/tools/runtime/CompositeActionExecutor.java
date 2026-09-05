package com.toolbox.tools.runtime;

import com.toolbox.tools.core.StableId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class CompositeActionExecutor {
    public interface Clock {
        long nowMillis();
    }

    public interface ActionInvoker {
        Outcome invoke(
                ActionContract contract,
                String executionId,
                CancellationToken cancellation
        );
    }

    public interface ConditionEvaluator {
        boolean evaluate(String conditionId);
    }

    public static final class CancellationToken {
        private boolean cancelled;

        public synchronized void cancel() {
            cancelled = true;
        }

        public synchronized boolean isCancelled() {
            return cancelled;
        }
    }

    public static final class Outcome {
        private final boolean success;
        private final boolean cancelled;
        private final String message;

        public Outcome(
                boolean success,
                boolean cancelled,
                String message
        ) {
            this.success = success;
            this.cancelled = cancelled;
            this.message = message == null ? "" : message;
        }

        public boolean success() { return success; }
        public boolean cancelled() { return cancelled; }
        public String message() { return message; }

        public static Outcome success() {
            return new Outcome(true, false, "PASS");
        }

        public static Outcome failed(String message) {
            return new Outcome(false, false, message);
        }

        public static Outcome cancelled(String message) {
            return new Outcome(false, true, message);
        }
    }

    public static final class Result {
        private final boolean success;
        private final boolean cancelled;
        private final boolean timedOut;
        private final List<String> executed;
        private final List<String> compensated;
        private final String message;

        Result(
                boolean success,
                boolean cancelled,
                boolean timedOut,
                List<String> executed,
                List<String> compensated,
                String message
        ) {
            this.success = success;
            this.cancelled = cancelled;
            this.timedOut = timedOut;
            this.executed = Collections.unmodifiableList(
                    new ArrayList<>(executed)
            );
            this.compensated = Collections.unmodifiableList(
                    new ArrayList<>(compensated)
            );
            this.message = message == null ? "" : message;
        }

        public boolean success() { return success; }
        public boolean cancelled() { return cancelled; }
        public boolean timedOut() { return timedOut; }
        public List<String> executed() { return executed; }
        public List<String> compensated() { return compensated; }
        public String message() { return message; }
    }

    private final ActionRegistry actions;
    private final ActionInvoker invoker;
    private final ConditionEvaluator conditions;
    private final Clock clock;

    public CompositeActionExecutor(
            ActionRegistry actions,
            ActionInvoker invoker,
            ConditionEvaluator conditions
    ) {
        this(
                actions,
                invoker,
                conditions,
                System::currentTimeMillis
        );
    }

    public CompositeActionExecutor(
            ActionRegistry actions,
            ActionInvoker invoker,
            ConditionEvaluator conditions,
            Clock clock
    ) {
        this.actions = Objects.requireNonNull(
                actions,
                "actions"
        );
        this.invoker = Objects.requireNonNull(
                invoker,
                "invoker"
        );
        this.conditions = Objects.requireNonNull(
                conditions,
                "conditions"
        );
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Result execute(
            CompositeAction composite,
            String runId,
            long overallTimeoutMillis,
            CancellationToken cancellation
    ) {
        Objects.requireNonNull(composite, "composite");
        String run = StableId.require(runId, "runId");
        if (overallTimeoutMillis <= 0
                || overallTimeoutMillis > 300_000L) {
            throw new IllegalArgumentException(
                    "composite timeout invalid"
            );
        }
        CancellationToken token = cancellation == null
                ? new CancellationToken()
                : cancellation;

        long started = clock.nowMillis();
        List<String> executed = new ArrayList<>();
        List<String> compensated = new ArrayList<>();

        int index = 0;
        for (String actionId : composite.orderedActionIds()) {
            if (token.isCancelled()) {
                return new Result(
                        false,
                        true,
                        false,
                        executed,
                        compensated,
                        "COMPOSITE_CANCELLED"
                );
            }
            if (timedOut(started, overallTimeoutMillis)) {
                compensate(
                        composite,
                        run,
                        token,
                        executed,
                        compensated
                );
                return new Result(
                        false,
                        false,
                        true,
                        executed,
                        compensated,
                        "COMPOSITE_TIMEOUT"
                );
            }

            ActionContract contract = requireAction(actionId);
            Outcome outcome = invokeWithRetry(
                    contract,
                    run + ".step." + index,
                    token,
                    started,
                    overallTimeoutMillis
            );
            executed.add(actionId);
            if (!outcome.success()) {
                runOptional(
                        composite.failureActionId(),
                        run + ".failure",
                        token
                );
                boolean fallbackSuccess = runOptional(
                        composite.fallbackActionId(),
                        run + ".fallback",
                        token
                );
                if (!fallbackSuccess) {
                    compensate(
                            composite,
                            run,
                            token,
                            executed,
                            compensated
                    );
                }
                return new Result(
                        fallbackSuccess,
                        outcome.cancelled(),
                        false,
                        executed,
                        compensated,
                        fallbackSuccess
                                ? "FALLBACK_PASS"
                                : outcome.message()
                );
            }
            index++;
        }

        if (composite.successConditionId() != null
                && !conditions.evaluate(
                    composite.successConditionId()
                )) {
            compensate(
                    composite,
                    run,
                    token,
                    executed,
                    compensated
            );
            return new Result(
                    false,
                    false,
                    false,
                    executed,
                    compensated,
                    "SUCCESS_CONDITION_FAILED"
            );
        }

        return new Result(
                true,
                false,
                false,
                executed,
                compensated,
                "PASS"
        );
    }

    private Outcome invokeWithRetry(
            ActionContract contract,
            String executionPrefix,
            CancellationToken token,
            long started,
            long overallTimeoutMillis
    ) {
        int attempts = contract.retryCount() + 1;
        Outcome last = Outcome.failed("ACTION_NOT_RUN");
        for (int attempt = 0; attempt < attempts; attempt++) {
            if (token.isCancelled()) {
                return Outcome.cancelled(
                        "ACTION_CANCELLED"
                );
            }
            if (timedOut(started, overallTimeoutMillis)) {
                return Outcome.failed(
                        "ACTION_OVERALL_TIMEOUT"
                );
            }
            String executionId = StableId.require(
                    executionPrefix + ".try." + attempt,
                    "executionId"
            );
            last = invoker.invoke(
                    contract,
                    executionId,
                    token
            );
            if (last.success() || last.cancelled()) {
                return last;
            }
            if (!contract.idempotent()) break;
        }
        return last;
    }

    private boolean runOptional(
            String actionId,
            String executionId,
            CancellationToken token
    ) {
        if (actionId == null) return false;
        ActionContract contract = requireAction(actionId);
        return invoker.invoke(
                contract,
                StableId.require(executionId, "executionId"),
                token
        ).success();
    }

    private void compensate(
            CompositeAction composite,
            String run,
            CancellationToken token,
            List<String> executed,
            List<String> compensated
    ) {
        String compensation = composite.compensationActionId();
        if (compensation == null || executed.isEmpty()) return;

        ActionContract contract = requireAction(compensation);
        for (int index = executed.size() - 1;
                index >= 0;
                index--) {
            if (token.isCancelled()) return;
            String executionId = StableId.require(
                    run + ".comp." + index,
                    "executionId"
            );
            Outcome outcome = invoker.invoke(
                    contract,
                    executionId,
                    token
            );
            if (!outcome.success()) return;
            compensated.add(executed.get(index));
        }
    }

    private ActionContract requireAction(String actionId) {
        ActionContract contract = actions.resolve(actionId);
        if (contract == null || !contract.completeContract()) {
            throw new IllegalArgumentException(
                    "composite action unavailable:"
                            + actionId
            );
        }
        return contract;
    }

    private boolean timedOut(
            long started,
            long timeout
    ) {
        long now = clock.nowMillis();
        return now < started || now - started > timeout;
    }
}
