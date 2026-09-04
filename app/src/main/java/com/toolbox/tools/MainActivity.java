package com.toolbox.tools;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

import com.toolbox.tools.core.AppKernel;
import com.toolbox.tools.core.VerificationManager;

public final class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AppKernel kernel = AppKernel.createDefault();
        VerificationManager verifier = new VerificationManager();
        String status = verifier.verify(kernel).isPass() ? "PASS" : "ERROR";

        TextView view = new TextView(this);
        view.setGravity(Gravity.CENTER);
        view.setText("ToolBox Foundation Stage 1\n" + status);
        view.setTextSize(18f);
        setContentView(view);
    }
}
