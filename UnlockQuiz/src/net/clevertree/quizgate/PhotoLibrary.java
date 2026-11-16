package net.clevertree.quizgate;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Device gallery images (MediaStore) and bundled backdrop catalog.
 * Prefer {@link #listForQuiz(Context, boolean)} which respects photo_source prefs.
 */
public final class PhotoLibrary {
    private static final String TAG = AlarmScheduler.TAG;
    private static final int MAX_PHOTOS = 500;

    private PhotoLibrary() {}

    public static final String SOURCE_GALLERY = "gallery";
    public static final String SOURCE_BUNDLED = "bundled";

    /** Photos for quiz backdrop / transition, honoring AppPrefs photo_source. */
    public static List<Uri> listForQuiz(Context ctx, boolean forSuccess) {
        AppPrefs prefs = new AppPrefs(ctx);
        if (SOURCE_GALLERY.equals(prefs.getPhotoSource())) {
            List<Uri> gallery = listGallery(ctx);
            if (!gallery.isEmpty()) return gallery;
            Log.i(TAG, "gallery empty — falling back to bundled catalog");
        }
        List<Uri> mood = forSuccess ? BackdropCatalog.listSuccess(ctx) : BackdropCatalog.listFail(ctx);
        if (!mood.isEmpty()) return mood;
        List<Uri> other = forSuccess ? BackdropCatalog.listFail(ctx) : BackdropCatalog.listSuccess(ctx);
        if (!other.isEmpty()) return other;
        return listGallery(ctx);
    }

    public static List<Uri> listAll(Context ctx) {
        return listForQuiz(ctx, true);
    }

    public static List<Uri> listGallery(Context ctx) {
        Set<String> seen = new LinkedHashSet<>();
        List<Uri> out = new ArrayList<>();

        String[] projection = {
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATA
        };
        try (Cursor c = ctx.getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                MediaStore.Images.Media.DATE_ADDED + " DESC")) {
            if (c != null) {
                int idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                int dataCol = c.getColumnIndex(MediaStore.Images.Media.DATA);
                while (c.moveToNext() && out.size() < MAX_PHOTOS) {
                    long id = c.getLong(idCol);
                    Uri uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                    String key = uri.toString();
                    if (dataCol >= 0) {
                        String path = c.getString(dataCol);
                        if (path != null && !path.isEmpty()) key = path;
                    }
                    if (seen.add(key)) out.add(uri);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "MediaStore photo query failed", e);
        }

        File[] roots = {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                new File("/sdcard/Pictures"),
                new File("/sdcard/DCIM"),
                new File("/storage/emulated/0/Pictures"),
                new File("/storage/emulated/0/DCIM")
        };
        for (File root : roots) {
            collectFiles(root, seen, out, 0);
            if (out.size() >= MAX_PHOTOS) break;
        }

        Log.i(TAG, "device photos available=" + out.size());
        return out;
    }

    private static void collectFiles(File dir, Set<String> seen, List<Uri> out, int depth) {
        if (dir == null || !dir.isDirectory() || depth > 6 || out.size() >= MAX_PHOTOS) return;
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File f : kids) {
            if (out.size() >= MAX_PHOTOS) return;
            if (f.isDirectory()) {
                collectFiles(f, seen, out, depth + 1);
            } else if (isImageName(f.getName())) {
                String path = f.getAbsolutePath();
                if (seen.add(path)) out.add(Uri.fromFile(f));
            }
        }
    }

    private static boolean isImageName(String name) {
        String n = name.toLowerCase(Locale.US);
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp");
    }

    public static Bitmap decodeSampled(Context ctx, Uri uri, int reqW, int reqH) {
        if (uri == null) return null;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        try (InputStream in = open(ctx, uri)) {
            if (in == null) return null;
            BitmapFactory.decodeStream(in, null, opts);
        } catch (Exception e) {
            Log.w(TAG, "decode bounds " + uri, e);
            return null;
        }
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null;
        int sample = 1;
        while (opts.outWidth / sample > reqW || opts.outHeight / sample > reqH) sample *= 2;
        opts.inJustDecodeBounds = false;
        opts.inSampleSize = sample;
        try (InputStream in = open(ctx, uri)) {
            if (in == null) return null;
            return BitmapFactory.decodeStream(in, null, opts);
        } catch (Exception e) {
            Log.w(TAG, "decode photo " + uri, e);
            return null;
        }
    }

    private static InputStream open(Context ctx, Uri uri) throws Exception {
        if (BackdropCatalog.ASSET_SCHEME.equals(uri.getScheme())) {
            String path = uri.getPath();
            if (path != null && path.startsWith("/")) path = path.substring(1);
            return ctx.getAssets().open(path);
        }
        if ("file".equals(uri.getScheme())) {
            return new java.io.FileInputStream(uri.getPath());
        }
        return ctx.getContentResolver().openInputStream(uri);
    }
}
