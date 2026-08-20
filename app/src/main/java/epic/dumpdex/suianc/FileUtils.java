package epic.dumpdex.suianc;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class FileUtils {

    /**
     * 调起文件选择器，优先尝试 MT 管理器与 MT Canary 版
     */
    public static void openFilePicker(Activity activity, int requestCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        PackageManager pm = activity.getPackageManager();

        // 优先 1: MT 管理器正式版
        Intent mtIntent = new Intent(intent);
        mtIntent.setPackage("bin.mt.plus");
        if (mtIntent.resolveActivity(pm) != null) {
            activity.startActivityForResult(mtIntent, requestCode);
            return;
        }

        // 优先 2: MT 管理器 Canary 版
        Intent mtCanaryIntent = new Intent(intent);
        mtCanaryIntent.setPackage("bin.mt.plus.canary");
        if (mtCanaryIntent.resolveActivity(pm) != null) {
            activity.startActivityForResult(mtCanaryIntent, requestCode);
            return;
        }

        // 回退: 系统文件选择器
        try {
            activity.startActivityForResult(Intent.createChooser(intent, "选择 APK 文件"), requestCode);
        } catch (Exception e) {
            Toast.makeText(activity, "未找到有效的文件管理器！", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 解析 Uri 路径，如无法直接获取真实路径则拷贝至缓存目录
     */
    public static String getPathFromUri(Context context, Uri uri) {
        if (uri == null) return null;

        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        String path = getRealPathFromUri(context, uri);
        if (path != null && new File(path).exists()) {
            return path;
        }

        // 兜底方案：拷贝 Content Stream 到 Cache
        try {
            File cacheFile = new File(context.getCacheDir(), "input_target.apk");
            try (InputStream is = context.getContentResolver().openInputStream(uri);
                 FileOutputStream fos = new FileOutputStream(cacheFile)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = is.read(buf)) > 0) {
                    fos.write(buf, 0, len);
                }
            }
            return cacheFile.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static String getRealPathFromUri(Context context, Uri uri) {
        if (DocumentsContract.isDocumentUri(context, uri)) {
            if ("com.android.externalstorage.documents".equals(uri.getAuthority())) {
                String docId = DocumentsContract.getDocumentId(uri);
                String[] split = docId.split(":");
                if ("primary".equalsIgnoreCase(split[0])) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }
            } else if ("com.android.providers.downloads.documents".equals(uri.getAuthority())) {
                String id = DocumentsContract.getDocumentId(uri);
                if (id.startsWith("raw:")) {
                    return id.replaceFirst("raw:", "");
                }
                Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.parseLong(id));
                return getDataColumn(context, contentUri, null, null);
            }
        } else if ("content".equalsIgnoreCase(uri.getScheme())) {
            return getDataColumn(context, uri, null, null);
        }
        return null;
    }

    private static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        Cursor cursor = null;
        String column = "_data";
        String[] projection = {column};
        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(columnIndex);
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }
}
