package com.toolbox.fixture;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public final class FixtureDescribeActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView text = new TextView(this);
        text.setText("Managed Fixture Editing Door");
        setContentView(text);
    }
}
