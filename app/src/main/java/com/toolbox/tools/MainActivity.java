package com.toolbox.tools;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Window;

import com.toolbox.tools.android.SafProjectAccessGateway;
import com.toolbox.tools.android.SafProjectStore;
import com.toolbox.tools.android.ToolboxAwareTargetDiscovery;
import com.toolbox.tools.core.AppKernel;
import com.toolbox.tools.core.ProjectAccessStatus;
import com.toolbox.tools.core.ProjectLoadResult;
import com.toolbox.tools.ui.StoragePickerHost;
import com.toolbox.tools.ui.UiKit;
import com.toolbox.tools.ui.WorkspaceShellView;

import java.io.File;
import java.io.IOException;

public final class MainActivity extends Activity implements StoragePickerHost {
    private static final int REQUEST_TOOLBOX_TREE = 4301;
    private static final String PREFS = "toolbox.storage";
    private static final String KEY_TREE_URI = "project.tree.uri";

    private AppKernel kernel;
    private WorkspaceShellView shell;
    private final SafProjectAccessGateway safGateway = new SafProjectAccessGateway();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(UiKit.LATAR);
        window.setNavigationBarColor(UiKit.LATAR);

        kernel = createKernelFromRememberedStorage();
        discoverAwareTargets();
        renderShell();
    }

    @Override
    public void requestToolBoxStorageTree() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        );
        startActivityForResult(intent, REQUEST_TOOLBOX_TREE);
    }

    @Override
    public String storageTreeStatus() {
        String value = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(KEY_TREE_URI, null);
        if (value == null) return "Belum dipilih";
        try {
            return safGateway.hasPersistedReadWriteAccess(
                    getContentResolver(),
                    Uri.parse(value)
            ) ? "Terhubung • source of truth aktif"
                    : "Akses hilang • relink diperlukan";
        } catch (RuntimeException error) {
            return "Akses hilang • relink diperlukan";
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_TOOLBOX_TREE
                || resultCode != RESULT_OK
                || data == null
                || data.getData() == null) {
            return;
        }
        Uri treeUri = data.getData();
        try {
            safGateway.persistReadWriteAccess(
                    getContentResolver(),
                    treeUri,
                    data.getFlags()
            );

            SafProjectStore selected = new SafProjectStore(
                    getContentResolver(),
                    treeUri
            );
            ProjectLoadResult existing = selected.load(
                    "project.default"
            );
            if (existing.status() == ProjectAccessStatus.FOLDER_MISSING
                    && kernel != null) {
                selected.commit(
                        kernel.projectManager().current(),
                        0
                );
            }

            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit()
                    .putString(KEY_TREE_URI, treeUri.toString())
                    .apply();

            kernel = AppKernel.createPersistent(
                    selected,
                    privateProjectRoot(),
                    assetLibraryRoot()
            );
            kernel.productServices().completion().storage.relink(
                    treeUri.toString(),
                    true,
                    true
            );
            discoverAwareTargets();
            renderShell();
        } catch (IOException | RuntimeException ignored) {
            // Grant/project lama tetap utuh bila pemindahan gagal.
        }
    }

    private AppKernel createKernelFromRememberedStorage() {
        String value = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(KEY_TREE_URI, null);
        if (value != null) {
            try {
                Uri treeUri = Uri.parse(value);
                if (safGateway.hasPersistedReadWriteAccess(
                        getContentResolver(),
                        treeUri
                )) {
                    AppKernel result = AppKernel.createPersistent(
                            new SafProjectStore(
                                    getContentResolver(),
                                    treeUri
                            ),
                            privateProjectRoot(),
                            assetLibraryRoot()
                    );
                    result.productServices().completion().storage.relink(
                            value,
                            true,
                            true
                    );
                    return result;
                }
            } catch (RuntimeException ignored) {
                // Fallback app-private dipakai sampai user melakukan relink.
            }
        }
        return AppKernel.createPersistent(
                fallbackProjectRoot(),
                assetLibraryRoot()
        );
    }

    private void discoverAwareTargets() {
        ToolboxAwareTargetDiscovery.discover(
                this,
                kernel.productServices().completion().installedTargets
        );
    }

    private void renderShell() {
        shell = new WorkspaceShellView(this, kernel);
        setContentView(shell);
    }

    private File fallbackProjectRoot() {
        return new File(
                getFilesDir(),
                "projects/project.default"
        );
    }

    private File privateProjectRoot() {
        return new File(
                getFilesDir(),
                "projects/private.project.default"
        );
    }

    private File assetLibraryRoot() {
        return new File(
                getFilesDir(),
                "library/assets"
        );
    }

    @Override
    public void onBackPressed() {
        if (shell != null && shell.handleBack()) {
            return;
        }
        super.onBackPressed();
    }

    public AppKernel kernelForTest() {
        return kernel;
    }
}
