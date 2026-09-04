package com.toolbox.tools.library;

public final class DefaultLibraryFactory {
    private DefaultLibraryFactory() {}

    public static LibraryManager create() {
        ComponentRegistry components = new ComponentRegistry();
        AssetRegistry assets = new AssetRegistry();
        TemplateRegistry templates = new TemplateRegistry();
        DependencyResolver resolver = new DependencyResolver();
        ComponentValidator validator = new ComponentValidator();

        for (ComponentDefinition draft : BuiltinComponentCatalog.components()) {
            components.publishReady(draft, validator, assets);
        }

        for (TemplateDefinition draft : BuiltinComponentCatalog.templates()) {
            templates.publishReady(
                    draft,
                    resolver,
                    components,
                    assets
            );
        }

        return new LibraryManager(components, assets, templates);
    }
}
