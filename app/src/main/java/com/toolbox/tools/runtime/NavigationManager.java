package com.toolbox.tools.runtime;

import com.toolbox.tools.core.StableId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class NavigationManager {
    public static final int MAX_BACK_STACK = 64;

    private final SharedRuntimeModel model;
    private final Map<String, NavigationRoute> routes;
    private final Deque<BackStackEntry> backStack = new ArrayDeque<>();
    private BackStackEntry current;

    public NavigationManager(
            SharedRuntimeModel model,
            List<NavigationRoute> routes
    ) {
        this.model = Objects.requireNonNull(model, "model");
        LinkedHashMap<String, NavigationRoute> copy = new LinkedHashMap<>();
        for (NavigationRoute route : routes == null
                ? Collections.<NavigationRoute>emptyList()
                : routes) {
            if (copy.put(route.routeId(), route) != null) {
                throw new IllegalArgumentException("duplicate route");
            }
        }
        this.routes = Collections.unmodifiableMap(copy);
        this.current = new BackStackEntry(
                model.startScreenId(),
                Collections.emptyMap()
        );
    }

    public synchronized List<RuntimeDiagnostic> validateRoutes() {
        List<RuntimeDiagnostic> out = new ArrayList<>();
        for (NavigationRoute route : routes.values()) {
            if (model.screen(route.targetScreenId()) == null) {
                out.add(new RuntimeDiagnostic(
                        DiagnosticCode.BROKEN_NAVIGATION_REFERENCE,
                        route.routeId(),
                        "Target screen unavailable"
                ));
            }
        }
        return Collections.unmodifiableList(out);
    }

    public synchronized BackStackEntry navigate(
            String routeId,
            Map<String, String> parameters
    ) {
        NavigationRoute route = routes.get(StableId.require(routeId, "routeId"));
        if (route == null || model.screen(route.targetScreenId()) == null) {
            throw new IllegalArgumentException("BROKEN_NAVIGATION_REFERENCE");
        }
        Map<String, String> args = parameters == null
                ? Collections.emptyMap()
                : parameters;
        if (!route.parameters().keySet().equals(args.keySet())) {
            throw new IllegalArgumentException("navigation parameter mismatch");
        }

        backStack.addLast(current);
        while (backStack.size() > MAX_BACK_STACK) {
            backStack.removeFirst();
        }
        current = new BackStackEntry(route.targetScreenId(), args);
        return current;
    }

    public synchronized BackStackEntry back() {
        if (backStack.isEmpty()) return current;
        current = backStack.removeLast();
        return current;
    }

    public synchronized BackStackEntry current() { return current; }

    public synchronized List<BackStackEntry> backStackSnapshot() {
        return Collections.unmodifiableList(new ArrayList<>(backStack));
    }
}
