package com.toolbox.tools;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

import com.toolbox.tools.core.AppKernel;
import com.toolbox.tools.core.VerificationManager;

import java.io.File;

public final class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        File projectRoot = new File(getFilesDir(), "projects/project.default");
        File assetLibraryRoot = new File(getFilesDir(), "library/assets");
        AppKernel kernel = AppKernel.createPersistent(
                projectRoot,
                assetLibraryRoot
        );
        VerificationManager verifier = new VerificationManager();
        String status = verifier.verify(kernel).isPass() ? "LULUS" : "GAGAL";

        TextView view = new TextView(this);
        view.setGravity(Gravity.CENTER);
        view.setText(
                "ToolBox Tahap 3\n"
                        + "Komponen • Asset • Template • Library\n"
                        + status
        );
        view.setTextSize(18f);
        setContentView(view);
    }
}
