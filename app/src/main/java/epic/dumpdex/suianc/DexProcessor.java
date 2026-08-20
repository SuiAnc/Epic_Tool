package epic.dumpdex.suianc;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class DexProcessor {

    public interface OnLogListener {
        void onLog(String msg, String type);
    }

    public static class Result {
        public int dexCount;
        public String methodName;
        public Object decryptionKey;
        public List<String> mappingLogs;

        public Result(int dexCount, String methodName, Object decryptionKey, List<String> mappingLogs) {
            this.dexCount = dexCount;
            this.methodName = methodName;
            this.decryptionKey = decryptionKey;
            this.mappingLogs = mappingLogs;
        }
    }

    public static Result processDex(ZipFile zipFile, JSONObject config, File outDir, OnLogListener listener) throws Exception {
        if (!config.has("dex_protection_method")) {
            throw new IllegalArgumentException("Config missing dex_protection_method");
        }

        int method = config.optInt("dex_protection_method", 0);
        String dexDir = config.optString("dex_dir", "");
        String targetPrefix = dexDir.isEmpty() ? "assets/" : "assets/" + dexDir + "/";

        Object keyObj = (method == 1)
                ? config.opt("dex_protection_rc4_key")
                : config.opt("dex_protection_xor_key");
        String methodName = (method == 1) ? "RC4" : "XOR";

        List<ZipEntry> epicEntries = new ArrayList<>();
        Enumeration<? extends ZipEntry> entries = zipFile.entries();

        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.startsWith(targetPrefix) && name.toLowerCase().endsWith(".epic")) {
                epicEntries.add(entry);
            }
        }

        // Sort entries deterministically
        Collections.sort(epicEntries, (a, b) -> a.getName().compareTo(b.getName()));

        int dexCount = 0;
        List<String> mappingLogs = new ArrayList<>();

        for (ZipEntry entry : epicEntries) {
            dexCount++;
            String outFileName = (dexCount == 1) ? "classes.dex" : "classes" + dexCount + ".dex";
            File outFile = new File(outDir, outFileName);

            if (listener != null) {
                listener.onLog("Dex保护: " + entry.getName() + " -> " + outFileName, "info");
            }

            byte[] encData;
            try (InputStream is = zipFile.getInputStream(entry)) {
                encData = readAllBytes(is);
            }

            byte[] decData = (method == 1)
                    ? Decryptor.rc4Crypt(encData, keyObj)
                    : Decryptor.xorCrypt(encData, keyObj);

            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(decData);
            }

            mappingLogs.add("[" + dexCount + "] " + entry.getName() + " -> " + outFileName);
        }

        return new Result(dexCount, methodName, keyObj, mappingLogs);
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
