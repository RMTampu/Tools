package com.toolbox.tools.authoring;

import com.toolbox.tools.library.LibraryItemType;
import com.toolbox.tools.library.LibraryManager;
import com.toolbox.tools.library.LibrarySearchResult;
import com.toolbox.tools.runtime.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class AuthoringSearchIndex {
    private final LibraryManager library;
    private final RuntimeEnvironment runtime;

    public AuthoringSearchIndex(
            LibraryManager library,
            RuntimeEnvironment runtime
    ) {
        this.library = Objects.requireNonNull(library, "library");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    public List<AuthoringSearchResult> search(AuthoringSearchQuery query) {
        Objects.requireNonNull(query, "query");
        String needle = query.text().toLowerCase(Locale.ROOT);
        List<AuthoringSearchResult> all = new ArrayList<>();

        for (LibrarySearchResult result : library.search(query.text())) {
            AuthoringItemKind kind = fromLibrary(result.key().type());
            if (!query.accepts(kind)) continue;
            addIfMatches(
                    all,
                    needle,
                    new AuthoringSearchResult(
                            kind,
                            result.key().stableId(),
                            result.labelIndonesia(),
                            result.key().version().toString()
                    )
            );
        }

        if (query.accepts(AuthoringItemKind.SCREEN)) {
            for (com.toolbox.tools.runtime.ScreenDefinition screen
                    : runtime.model().screens().values()) {
                addIfMatches(all, needle, new AuthoringSearchResult(
                        AuthoringItemKind.SCREEN,
                        screen.screenId(),
                        screen.labelIndonesia(),
                        null
                ));
            }
        }

        if (query.accepts(AuthoringItemKind.FLOW)) {
            for (com.toolbox.tools.runtime.FlowGraph flow
                    : runtime.model().flows().values()) {
                addIfMatches(all, needle, new AuthoringSearchResult(
                        AuthoringItemKind.FLOW,
                        flow.flowId(),
                        "Flow " + flow.flowId(),
                        null
                ));
            }
        }

        if (query.accepts(AuthoringItemKind.DATA_SOURCE)) {
            for (com.toolbox.tools.runtime.DataSourceDefinition source
                    : runtime.model().dataSources().values()) {
                addIfMatches(all, needle, new AuthoringSearchResult(
                        AuthoringItemKind.DATA_SOURCE,
                        source.sourceId(),
                        "Sumber Data " + source.sourceId(),
                        null
                ));
            }
        }

        if (query.accepts(AuthoringItemKind.BINDING)) {
            for (com.toolbox.tools.runtime.BindingDefinition binding
                    : runtime.model().bindings().values()) {
                addIfMatches(all, needle, new AuthoringSearchResult(
                        AuthoringItemKind.BINDING,
                        binding.bindingId(),
                        "Binding " + binding.bindingId(),
                        null
                ));
            }
        }

        if (query.accepts(AuthoringItemKind.ACTION)) {
            for (Map.Entry<String, com.toolbox.tools.runtime.ActionContract> entry
                    : runtime.actions().all().entrySet()) {
                addIfMatches(all, needle, new AuthoringSearchResult(
                        AuthoringItemKind.ACTION,
                        entry.getKey(),
                        "Action " + entry.getKey(),
                        null
                ));
            }
        }

        if (query.accepts(AuthoringItemKind.EVENT)) {
            for (com.toolbox.tools.runtime.EventDefinition event
                    : runtime.model().events().values()) {
                addIfMatches(all, needle, new AuthoringSearchResult(
                        AuthoringItemKind.EVENT,
                        event.eventId(),
                        "Event " + event.eventId(),
                        null
                ));
            }
        }

        all.sort(Comparator
                .comparing(AuthoringSearchResult::kind)
                .thenComparing(AuthoringSearchResult::stableId)
                .thenComparing(result -> result.version() == null ? "" : result.version()));

        List<AuthoringSearchResult> deduplicated = new ArrayList<>();
        String last = null;
        for (AuthoringSearchResult result : all) {
            if (result.stableKey().equals(last)) continue;
            deduplicated.add(result);
            last = result.stableKey();
            if (deduplicated.size() >= query.limit()) break;
        }
        return Collections.unmodifiableList(deduplicated);
    }

    private static void addIfMatches(
            List<AuthoringSearchResult> out,
            String needle,
            AuthoringSearchResult result
    ) {
        if (needle.isEmpty()
                || result.stableId().toLowerCase(Locale.ROOT).contains(needle)
                || result.labelIndonesia().toLowerCase(Locale.ROOT).contains(needle)) {
            out.add(result);
        }
    }

    private static AuthoringItemKind fromLibrary(LibraryItemType type) {
        switch (type) {
            case COMPONENT: return AuthoringItemKind.COMPONENT;
            case TEMPLATE: return AuthoringItemKind.TEMPLATE;
            case ASSET: return AuthoringItemKind.ASSET;
            default: throw new IllegalStateException("unknown library item type");
        }
    }
}
