package com.toolbox.tools.android;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Objects;

public final class InstalledApkIdentity {
    private final String packageName;
    private final long versionCode;
    private final String apkSha256;

    private InstalledApkIdentity(
            String packageName,
            long versionCode,
            String apkSha256
    ) {
        this.packageName = packageName;
        this.versionCode = versionCode;
        this.apkSha256 = apkSha256;
    }

    public static InstalledApkIdentity read(Context context)
            throws IOException {
        Objects.requireNonNull(context, "context");
        PackageManager pm = context.getPackageManager();
        String packageName = context.getPackageName();
        try {
            ApplicationInfo applicationInfo =
                    pm.getApplicationInfo(packageName, 0);
            if (applicationInfo.sourceDir == null) {
                throw new IOException("installed APK source unavailable");
            }
            PackageInfo packageInfo =
                    pm.getPackageInfo(packageName, 0);
            long versionCode = Build.VERSION.SDK_INT >= 28
                    ? packageInfo.getLongVersionCode()
                    : packageInfo.versionCode;
            return new InstalledApkIdentity(
                    packageName,
                    versionCode,
                    sha256(new File(applicationInfo.sourceDir))
            );
        } catch (PackageManager.NameNotFoundException error) {
            throw new IOException(
                    "installed APK package unavailable",
                    error
            );
        }
    }

    public String packageName() { return packageName; }
    public long versionCode() { return versionCode; }
    public String apkSha256() { return apkSha256; }

    private static String sha256(File file) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IOException("installed APK file unavailable");
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (Exception error) {
            throw new IOException("SHA-256 unavailable", error);
        }
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[128 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder out = new StringBuilder();
        for (byte value : digest.digest()) {
            out.append(String.format(Locale.ROOT, "%02x", value));
        }
        return out.toString();
    }
}
