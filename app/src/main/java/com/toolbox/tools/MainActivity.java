package com.toolbox.tools;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Window;

import com.toolbox.tools.android.SafProjectAccessGateway;
import com.toolbox.tools.android.ToolboxAwareTargetDiscovery;
import com.toolbox.tools.core.AppKernel;
import com.toolbox.tools.ui.StoragePickerHost;
import com.toolbox.tools.ui.UiKit;
import com.toolbox.tools.ui.WorkspaceShellView;

import java.io.File;

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

        File projectRoot = new File(
                getFilesDir(),
                "projects/project.default"
        );
        File assetLibraryRoot = new File(
                getFilesDir(),
                "library/assets"
        );

        kernel = AppKernel.createPersistent(
                projectRoot,
                assetLibraryRoot
        );

        restorePersistedTree();
        ToolboxAwareTargetDiscovery.discover(
                this,
                kernel.productServices().completion().installedTargets
        );

        shell = new WorkspaceShellView(
                this,
                kernel
        );
        setContentView(shell);
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
        return value == null
                ? "Belum dipilih"
                : "Terhubung";
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
            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit()
                    .putString(KEY_TREE_URI, treeUri.toString())
                    .apply();
            kernel.productServices().completion().storage.relink(
                    treeUri.toString(),
                    true,
                    true
            );
        } catch (RuntimeException ignored) {
            // Akses yang tidak lengkap tidak dipromosikan sebagai storage aktif.
        }
    }

    private void restorePersistedTree() {
        String value = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(KEY_TREE_URI, null);
        if (value == null) return;
        try {
            Uri uri = Uri.parse(value);
            if (safGateway.hasPersistedReadWriteAccess(
                    getContentResolver(),
                    uri
            )) {
                kernel.productServices().completion().storage.relink(
                        value,
                        true,
                        true
                );
            }
        } catch (RuntimeException ignored) {
            // Relink tetap tersedia dari Pengaturan bila grant hilang.
        }
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
