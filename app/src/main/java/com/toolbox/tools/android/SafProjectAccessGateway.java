package com.toolbox.tools.android;

import android.content.ContentResolver;
import android.content.Intent;
import android.content.UriPermission;
import android.net.Uri;

import java.util.Objects;

public final class SafProjectAccessGateway {
    public boolean hasPersistedReadWriteAccess(
            ContentResolver resolver,
            Uri treeUri
    ) {
        Objects.requireNonNull(resolver, "resolver");
        Objects.requireNonNull(treeUri, "treeUri");
        for (UriPermission permission : resolver.getPersistedUriPermissions()) {
            if (treeUri.equals(permission.getUri())
                    && permission.isReadPermission()
                    && permission.isWritePermission()) {
                return true;
            }
        }
        return false;
    }

    public void persistReadWriteAccess(
            ContentResolver resolver,
            Uri treeUri,
            int returnedFlags
    ) {
        Objects.requireNonNull(resolver, "resolver");
        Objects.requireNonNull(treeUri, "treeUri");
        int allowed = Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        int flags = returnedFlags & allowed;
        if ((flags & allowed) != allowed) {
            throw new IllegalArgumentException(
                    "SAF read/write grant incomplete"
            );
        }
        resolver.takePersistableUriPermission(treeUri, flags);
    }

    public void releaseReadWriteAccess(
            ContentResolver resolver,
            Uri treeUri
    ) {
        Objects.requireNonNull(resolver, "resolver");
        Objects.requireNonNull(treeUri, "treeUri");
        resolver.releasePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        );
    }
}
