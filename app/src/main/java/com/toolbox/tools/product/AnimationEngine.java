package com.toolbox.tools.product;

import com.toolbox.tools.core.StableId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class AnimationEngine {
    public enum Kind { FADE, SLIDE, SCALE, ROTATE }
    public enum Easing { LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT }

    public static final class Animation {
        private final String id;
        private final Kind kind;
        private final String triggerId;
        private final long durationMs;
        private final long delayMs;
        private final Easing easing;

        public Animation(
                String id,
                Kind kind,
                String triggerId,
                long durationMs,
                long delayMs,
                Easing easing
        ) {
            this.id = StableId.require(id, "animationId");
            this.kind = Objects.requireNonNull(kind, "kind");
            this.triggerId = StableId.require(triggerId, "triggerId");
            if (durationMs < 1 || durationMs > 60_000 || delayMs < 0 || delayMs > 60_000) {
                throw new IllegalArgumentException("waktu animasi di luar batas");
            }
            this.durationMs = durationMs;
            this.delayMs = delayMs;
            this.easing = Objects.requireNonNull(easing, "easing");
        }

        public String id() { return id; }
        public Kind kind() { return kind; }
        public String triggerId() { return triggerId; }
        public long durationMs() { return durationMs; }
        public long delayMs() { return delayMs; }
        public Easing easing() { return easing; }
    }

    private final List<Animation> animations = new ArrayList<>();

    public synchronized void register(Animation animation) {
        Objects.requireNonNull(animation, "animation");
        for (Animation item : animations) {
            if (item.id().equals(animation.id())) throw new IllegalArgumentException("animasi duplikat");
        }
        animations.add(animation);
    }

    public synchronized List<Animation> all() {
        return Collections.unmodifiableList(new ArrayList<>(animations));
    }
}
