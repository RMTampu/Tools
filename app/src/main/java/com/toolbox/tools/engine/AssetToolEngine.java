package com.toolbox.tools.engine;

import com.toolbox.tools.core.EngineContract;
import com.toolbox.tools.library.AssetStore;
import com.toolbox.tools.library.LibraryManager;
import java.util.Objects;

public final class AssetToolEngine implements EngineContract {
    private final LibraryManager library;
    private final AssetStore store;

    public AssetToolEngine(LibraryManager library, AssetStore store) {
        this.library = Objects.requireNonNull(library, "library");
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override public String id() { return "engine.asset"; }
    @Override public boolean isReady() {
        return library.assets() != null
                && library.templates() != null
                && library.components() != null
                && store != null;
    }

    public int jumlahAsetSiap() { return library.assets().allReady().size(); }
    public int jumlahTemplateSiap() { return library.templates().allReady().size(); }
}
