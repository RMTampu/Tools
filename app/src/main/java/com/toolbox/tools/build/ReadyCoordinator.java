package com.toolbox.tools.build;

import com.toolbox.tools.core.AppKernel;
import com.toolbox.tools.core.ProjectLifecycle;
import com.toolbox.tools.core.ProjectState;

import java.io.IOException;
import java.util.Objects;

public final class ReadyCoordinator {
    private final AppKernel kernel;
    private final BuildValidator validator;
    private final ApplicationIrBuilder irBuilder;

    public ReadyCoordinator(
            AppKernel kernel,
            BuildValidator validator,
            ApplicationIrBuilder irBuilder
    ) {
        this.kernel = Objects.requireNonNull(kernel, "kernel");
        this.validator = Objects.requireNonNull(
                validator,
                "validator"
        );
        this.irBuilder = Objects.requireNonNull(
                irBuilder,
                "irBuilder"
        );
    }

    public synchronized BuildValidationResult preview() {
        return validator.validate(kernel, false);
    }

    public synchronized ProjectState publishReady()
            throws IOException {
        BuildValidationResult preview = preview();
        if (!preview.isPass()) {
            throw new IllegalStateException(
                    "READY_VALIDATION_FAILED:" + preview.message()
            );
        }
        if (kernel.projectManager().hasUnsavedChanges()) {
            throw new IllegalStateException(
                    "READY_REQUIRES_CLEAN_PROJECT"
            );
        }

        if (kernel.projectManager().savedRevision() <= 0) {
            kernel.projectManager().save();
        }
        kernel.projectManager().captureFinalRecoverySnapshot();

        if (kernel.projectManager().current().lifecycle()
                != ProjectLifecycle.READY) {
            kernel.projectManager().setLifecycle(
                    ProjectLifecycle.READY
            );
            kernel.projectManager().save();
        }

        BuildValidationResult finalValidation =
                validator.validate(kernel, true);
        if (!finalValidation.isPass()) {
            throw new IOException(
                    "READY_FINAL_VALIDATION_FAILED:"
                            + finalValidation.message()
            );
        }
        return kernel.projectManager().current();
    }

    public synchronized ApplicationIr buildIr() {
        BuildValidationResult result =
                validator.validate(kernel, true);
        if (!result.isPass()) {
            throw new IllegalStateException(
                    "IR_REQUIRES_READY:" + result.message()
            );
        }
        return irBuilder.build(kernel);
    }
}
