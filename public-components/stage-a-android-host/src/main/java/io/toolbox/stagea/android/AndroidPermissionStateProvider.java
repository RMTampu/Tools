package io.toolbox.stagea.android;

import android.content.Context;
import android.content.pm.PackageManager;

import io.toolbox.contracts.runtime.Contracts;
import io.toolbox.contracts.runtime.ProductRegistry;
import io.toolbox.stagea.StageAContracts;

import java.util.Optional;

public final class AndroidPermissionStateProvider implements StageAContracts.PermissionStateProvider {
    private final Context context;
    private final ProductRegistry registry;

    public AndroidPermissionStateProvider(Context context, ProductRegistry registry) {
        if (context == null) throw new NullPointerException("context");
        if (registry == null) throw new NullPointerException("registry");
        this.context = context.getApplicationContext();
        this.registry = registry;
    }

    @Override
    public StageAContracts.Availability permissionState(String permissionId) {
        Optional<Contracts.PermissionRequirement> optional;
        try {
            optional = registry.permission(permissionId);
        } catch (RuntimeException failure) {
            return StageAContracts.Availability.UNAVAILABLE;
        }
        if (!optional.isPresent()) return StageAContracts.Availability.UNAVAILABLE;
        Contracts.PermissionRequirement requirement = optional.get();
        String platformRef = requirement.platformPermissionRef();
        if (platformRef == null || platformRef.trim().isEmpty()) {
            return StageAContracts.Availability.UNSUPPORTED;
        }
        if (requirement.kind() == Contracts.PermissionKind.SPECIAL_ACCESS) {
            return StageAContracts.Availability.UNSUPPORTED;
        }
        return context.checkSelfPermission(platformRef) == PackageManager.PERMISSION_GRANTED
                ? StageAContracts.Availability.AVAILABLE
                : StageAContracts.Availability.UNAVAILABLE;
    }
}
