package epic.dumpdex.suianc;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class AxmlProcessor {

    public interface OnLogListener {
        void onLog(String msg, String type);
    }

    public static class Result {
        public int totalXmlCount;
        public int decryptedCount;
        public String methodName;
        public Object decryptionKey;

        public Result(int totalXmlCount, int decryptedCount, String methodName, Object decryptionKey) {
            this.totalXmlCount = totalXmlCount;
            this.decryptedCount = decryptedCount;
            this.methodName = methodName;
            this.decryptionKey = decryptionKey;
        }
    }

    /**
     * 读取配置并处理 res/ 目录下的 Epic 加密 XML
     */
    public static Result processResXmls(ZipFile zipFile, JSONObject config, File zipOutputFile, OnLogListener listener) throws Exception {
        if (!config.has("axml_protection_method")) {
            throw new IllegalArgumentException("配置中未找到 axml_protection_method 字段");
        }

        int method = config.getInt("axml_protection_method");
        Object decryptionKey = (method == 0)
                ? config.get("axml_protection_xor_key")
                : config.get("axml_protection_rc4_key");
        String methodName = (method == 0) ? "XOR" : "RC4";

        if (listener != null) {
            listener.onLog("AXML 资源保护算法: " + methodName + " | Key: " + decryptionKey, "info");
        }

        int totalXmlCount = 0;
        int decryptedCount = 0;

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipOutputFile))) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();

                if (name.startsWith("res/") && name.endsWith(".xml")) {
                    totalXmlCount++;
                    byte[] data;
                    try (InputStream is = zipFile.getInputStream(entry)) {
                        data = readAllBytes(is);
                    }

                    // 检查魔数头部 'Epic' (0x45 0x70 0x69 0x63)
                    if (data.length >= 4 && data[0] == 'E' && data[1] == 'p' && data[2] == 'i' && data[3] == 'c') {
                        decryptedCount++;

                        byte[] decrypted;
                        if (method == 0) {
                            decrypted = Decryptor.xorCrypt(data, decryptionKey);
                        } else {
                            byte[] encBody = new byte[data.length - 4];
                            System.arraycopy(data, 4, encBody, 0, encBody.length);
                            byte[] decBody = Decryptor.rc4Crypt(encBody, decryptionKey);

                            decrypted = new byte[data.length];
                            System.arraycopy(data, 0, decrypted, 0, 4);
                            System.arraycopy(decBody, 0, decrypted, 4, decBody.length);
                        }

                        // 修正文件头为标准二进制 XML 魔数: 0x03 0x00 0x08 0x00
                        if (decrypted.length >= 4) {
                            decrypted[0] = (byte) 0x03;
                            decrypted[1] = (byte) 0x00;
                            decrypted[2] = (byte) 0x08;
                            decrypted[3] = (byte) 0x00;
                        }

                        data = decrypted;
                    }

                    zos.putNextEntry(new ZipEntry(name));
                    zos.write(data);
                    zos.closeEntry();
                }
            }
        }

        return new Result(totalXmlCount, decryptedCount, methodName, decryptionKey);
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
