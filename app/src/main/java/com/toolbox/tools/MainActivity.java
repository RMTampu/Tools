package com.toolbox.tools;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Window;

import com.toolbox.tools.core.AppKernel;
import com.toolbox.tools.ui.UiKit;
import com.toolbox.tools.ui.WorkspaceShellView;

import java.io.File;

public final class MainActivity extends Activity {
    private AppKernel kernel;

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

        WorkspaceShellView shell = new WorkspaceShellView(
                this,
                kernel
        );
        setContentView(shell);
    }

    public AppKernel kernelForTest() {
        return kernel;
    }
}
