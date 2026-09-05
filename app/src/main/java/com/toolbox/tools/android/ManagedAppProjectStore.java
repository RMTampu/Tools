package com.toolbox.tools.android;

import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.Bundle;

import com.toolbox.tools.core.ProjectAccessStatus;
import com.toolbox.tools.core.ProjectCodec;
import com.toolbox.tools.core.ProjectLoadResult;
import com.toolbox.tools.core.ProjectState;
import com.toolbox.tools.core.ProjectStore;
import com.toolbox.tools.core.RecoveryCandidate;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ManagedAppProjectStore implements ProjectStore {
    private final ContentResolver resolver;
    private final Uri providerUri;
    private final String targetPackage;
    private final String expectedProjectId;
    private final ProjectCodec codec = new ProjectCodec();

    public ManagedAppProjectStore(
            ContentResolver resolver,
            PackageManager packageManager,
            String targetPackage,
            String providerAuthority,
            String expectedProjectId
    ) {
        this.resolver = Objects.requireNonNull(
                resolver,
                "resolver"
        );
        Objects.requireNonNull(packageManager, "packageManager");
        this.targetPackage = requirePackage(targetPackage);
        this.expectedProjectId = com.toolbox.tools.core.StableId.require(
                expectedProjectId,
                "projectId"
        );
        if (providerAuthority == null
                || !providerAuthority.matches(
                        "[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"
                )) {
            throw new IllegalArgumentException(
                    "provider authority invalid"
            );
        }
        ProviderInfo provider = packageManager.resolveContentProvider(
                providerAuthority,
                PackageManager.GET_META_DATA
        );
        if (provider == null
                || !provider.exported
                || !this.targetPackage.equals(provider.packageName)) {
            throw new IllegalArgumentException(
                    "managed project provider unavailable"
            );
        }
        this.providerUri = Uri.parse(
                "content://" + providerAuthority + "/toolbox"
        );
        verifyDescribe();
    }

    @Override
    public synchronized ProjectLoadResult load(String projectId)
            throws IOException {
        requireProject(projectId);
        Bundle extras = new Bundle();
        extras.putString(
                ManagedProjectProviderContract.EXTRA_PROJECT_ID,
                expectedProjectId
        );
        Bundle result = call(
                ManagedProjectProviderContract.METHOD_LOAD,
                extras
        );
        String payload = result.getString(
                ManagedProjectProviderContract.EXTRA_PROJECT_PAYLOAD
        );
        if (payload == null || payload.trim().isEmpty()) {
            return new ProjectLoadResult(
                    ProjectAccessStatus.FOLDER_MISSING,
                    null,
                    Collections.emptyList()
            );
        }
        ProjectState state = decode(payload);
        return new ProjectLoadResult(
                ProjectAccessStatus.PROJECT_OK,
                state,
                Collections.emptyList()
        );
    }

    @Override
    public synchronized ProjectState commit(
            ProjectState workingState,
            long expectedRevision
    ) throws IOException {
        requireState(workingState);
        if (expectedRevision < 0) {
            throw new IOException("expected revision invalid");
        }
        Bundle extras = new Bundle();
        extras.putString(
                ManagedProjectProviderContract.EXTRA_PROJECT_ID,
                expectedProjectId
        );
        extras.putLong(
                ManagedProjectProviderContract.EXTRA_EXPECTED_REVISION,
                expectedRevision
        );
        extras.putString(
                ManagedProjectProviderContract.EXTRA_PROJECT_PAYLOAD,
                codec.encode(workingState)
        );
        ProjectState committed = decode(
                requirePayload(
                        call(
                                ManagedProjectProviderContract
                                        .METHOD_COMMIT,
                                extras
                        )
                )
        );
        if (committed.revision() != expectedRevision + 1) {
            throw new IOException(
                    "managed target commit revision mismatch"
            );
        }
        return committed;
    }

    @Override
    public synchronized ProjectState recoverRevision(long revision)
            throws IOException {
        if (revision <= 0) throw new IOException("revision invalid");
        Bundle extras = revisionExtras(revision);
        return decode(
                requirePayload(
                        call(
                                ManagedProjectProviderContract
                                        .METHOD_RECOVER_REVISION,
                                extras
                        )
                )
        );
    }

    @Override
    public synchronized ProjectState recoverState(
            ProjectState candidate
    ) throws IOException {
        requireState(candidate);
        Bundle extras = new Bundle();
        extras.putString(
                ManagedProjectProviderContract.EXTRA_PROJECT_ID,
                expectedProjectId
        );
        extras.putString(
                ManagedProjectProviderContract.EXTRA_PROJECT_PAYLOAD,
                codec.encode(candidate)
        );
        return decode(
                requirePayload(
                        call(
                                ManagedProjectProviderContract
                                        .METHOD_RECOVER_STATE,
                                extras
                        )
                )
        );
    }

    @Override
    public synchronized ProjectState loadRevision(long revision)
            throws IOException {
        if (revision <= 0) throw new IOException("revision invalid");
        return decode(
                requirePayload(
                        call(
                                ManagedProjectProviderContract
                                        .METHOD_LOAD_REVISION,
                                revisionExtras(revision)
                        )
                )
        );
    }

    @Override
    public synchronized List<RecoveryCandidate> recoveryCandidates() {
        // Remote target owns its internal history. ToolBox never invents
        // recovery entries that the target did not expose.
        return Collections.emptyList();
    }

    public String targetPackage() {
        return targetPackage;
    }

    public String projectId() {
        return expectedProjectId;
    }

    private void verifyDescribe() {
        Bundle extras = new Bundle();
        extras.putString(
                ManagedProjectProviderContract.EXTRA_PROJECT_ID,
                expectedProjectId
        );
        try {
            Bundle result = call(
                    ManagedProjectProviderContract.METHOD_DESCRIBE,
                    extras
            );
            if (result.getInt(
                    ManagedProjectProviderContract.EXTRA_PROTOCOL_VERSION,
                    -1
            ) != ManagedProjectProviderContract.VERSION
                    || !result.getBoolean(
                            ManagedProjectProviderContract.EXTRA_WRITABLE,
                            false
                    )) {
                throw new IllegalArgumentException(
                        "managed project provider not writable"
                );
            }
        } catch (IOException error) {
            throw new IllegalArgumentException(
                    "managed project provider describe failed",
                    error
            );
        }
    }

    private Bundle revisionExtras(long revision) {
        Bundle extras = new Bundle();
        extras.putString(
                ManagedProjectProviderContract.EXTRA_PROJECT_ID,
                expectedProjectId
        );
        extras.putLong(
                ManagedProjectProviderContract.EXTRA_REVISION,
                revision
        );
        return extras;
    }

    private Bundle call(String method, Bundle extras)
            throws IOException {
        Bundle result;
        try {
            result = resolver.call(
                    providerUri,
                    method,
                    null,
                    extras
            );
        } catch (RuntimeException error) {
            throw new IOException(
                    "managed target bridge call failed:" + method,
                    error
            );
        }
        if (result == null) {
            throw new IOException(
                    "managed target bridge empty result:" + method
            );
        }
        String error = result.getString(
                ManagedProjectProviderContract.EXTRA_ERROR
        );
        if (error != null && !error.trim().isEmpty()) {
            throw new IOException(
                    "managed target rejected:" + error
            );
        }
        return result;
    }

    private String requirePayload(Bundle result) throws IOException {
        String payload = result.getString(
                ManagedProjectProviderContract.EXTRA_PROJECT_PAYLOAD
        );
        if (payload == null || payload.trim().isEmpty()) {
            throw new IOException("managed target payload missing");
        }
        return payload;
    }

    private ProjectState decode(String payload) throws IOException {
        try {
            ProjectState state = codec.decode(payload);
            requireState(state);
            return state;
        } catch (IllegalArgumentException error) {
            throw new IOException(
                    "managed target payload invalid",
                    error
            );
        }
    }

    private void requireState(ProjectState state) throws IOException {
        if (state == null
                || !expectedProjectId.equals(state.projectId())) {
            throw new IOException(
                    "managed target project identity mismatch"
            );
        }
    }

    private void requireProject(String projectId) throws IOException {
        if (!expectedProjectId.equals(projectId)) {
            throw new IOException(
                    "managed target project identity mismatch"
            );
        }
    }

    private static String requirePackage(String packageName) {
        if (packageName == null
                || !packageName.matches(
                        "[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+"
                )) {
            throw new IllegalArgumentException(
                    "target package invalid"
            );
        }
        return packageName;
    }
}
