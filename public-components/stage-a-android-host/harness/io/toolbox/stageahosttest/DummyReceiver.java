package io.toolbox.stageahosttest;

import android.app.Activity;

interface DummyReceiver {
    String receiverId();
    void verify(Activity activity);
}
