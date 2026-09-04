package com.toolbox.tools.library;

import com.toolbox.tools.core.StableId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class LibraryManager {
    public static final int MAX_RECENT = 32;

    private final ComponentRegistry components;
    private final AssetRegistry assets;
    private final TemplateRegistry templates;
    private final Set<LibraryKey> favorites = new LinkedHashSet<>();
    private final Deque<LibraryKey> recent = new ArrayDeque<>();

    public LibraryManager(
            ComponentRegistry components,
            AssetRegistry assets,
            TemplateRegistry templates
    ) {
        this.components = components;
        this.assets = assets;
        this.templates = templates;
    }

    public synchronized Object resolveExact(LibraryKey key) {
        switch (key.type()) {
            case COMPONENT:
                return components.resolveExact(key.stableId(), key.version());
            case ASSET:
                return assets.resolveExact(key.stableId(), key.version());
            case TEMPLATE:
                return templates.resolveExact(key.stableId(), key.version());
            default:
                throw new IllegalStateException("unknown library type");
        }
    }

    public synchronized void markFavorite(LibraryKey key, boolean favorite) {
        requireExisting(key);
        if (favorite) favorites.add(key);
        else favorites.remove(key);
    }

    public synchronized void markRecent(LibraryKey key) {
        requireExisting(key);
        recent.remove(key);
        recent.addFirst(key);
        while (recent.size() > MAX_RECENT) recent.removeLast();
    }

    public synchronized Set<LibraryKey> favorites() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(favorites));
    }

    public synchronized List<LibraryKey> recent() {
        return Collections.unmodifiableList(new ArrayList<>(recent));
    }

    public synchronized List<LibrarySearchResult> search(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<LibrarySearchResult> out = new ArrayList<>();

        for (ComponentDefinition item : components.allReady()) {
            if (matches(needle, item.componentId(), item.labelIndonesia())) {
                out.add(new LibrarySearchResult(
                        new LibraryKey(
                                LibraryItemType.COMPONENT,
                                item.componentId(),
                                item.version()
                        ),
                        item.labelIndonesia()
                ));
            }
        }

        for (AssetDescriptor item : assets.allReady()) {
            if (matches(needle, item.assetId(), item.sourceName())) {
                out.add(new LibrarySearchResult(
                        new LibraryKey(
                                LibraryItemType.ASSET,
                                item.assetId(),
                                item.version()
                        ),
                        item.sourceName()
                ));
            }
        }

        for (TemplateDefinition item : templates.allReady()) {
            if (matches(needle, item.templateId(), item.labelIndonesia())) {
                out.add(new LibrarySearchResult(
                        new LibraryKey(
                                LibraryItemType.TEMPLATE,
                                item.templateId(),
                                item.version()
                        ),
                        item.labelIndonesia()
                ));
            }
        }

        out.sort((a, b) -> {
            int type = a.key().type().compareTo(b.key().type());
            if (type != 0) return type;
            int id = a.key().stableId().compareTo(b.key().stableId());
            if (id != 0) return id;
            return a.key().version().compareTo(b.key().version());
        });
        return Collections.unmodifiableList(out);
    }

    public ComponentRegistry components() { return components; }
    public AssetRegistry assets() { return assets; }
    public TemplateRegistry templates() { return templates; }

    private void requireExisting(LibraryKey key) {
        StableId.require(key.stableId(), "stableId");
        if (resolveExact(key) == null) {
            throw new IllegalArgumentException("library item unavailable");
        }
    }

    private static boolean matches(String needle, String id, String label) {
        if (needle.isEmpty()) return true;
        return id.toLowerCase(Locale.ROOT).contains(needle)
                || label.toLowerCase(Locale.ROOT).contains(needle);
    }
}
