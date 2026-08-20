package epic.dumpdex.suianc;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ElfConfigParser {

    public interface OnLogListener {
        void onLog(String msg, String type);
    }

    /**
     * 只负责从 APK 中检索 .so，读取并解密 .ArmEpic 节区，返回纯粹的 JSONObject
     */
    public static JSONObject parseConfigFromApk(ZipFile zipFile, OnLogListener listener) {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        List<ZipEntry> soEntries = new ArrayList<>();

        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.getName().endsWith(".so")) {
                soEntries.add(entry);
            }
        }

        if (listener != null) {
            listener.onLog("共发现 " + soEntries.size() + " 个 .so 文件", "info");
        }

        for (ZipEntry soEntry : soEntries) {
            try (InputStream is = zipFile.getInputStream(soEntry)) {
                byte[] soData = readAllBytes(is);
                byte[] raw = parseArmEpicSection(soData);

                if (raw != null && raw.length > 16) {
                    byte[] key = Arrays.copyOfRange(raw, 0, 16);
                    byte[] encryptedConfig = Arrays.copyOfRange(raw, 16, raw.length);
                    byte[] cfgBytes = Decryptor.rc4Crypt(encryptedConfig, key);
                    String cfgStr = new String(cfgBytes, StandardCharsets.UTF_8);

                    int firstJson = cfgStr.indexOf('{');
                    int lastJson = cfgStr.lastIndexOf('}');
                    if (firstJson != -1 && lastJson != -1 && lastJson >= firstJson) {
                        if (listener != null) {
                            listener.onLog("成功从 " + soEntry.getName() + " 读取配置", "success");
                        }
                        return new JSONObject(cfgStr.substring(firstJson, lastJson + 1));
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static byte[] parseArmEpicSection(byte[] soData) {
        try {
            if (soData == null || soData.length < 0x40) return null;
            boolean is32 = soData[4] == 1;

            ByteBuffer bb = ByteBuffer.wrap(soData).order(ByteOrder.LITTLE_ENDIAN);

            long e_shoff = is32 ? (bb.getInt(0x20) & 0xFFFFFFFFL) : bb.getLong(0x28);
            int e_shnum = is32 ? (bb.getShort(0x30) & 0xFFFF) : (bb.getShort(0x3C) & 0xFFFF);
            int e_shstrndx = is32 ? (bb.getShort(0x32) & 0xFFFF) : (bb.getShort(0x3E) & 0xFFFF);
            int shSize = is32 ? 40 : 64;

            long shstrtabHeaderOff = e_shoff + ((long) e_shstrndx * shSize);
            long shstrtabOff = is32
                    ? (bb.getInt((int) (shstrtabHeaderOff + 16)) & 0xFFFFFFFFL)
                    : bb.getLong((int) (shstrtabHeaderOff + 24));

            for (int i = 0; i < e_shnum; i++) {
                long entryOff = e_shoff + ((long) i * shSize);
                int nameIdx = bb.getInt((int) entryOff);

                int nameStart = (int) (shstrtabOff + nameIdx);
                int nameEnd = nameStart;
                while (nameEnd < soData.length && soData[nameEnd] != 0) nameEnd++;
                String name = new String(soData, nameStart, nameEnd - nameStart, StandardCharsets.UTF_8);

                if (".ArmEpic".equals(name)) {
                    long offset = is32 ? (bb.getInt((int) (entryOff + 16)) & 0xFFFFFFFFL) : bb.getLong((int) (entryOff + 24));
                    long size = is32 ? (bb.getInt((int) (entryOff + 20)) & 0xFFFFFFFFL) : bb.getLong((int) (entryOff + 32));
                    return Arrays.copyOfRange(soData, (int) offset, (int) (offset + size));
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int len;
        while ((len = is.read(buffer)) != -1) baos.write(buffer, 0, len);
        return baos.toByteArray();
    }
}
