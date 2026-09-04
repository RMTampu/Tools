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

        AppKernel kernel = AppKernel.createPersistent(
                new File(getFilesDir(), "workspace-stage2.tbx")
        );
        VerificationManager verifier = new VerificationManager();
        String status = verifier.verify(kernel).isPass() ? "PASS" : "ERROR";

        TextView view = new TextView(this);
        view.setGravity(Gravity.CENTER);
        view.setText("ToolBox Stage 2\nWorkspace / Save / Revision / Recovery\n" + status);
        view.setTextSize(18f);
        setContentView(view);
    }
}
