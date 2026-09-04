package com.toolbox.tools.engine;

import com.toolbox.tools.core.EngineManager;
import com.toolbox.tools.core.ToolDescriptor;
import com.toolbox.tools.core.ToolRegistry;
import com.toolbox.tools.editor.EditorEnvironment;
import com.toolbox.tools.library.AssetStore;
import com.toolbox.tools.library.LibraryManager;
import com.toolbox.tools.runtime.RuntimeEnvironment;
import java.util.Objects;

public final class ProductEngineSuite {
    private final UiToolEngine ui;
    private final LogicToolEngine logic;
    private final DataToolEngine data;
    private final BindingToolEngine binding;
    private final AssetToolEngine asset;

    private ProductEngineSuite(
            UiToolEngine ui,
            LogicToolEngine logic,
            DataToolEngine data,
            BindingToolEngine binding,
            AssetToolEngine asset
    ) {
        this.ui = ui;
        this.logic = logic;
        this.data = data;
        this.binding = binding;
        this.asset = asset;
    }

    public static ProductEngineSuite register(
            ToolRegistry tools,
            EngineManager engines,
            EditorEnvironment editor,
            RuntimeEnvironment runtime,
            LibraryManager library,
            AssetStore assetStore
    ) {
        Objects.requireNonNull(tools, "tools");
        Objects.requireNonNull(engines, "engines");

        ProductEngineSuite suite = new ProductEngineSuite(
                new UiToolEngine(editor, runtime, library),
                new LogicToolEngine(runtime),
                new DataToolEngine(runtime),
                new BindingToolEngine(runtime),
                new AssetToolEngine(library, assetStore)
        );

        tools.register(new ToolDescriptor("tool.ui", "UI", "12.0"));
        tools.register(new ToolDescriptor("tool.logic", "Logika", "12.0"));
        tools.register(new ToolDescriptor("tool.data", "Data", "12.0"));
        tools.register(new ToolDescriptor("tool.binding", "Binding", "12.0"));
        tools.register(new ToolDescriptor("tool.asset", "Aset", "12.0"));

        engines.register(suite.ui);
        engines.register(suite.logic);
        engines.register(suite.data);
        engines.register(suite.binding);
        engines.register(suite.asset);
        return suite;
    }

    public UiToolEngine ui() { return ui; }
    public LogicToolEngine logic() { return logic; }
    public DataToolEngine data() { return data; }
    public BindingToolEngine binding() { return binding; }
    public AssetToolEngine asset() { return asset; }

    public boolean semuaSiap() {
        return ui.isReady()
                && logic.isReady()
                && data.isReady()
                && binding.isReady()
                && asset.isReady();
    }
}
