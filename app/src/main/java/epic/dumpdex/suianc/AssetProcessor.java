package epic.dumpdex.suianc;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class AssetProcessor {

    public interface OnLogListener {
        void onLog(String msg, String type);
    }

    public static class Result {
        public int processedCount;
        public String methodName;
        public Object decryptionKey;

        public Result(int processedCount, String methodName, Object decryptionKey) {
            this.processedCount = processedCount;
            this.methodName = methodName;
            this.decryptionKey = decryptionKey;
        }
    }

    public static Result processAssets(ZipFile zipFile, JSONObject config, File zipOutputFile, OnLogListener listener) throws Exception {
        if (!config.has("asset_protection_method")) {
            throw new IllegalArgumentException("Config missing asset_protection_method");
        }

        int method = config.optInt("asset_protection_method", 0);
        Object keyObj = (method == 1)
                ? config.opt("asset_protection_rc4_key")
                : config.opt("asset_protection_xor_key");
        String methodName = (method == 1) ? "RC4" : "XOR";

        JSONObject renameMap = config.optJSONObject("asset_rename_map");
        if (renameMap == null) {
            renameMap = new JSONObject();
        }

        int processedCount = 0;

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipOutputFile))) {
            // Write debug config first
            zos.putNextEntry(new ZipEntry("config_debug.json"));
            zos.write(config.toString(4).getBytes("UTF-8"));
            zos.closeEntry();

            Iterator<String> keys = renameMap.keys();
            while (keys.hasNext()) {
                String logicPath = keys.next();
                String realPath = renameMap.getString(logicPath);

                ZipEntry entry = zipFile.getEntry(realPath);
                if (entry == null) continue;

                if (listener != null) {
                    listener.onLog("Assest资源解密: " + realPath + " -> " + logicPath, "info");
                }

                byte[] encData;
                try (InputStream is = zipFile.getInputStream(entry)) {
                    encData = readAllBytes(is);
                }

                byte[] decData = (method == 1)
                        ? Decryptor.rc4Crypt(encData, keyObj)
                        : Decryptor.xorCrypt(encData, keyObj);

                zos.putNextEntry(new ZipEntry(logicPath));
                zos.write(decData);
                zos.closeEntry();

                processedCount++;
            }
        }

        return new Result(processedCount, methodName, keyObj);
    }

    private static byte[] readAllBytes(InputStream is) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int len;
        while ((len = is.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
        return baos.toByteArray();
    }
}
