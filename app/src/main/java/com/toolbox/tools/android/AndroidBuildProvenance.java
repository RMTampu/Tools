package com.toolbox.tools.android;

import com.toolbox.tools.BuildConfig;
import com.toolbox.tools.build.BuildProvenance;

public final class AndroidBuildProvenance {
    private AndroidBuildProvenance() {}

    public static BuildProvenance current() {
        return new BuildProvenance(
                BuildConfig.SOURCE_REPOSITORY,
                BuildConfig.SOURCE_COMMIT_SHA,
                BuildConfig.SOURCE_REF,
                BuildConfig.CI_REPOSITORY,
                BuildConfig.CI_WORKFLOW_REF,
                BuildConfig.TOOLCHAIN_LOCK,
                BuildConfig.EXPECTED_SIGNER_SHA256,
                BuildConfig.BASELINE_APK_SHA256
        );
    }
}
