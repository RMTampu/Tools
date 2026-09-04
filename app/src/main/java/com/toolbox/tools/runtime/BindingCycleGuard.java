package com.toolbox.tools.runtime;

import java.util.LinkedHashSet;
import java.util.Set;

public final class BindingCycleGuard {
    public static final int MAX_ACTIVE_TOKENS = 256;
    private final Set<String> active = new LinkedHashSet<>();

    public synchronized boolean enter(ChangeToken token) {
        String key = token.fingerprint();
        if (active.contains(key)) {
            return false;
        }
        if (active.size() >= MAX_ACTIVE_TOKENS) {
            throw new IllegalStateException("binding cycle guard budget exceeded");
        }
        active.add(key);
        return true;
    }

    public synchronized void exit(ChangeToken token) {
        active.remove(token.fingerprint());
    }

    public synchronized int activeCount() {
        return active.size();
    }
}
