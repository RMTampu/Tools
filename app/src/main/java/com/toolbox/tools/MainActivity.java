package com.toolbox.tools;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;

import com.toolbox.tools.core.AppKernel;
import com.toolbox.tools.ui.UiKit;
import com.toolbox.tools.ui.WorkspaceShellView;

import java.io.File;

public final class MainActivity extends Activity {
    private AppKernel kernel;
    private WorkspaceShellView shell;

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

        shell = new WorkspaceShellView(
                this,
                kernel
        );
        setContentView(shell);
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
