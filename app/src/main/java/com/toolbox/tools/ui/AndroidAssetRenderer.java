package com.toolbox.tools.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.VideoView;

import com.toolbox.tools.core.AppKernel;
import com.toolbox.tools.core.VisibleWorkspaceStore;
import com.toolbox.tools.product.CacheManager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;

public final class AndroidAssetRenderer {
    private static final long MAX_MEDIA_BYTES = 128L * 1024L * 1024L;
    private static final int MAX_TEXT_BYTES = 64 * 1024;

    private AndroidAssetRenderer() {}

    public static View render(
            Context context,
            AppKernel kernel,
            String assetId,
            int targetWidthPx,
            int targetHeightPx
    ) throws IOException {
        if (context == null || kernel == null) {
            throw new NullPointerException("asset renderer context/kernel");
        }
        Map<String, String> resources =
                kernel.projectManager().current().resources();
        String storageArea = require(
                resources,
                assetId + ".storage.area"
        );
        String storageName = require(
                resources,
                assetId + ".storage.name"
        );
        String sha256 = require(resources, assetId + ".sha256");
        String kind = require(resources, assetId + ".kind");
        String mime = resources.getOrDefault(
                assetId + ".mime",
                "application/octet-stream"
        );
        if (!VisibleWorkspaceStore.Area.ASSETS.folder()
                .equals(storageArea)) {
            throw new IOException("asset storage area invalid");
        }
        VisibleWorkspaceStore visible = kernel.visibleWorkspaceStore();
        CacheManager cache = kernel.productServices().cache();

        float quality = kernel.productServices()
                .resources()
                .previewQuality();
        int adjustedWidth = Math.max(
                32,
                Math.round(targetWidthPx * quality)
        );
        int adjustedHeight = Math.max(
                32,
                Math.round(targetHeightPx * quality)
        );

        switch (kind) {
            case "IMAGE":
            case "ICON":
                return image(
                        context,
                        visible,
                        storageName,
                        sha256,
                        adjustedWidth,
                        adjustedHeight
                );
            case "FONT":
                return font(
                        context,
                        cache,
                        visible,
                        storageName,
                        sha256
                );
            case "AUDIO":
                return audio(
                        context,
                        cache,
                        visible,
                        storageName,
                        sha256
                );
            case "VIDEO":
                return video(
                        context,
                        cache,
                        visible,
                        storageName,
                        sha256
                );
            case "JSON":
            case "RAW":
            default:
                return text(
                        context,
                        visible,
                        storageName,
                        sha256,
                        mime
                );
        }
    }

    public static boolean verify(
            VisibleWorkspaceStore visible,
            String storageName,
            String expectedSha256
    ) throws IOException {
        MessageDigest digest = digest();
        long total = 0;
        try (InputStream input = visible.openInputStream(
                VisibleWorkspaceStore.Area.ASSETS,
                storageName
        )) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_MEDIA_BYTES) {
                    throw new IOException("asset runtime budget exceeded");
                }
                digest.update(buffer, 0, read);
            }
        }
        return constantEquals(
                expectedSha256,
                hex(digest.digest())
        );
    }

    private static View image(
            Context context,
            VisibleWorkspaceStore visible,
            String storageName,
            String sha256,
            int targetWidth,
            int targetHeight
    ) throws IOException {
        if (!verify(visible, storageName, sha256)) {
            throw new IOException("asset integrity mismatch");
        }

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = visible.openInputStream(
                VisibleWorkspaceStore.Area.ASSETS,
                storageName
        )) {
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("image decode bounds failed");
        }

        int desiredWidth = Math.max(32, targetWidth);
        int desiredHeight = Math.max(32, targetHeight);
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= desiredWidth
                && bounds.outHeight / (sample * 2) >= desiredHeight) {
            sample *= 2;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, sample);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap;
        try (InputStream input = visible.openInputStream(
                VisibleWorkspaceStore.Area.ASSETS,
                storageName
        )) {
            bitmap = BitmapFactory.decodeStream(input, null, options);
        }
        if (bitmap == null) {
            throw new IOException("image decode failed");
        }

        ImageView view = new ImageView(context);
        view.setImageBitmap(bitmap);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        view.setContentDescription("Pratinjau aset gambar");
        return view;
    }

    private static View font(
            Context context,
            CacheManager cache,
            VisibleWorkspaceStore visible,
            String storageName,
            String sha256
    ) throws IOException {
        File file = verifiedCacheFile(
                context,
                cache,
                visible,
                storageName,
                sha256,
                ".font"
        );
        Typeface typeface;
        try {
            typeface = Typeface.createFromFile(file);
        } catch (RuntimeException error) {
            throw new IOException("font decode failed", error);
        }
        TextView view = UiKit.judul(
                context,
                "ToolBox • Aa Bb 123",
                18f
        );
        view.setGravity(Gravity.CENTER);
        view.setTypeface(typeface);
        view.setContentDescription("Pratinjau aset font");
        return view;
    }

    private static View audio(
            Context context,
            CacheManager cache,
            VisibleWorkspaceStore visible,
            String storageName,
            String sha256
    ) throws IOException {
        File file = verifiedCacheFile(
                context,
                cache,
                visible,
                storageName,
                sha256,
                ".audio"
        );
        AudioPreviewFrame frame = new AudioPreviewFrame(context);
        TextView button = UiKit.tombol(
                context,
                "▶ Putar Audio",
                true
        );
        button.setGravity(Gravity.CENTER);
        frame.addView(button, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        button.setOnClickListener(v -> frame.toggle(file, button));
        frame.setContentDescription("Pratinjau aset audio");
        return frame;
    }

    private static View video(
            Context context,
            CacheManager cache,
            VisibleWorkspaceStore visible,
            String storageName,
            String sha256
    ) throws IOException {
        File file = verifiedCacheFile(
                context,
                cache,
                visible,
                storageName,
                sha256,
                ".video"
        );
        VideoPreviewFrame frame = new VideoPreviewFrame(context);
        VideoView video = new VideoView(context);
        video.setVideoPath(file.getAbsolutePath());
        frame.video = video;
        frame.addView(video, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        TextView play = UiKit.tombol(
                context,
                "▶ Video",
                true
        );
        FrameLayout.LayoutParams playParams = new FrameLayout.LayoutParams(
                UiKit.dp(context, 110),
                UiKit.dp(context, 42),
                Gravity.CENTER
        );
        frame.addView(play, playParams);
        play.setOnClickListener(v -> {
            if (video.isPlaying()) {
                video.pause();
                play.setText("▶ Video");
            } else {
                video.start();
                play.setText("❚❚ Jeda");
            }
        });
        video.setOnCompletionListener(mp -> play.setText("▶ Video"));
        frame.setContentDescription("Pratinjau aset video");
        return frame;
    }

    private static View text(
            Context context,
            VisibleWorkspaceStore visible,
            String storageName,
            String sha256,
            String mime
    ) throws IOException {
        if (!verify(visible, storageName, sha256)) {
            throw new IOException("asset integrity mismatch");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream input = visible.openInputStream(
                VisibleWorkspaceStore.Area.ASSETS,
                storageName
        )) {
            byte[] buffer = new byte[4096];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1
                    && total < MAX_TEXT_BYTES) {
                int accepted = Math.min(
                        read,
                        MAX_TEXT_BYTES - total
                );
                output.write(buffer, 0, accepted);
                total += accepted;
            }
        }
        String preview = new String(
                output.toByteArray(),
                StandardCharsets.UTF_8
        );
        if (preview.length() > 600) {
            preview = preview.substring(0, 600) + "…";
        }
        TextView view = UiKit.teks(
                context,
                mime + "\n" + preview,
                10f,
                UiKit.TEKS
        );
        view.setPadding(
                UiKit.dp(context, 8),
                UiKit.dp(context, 8),
                UiKit.dp(context, 8),
                UiKit.dp(context, 8)
        );
        return view;
    }

    private static File verifiedCacheFile(
            Context context,
            CacheManager cache,
            VisibleWorkspaceStore visible,
            String storageName,
            String sha256,
            String suffix
    ) throws IOException {
        File directory = new File(
                context.getCacheDir(),
                "asset-preview"
        );
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("asset preview cache unavailable");
        }
        File target = new File(
                directory,
                sha256.substring(0, 24) + suffix
        );
        if (target.isFile()
                && constantEquals(sha256, fileSha256(target))) {
            registerPreviewCache(cache, target, sha256);
            return target;
        }

        File pending = new File(target.getPath() + ".pending");
        MessageDigest digest = digest();
        long total = 0;
        try (InputStream input = visible.openInputStream(
                VisibleWorkspaceStore.Area.ASSETS,
                storageName
        );
             FileOutputStream output = new FileOutputStream(pending)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_MEDIA_BYTES) {
                    throw new IOException("asset runtime budget exceeded");
                }
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
            output.flush();
            output.getFD().sync();
        } catch (IOException error) {
            pending.delete();
            throw error;
        }
        if (!constantEquals(sha256, hex(digest.digest()))) {
            pending.delete();
            throw new IOException("asset integrity mismatch");
        }
        if (target.exists() && !target.delete()) {
            pending.delete();
            throw new IOException("asset preview cache replace failed");
        }
        if (!pending.renameTo(target)) {
            pending.delete();
            throw new IOException("asset preview cache publish failed");
        }
        registerPreviewCache(cache, target, sha256);
        return target;
    }

    private static void registerPreviewCache(
            CacheManager cache,
            File file,
            String sha256
    ) {
        if (cache == null || file == null || !file.isFile()) return;
        cache.put(
                "preview." + sha256.substring(0, 24),
                file.length(),
                CacheManager.Priority.COLD,
                CacheManager.Category.PREVIEW,
                CacheManager.Tier.DISK,
                () -> {
                    if (file.isFile()) file.delete();
                }
        );
    }

    private static String fileSha256(File file) throws IOException {
        MessageDigest digest = digest();
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return hex(digest.digest());
    }

    private static MessageDigest digest() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception error) {
            throw new IOException("SHA-256 unavailable", error);
        }
    }

    private static boolean constantEquals(String expected, String actual) {
        if (expected == null || actual == null) return false;
        return MessageDigest.isEqual(
                expected.toLowerCase(Locale.ROOT)
                        .getBytes(StandardCharsets.US_ASCII),
                actual.toLowerCase(Locale.ROOT)
                        .getBytes(StandardCharsets.US_ASCII)
        );
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        for (byte value : bytes) {
            out.append(String.format(Locale.ROOT, "%02x", value));
        }
        return out.toString();
    }

    private static String require(
            Map<String, String> resources,
            String key
    ) throws IOException {
        String value = resources.get(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IOException("asset metadata missing:" + key);
        }
        return value;
    }

    private static final class AudioPreviewFrame extends FrameLayout {
        private MediaPlayer player;

        AudioPreviewFrame(Context context) {
            super(context);
        }

        void toggle(File file, TextView button) {
            try {
                if (player != null && player.isPlaying()) {
                    player.pause();
                    button.setText("▶ Putar Audio");
                    return;
                }
                if (player == null) {
                    player = new MediaPlayer();
                    player.setDataSource(file.getAbsolutePath());
                    player.setOnCompletionListener(mp -> {
                        button.setText("▶ Putar Audio");
                        releasePlayer();
                    });
                    player.prepare();
                }
                player.start();
                button.setText("❚❚ Jeda Audio");
            } catch (Exception error) {
                releasePlayer();
                button.setText("Audio gagal diputar");
                button.setEnabled(false);
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            releasePlayer();
            super.onDetachedFromWindow();
        }

        private void releasePlayer() {
            if (player != null) {
                try {
                    player.stop();
                } catch (RuntimeException ignored) {}
                player.release();
                player = null;
            }
        }
    }

    private static final class VideoPreviewFrame extends FrameLayout {
        VideoView video;

        VideoPreviewFrame(Context context) {
            super(context);
        }

        @Override
        protected void onDetachedFromWindow() {
            if (video != null) video.stopPlayback();
            super.onDetachedFromWindow();
        }
    }
}
