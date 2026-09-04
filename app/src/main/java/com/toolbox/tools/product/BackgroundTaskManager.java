package com.toolbox.tools.product;

import com.toolbox.tools.core.StableId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BackgroundTaskManager {
    public enum State {
        QUEUED, RUNNING, PAUSED, SUCCESS, FAILED, CANCELLED
    }

    public static final class Task {
        private final String id;
        private final State state;
        private final int progress;

        Task(String id, State state, int progress) {
            this.id = id;
            this.state = state;
            this.progress = progress;
        }

        public String id() { return id; }
        public State state() { return state; }
        public int progress() { return progress; }
    }

    private final Map<String, Task> tasks = new LinkedHashMap<>();

    public synchronized void queue(String id) {
        String stable = StableId.require(id, "taskId");
        if (tasks.containsKey(stable)) {
            throw new IllegalArgumentException("tugas sudah ada");
        }
        tasks.put(stable, new Task(stable, State.QUEUED, 0));
    }

    public synchronized void update(String id, State state, int progress) {
        String stable = StableId.require(id, "taskId");
        if (!tasks.containsKey(stable)) {
            throw new IllegalArgumentException("tugas tidak tersedia");
        }
        if (progress < 0 || progress > 100) {
            throw new IllegalArgumentException("progress tidak valid");
        }
        tasks.put(stable, new Task(stable, state, progress));
    }

    public synchronized List<Task> all() {
        return Collections.unmodifiableList(new ArrayList<>(tasks.values()));
    }
}
