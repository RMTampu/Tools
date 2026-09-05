package com.toolbox.tools.authoring;

import com.toolbox.tools.library.AssetRegistry;
import com.toolbox.tools.library.CatalogLifecycle;
import com.toolbox.tools.library.ComponentRegistry;
import com.toolbox.tools.library.DependencyResolutionResult;
import com.toolbox.tools.library.DependencyResolver;
import com.toolbox.tools.library.TemplateDefinition;
import com.toolbox.tools.library.TemplateInstantiationPlan;
import com.toolbox.tools.library.TemplateRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class TemplateAuthoringService {
    private final AuthoringDraftStore drafts;
    private final TemplateRegistry templates;
    private final ComponentRegistry components;
    private final AssetRegistry assets;
    private final DependencyResolver resolver;
    private final TemplateArchiveStore archive;

    public TemplateAuthoringService(
            AuthoringDraftStore drafts,
            TemplateRegistry templates,
            ComponentRegistry components,
            AssetRegistry assets,
            DependencyResolver resolver
    ) {
        this(
                drafts,
                templates,
                components,
                assets,
                resolver,
                null
        );
    }

    public TemplateAuthoringService(
            AuthoringDraftStore drafts,
            TemplateRegistry templates,
            ComponentRegistry components,
            AssetRegistry assets,
            DependencyResolver resolver,
            TemplateArchiveStore archive
    ) {
        this.drafts = Objects.requireNonNull(drafts, "drafts");
        this.templates = Objects.requireNonNull(templates, "templates");
        this.components = Objects.requireNonNull(components, "components");
        this.assets = Objects.requireNonNull(assets, "assets");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.archive = archive;
    }

    public TemplateAuthoringDraft create(TemplateAuthoringDraft draft) {
        Objects.requireNonNull(draft, "draft");
        drafts.create(
                draft.draftId(),
                AuthoringSection.UI,
                draft.templateId(),
                Collections.singletonMap(
                        "template.label",
                        draft.labelIndonesia()
                )
        );
        return draft;
    }

    public TemplateAuthoringValidation validate(TemplateAuthoringDraft draft) {
        Objects.requireNonNull(draft, "draft");
        TemplateDefinition definition = toDefinition(draft);
        DependencyResolutionResult dependencies = resolver.resolveTemplate(
                definition,
                components,
                assets
        );
        List<String> issues = new ArrayList<>();
        if (!dependencies.isPass()) {
            issues.add(dependencies.message());
        }
        if (templates.resolveExact(
                draft.templateId(),
                draft.version()
        ) != null) {
            issues.add("TEMPLATE_VERSION_ALREADY_EXISTS");
        }
        if (issues.isEmpty()) {
            AuthoringDraft state = drafts.get(draft.draftId());
            if (state == null) {
                throw new IllegalStateException("template draft not registered");
            }
            if (state.lifecycle() == DraftLifecycle.DRAFT) {
                drafts.validate(draft.draftId());
            } else if (state.lifecycle() != DraftLifecycle.VALIDATED) {
                throw new IllegalStateException("template draft not validatable");
            }
        }
        return new TemplateAuthoringValidation(issues);
    }

    public TemplateInstantiationPlan preview(
            TemplateAuthoringDraft draft,
            String insertionId
    ) {
        TemplateAuthoringValidation validation = validate(draft);
        if (!validation.isPass()) {
            throw new IllegalArgumentException(validation.message());
        }
        return new TemplateInstantiationPlan(
                toDefinition(draft),
                insertionId
        );
    }

    public TemplateDefinition publish(TemplateAuthoringDraft draft) {
        TemplateAuthoringValidation validation = validate(draft);
        if (!validation.isPass()) {
            throw new IllegalArgumentException(validation.message());
        }
        AuthoringDraft state = drafts.get(draft.draftId());
        if (state.lifecycle() != DraftLifecycle.VALIDATED) {
            throw new IllegalStateException("template draft not validated");
        }
        TemplateDefinition definition = toDefinition(draft);
        TemplateDefinition readyDefinition = definition.withLifecycle(
                CatalogLifecycle.READY
        );
        boolean archived = false;
        try {
            if (archive != null) {
                archive.publish(readyDefinition);
                archived = true;
            }
            templates.publishReady(
                    definition,
                    resolver,
                    components,
                    assets
            );
            drafts.markPublished(draft.draftId());
            TemplateDefinition published = templates.resolveExact(
                    draft.templateId(),
                    draft.version()
            );
            if (published == null
                    || published.lifecycle() != CatalogLifecycle.READY) {
                throw new IllegalStateException(
                        "template publish not observable"
                );
            }
            if (archive != null && !archive.exists(published)) {
                throw new IllegalStateException(
                        "template visible archive missing"
                );
            }
            return published;
        } catch (java.io.IOException error) {
            if (archived) {
                try {
                    archive.delete(readyDefinition);
                } catch (java.io.IOException ignored) {
                    // Orphan archive remains visible and detectable;
                    // registry state is never silently fabricated.
                }
            }
            throw new IllegalStateException(
                    "template visible archive gagal",
                    error
            );
        } catch (RuntimeException error) {
            if (archived) {
                try {
                    archive.delete(readyDefinition);
                } catch (java.io.IOException ignored) {
                    // Fail closed; visible orphan is user-inspectable.
                }
            }
            throw error;
        }
    }

    private static TemplateDefinition toDefinition(
            TemplateAuthoringDraft draft
    ) {
        return new TemplateDefinition(
                draft.templateId(),
                draft.labelIndonesia(),
                draft.version(),
                CatalogLifecycle.DRAFT,
                draft.internalObjectIds(),
                draft.componentDependencies(),
                draft.assetDependencies()
        );
    }
}
