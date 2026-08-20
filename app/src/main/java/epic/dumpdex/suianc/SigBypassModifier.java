package epic.dumpdex.suianc;

import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SigBypassModifier {

    public static class ModifyResult {
        public boolean success;
        public byte[] modifiedSoData;
        public String logMessage;

        public ModifyResult(boolean success, byte[] modifiedSoData, String logMessage) {
            this.success = success;
            this.modifiedSoData = modifiedSoData;
            this.logMessage = logMessage;
        }
    }

    public static ModifyResult modifyCertificateMd5(byte[] soData, String newMd5) {
        StringBuilder log = new StringBuilder();
        try {
            int[] offsetAndSize = getArmEpicSectionOffsetAndSize(soData);
            if (offsetAndSize == null) {
                return new ModifyResult(false, null, "未找到 .ArmEpic 节区");
            }

            int offset = offsetAndSize[0];
            int size = offsetAndSize[1];

            byte[] rawSection = Arrays.copyOfRange(soData, offset, offset + size);
            if (rawSection.length < 16) {
                return new ModifyResult(false, null, "节区数据无效");
            }

            byte[] cachedKey = Arrays.copyOfRange(rawSection, 0, 16);
            byte[] encJson = Arrays.copyOfRange(rawSection, 16, rawSection.length);

            byte[] decJsonBytes = Decryptor.rc4Crypt(encJson, cachedKey);
            String configStr = new String(decJsonBytes, StandardCharsets.UTF_8);

            int startIdx = configStr.indexOf('{');
            int endIdx = configStr.lastIndexOf('}') + 1;
            if (startIdx == -1 || endIdx <= startIdx) {
                return new ModifyResult(false, null, "无法解析 JSON 结构");
            }

            String validJson = configStr.substring(startIdx, endIdx);
            log.append("原始配置 JSON:\n").append(validJson).append("\n");

            if (!validJson.contains("certificate_md5")) {
                return new ModifyResult(false, null, "未在配置中找到 certificate_md5 字段");
            }

            // 【核心改进】：使用正则直接在原 JSON 字符串上 1:1 替换 MD5 值，确保字节长度完全不变！
            Pattern pattern = Pattern.compile("(\"certificate_md5\"\\s*:\\s*\")[a-fA-F0-9]{32}(\")");
            Matcher matcher = pattern.matcher(validJson);

            String modifiedJson;
            if (matcher.find()) {
                modifiedJson = matcher.replaceFirst("$1" + newMd5 + "$2");
            } else {
                // 万一 MD5 不是标准的 32 位 hex 格式，备用替换策略
                modifiedJson = validJson.replaceAll("\"certificate_md5\"\\s*:\\s*\"[^\"]+\"", "\"certificate_md5\":\"" + newMd5 + "\"");
            }

            log.append("替换后 JSON:\n").append(modifiedJson).append("\n");

            // 转化为字节并校验
            byte[] newJsonBytes = modifiedJson.getBytes(StandardCharsets.UTF_8);
            byte[] newEncryptedJson = Decryptor.rc4Crypt(newJsonBytes, cachedKey);

            byte[] newSectionData = new byte[16 + newEncryptedJson.length];
            System.arraycopy(cachedKey, 0, newSectionData, 0, 16);
            System.arraycopy(newEncryptedJson, 0, newSectionData, 16, newEncryptedJson.length);

            if (newSectionData.length > size) {
                return new ModifyResult(false, null, "修改后的 JSON 过长，溢出节区容量 (" + newSectionData.length + " > " + size + ")");
            }

            // 填充 0x00 保持节区总长度不变
            byte[] paddedData = new byte[size];
            Arrays.fill(paddedData, (byte) 0);
            System.arraycopy(newSectionData, 0, paddedData, 0, newSectionData.length);

            // 写回原 SO 字节数组
            byte[] modifiedSo = Arrays.copyOf(soData, soData.length);
            System.arraycopy(paddedData, 0, modifiedSo, offset, size);

            return new ModifyResult(true, modifiedSo, log.toString());

        } catch (Exception e) {
            return new ModifyResult(false, null, "修改失败: " + e.getMessage());
        }
    }

    private static int[] getArmEpicSectionOffsetAndSize(byte[] soData) {
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
                    return new int[]{(int) offset, (int) size};
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
