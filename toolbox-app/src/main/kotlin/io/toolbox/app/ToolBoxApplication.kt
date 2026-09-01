package io.toolbox.app

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.StrictMode
import io.toolbox.kernel.KernelSnapshot

class ToolBoxApplication : Application() {
    lateinit var compatibility: RuntimeCompatibility.Result
        private set

    var initialKernelSnapshot: KernelSnapshot? = null
        private set

    override fun onCreate() {
        super.onCreate()

        compatibility = RuntimeCompatibility.evaluate(
            api = Build.VERSION.SDK_INT,
            supportedAbis = Build.SUPPORTED_ABIS.toList()
        )

        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (debuggable) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .penaltyDeathOnNetwork()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectActivityLeaks()
                    .detectLeakedClosableObjects()
                    .detectLeakedRegistrationObjects()
                    .penaltyLog()
                    .build()
            )
        }

        if (compatibility.supported) {
            initialKernelSnapshot = KernelRuntime.startIfNeeded()
        }
    }
}
