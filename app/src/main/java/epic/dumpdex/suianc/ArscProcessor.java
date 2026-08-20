package epic.dumpdex.suianc;

import com.reandroid.arsc.chunk.TableBlock;
import com.reandroid.arsc.chunk.xml.ResXmlDocument;
import com.reandroid.arsc.item.StringItem;
import com.reandroid.arsc.pool.StringPool;
import com.reandroid.arsc.pool.TableStringPool;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class ArscProcessor {

    public interface OnLogListener {
        void onLog(String msg, String type);
    }

    public static class Result {
        public int arscModifiedCount;
        public int axmlModifiedCount;
        public List<String> mappingLog;

        public Result(int arscModifiedCount, int axmlModifiedCount, List<String> mappingLog) {
            this.arscModifiedCount = arscModifiedCount;
            this.axmlModifiedCount = axmlModifiedCount;
            this.mappingLog = mappingLog;
        }
    }

    /**
     * 处理 resources.arsc
     */
    public static Result processArscAndManifest(byte[] arscBytes, byte[] manifestBytes, JSONObject config, File arscOutFile, File manifestOutFile, OnLogListener listener) throws Exception {
        if (!config.has("resource_string_protection_method")) {
            throw new IllegalArgumentException("配置中未找到 resource_string_protection_method 字段");
        }

        int method = config.getInt("resource_string_protection_method");
        Object keyObj = (method == 0)
                ? config.get("resource_string_protection_xor_key")
                : config.get("resource_string_protection_rc4_key");

        if (listener != null) {
            listener.onLog("ARSC/Manifest 字符串解密算法: " + (method == 0 ? "XOR" : "RC4"), "info");
        }

        int arscCount = 0;
        List<String> mappingLog = new ArrayList<>();

        // 1. 处理 resources.arsc
        if (arscBytes != null && arscOutFile != null) {
            TableBlock tableBlock = new TableBlock();
            tableBlock.readBytes(new ByteArrayInputStream(arscBytes));

            TableStringPool stringPool = tableBlock.getTableStringPool();
            if (stringPool != null) {
                for (int i = 0; i < stringPool.size(); i++) {
                    StringItem stringItem = stringPool.get(i);
                    if (stringItem == null) continue;

                    String s = stringItem.get();
                    if (s != null && s.startsWith("EP_")) {
                        String rawBase64 = s.substring(3);
                        byte[] enc;
                        try {
                            enc = Base64.getDecoder().decode(rawBase64);
                        } catch (Exception e) {
                            continue;
                        }

                        byte[] dec = (method == 0)
                                ? Decryptor.xorCrypt(enc, keyObj)
                                : Decryptor.rc4Crypt(enc, keyObj);

                        try {
                            String plain = new String(dec, StandardCharsets.UTF_8);
                            mappingLog.add(s + "  -->  " + plain);
                            stringItem.set(plain);
                            arscCount++;
                        } catch (Exception ignored) {}
                    }
                }
            }

            tableBlock.refresh();
            try (FileOutputStream fos = new FileOutputStream(arscOutFile)) {
                tableBlock.writeBytes(fos);
            }
        }

        // 2. 处理 AndroidManifest.xml 字符串池
        int axmlCount = 0;
        if (manifestBytes != null && manifestOutFile != null) {
            ResXmlDocument xmlDocument = new ResXmlDocument();
            xmlDocument.readBytes(new ByteArrayInputStream(manifestBytes));

            StringPool<?> stringPool = xmlDocument.getStringPool();
            if (stringPool != null) {
                for (int i = 0; i < stringPool.size(); i++) {
                    var stringItem = stringPool.get(i);
                    if (stringItem == null) continue;

                    String s = stringItem.get();
                    if (s != null && s.startsWith("EP_")) {
                        String rawBase64 = s.substring(3);
                        byte[] enc;
                        try {
                            enc = Base64.getDecoder().decode(rawBase64);
                        } catch (Exception e) {
                            continue;
                        }

                        byte[] dec = (method == 0)
                                ? Decryptor.xorCrypt(enc, keyObj)
                                : Decryptor.rc4Crypt(enc, keyObj);

                        try {
                            String plain = new String(dec, StandardCharsets.UTF_8);
                            stringItem.set(plain);
                            axmlCount++;
                        } catch (Exception ignored) {}
                    }
                }
            }

            xmlDocument.refresh();
            try (FileOutputStream fos = new FileOutputStream(manifestOutFile)) {
                xmlDocument.writeBytes(fos);
            }
        }

        return new Result(arscCount, axmlCount, mappingLog);
    }
}
