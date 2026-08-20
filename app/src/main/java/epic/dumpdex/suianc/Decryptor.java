package epic.dumpdex.suianc;

import java.nio.charset.StandardCharsets;

public class Decryptor {

    public static byte[] rc4Crypt(byte[] data, Object keyObj) {
        byte[] key = (keyObj instanceof byte[])
                ? (byte[]) keyObj
                : keyObj.toString().getBytes(StandardCharsets.UTF_8);

        int[] S = new int[256];
        for (int i = 0; i < 256; i++) S[i] = i;

        int j = 0;
        for (int i = 0; i < 256; i++) {
            j = (j + S[i] + (key[i % key.length] & 0xFF)) % 256;
            int temp = S[i]; S[i] = S[j]; S[j] = temp;
        }

        int i = 0; j = 0;
        byte[] out = new byte[data.length];
        for (int k = 0; k < data.length; k++) {
            i = (i + 1) % 256;
            j = (j + S[i]) % 256;
            int temp = S[i]; S[i] = S[j]; S[j] = temp;
            out[k] = (byte) ((data[k] & 0xFF) ^ S[(S[i] + S[j]) % 256]);
        }
        return out;
    }

    public static byte[] xorCrypt(byte[] data, Object keyObj) {
        int key;
        if (keyObj instanceof Number) {
            key = ((Number) keyObj).intValue();
        } else {
            String strKey = keyObj.toString();
            key = strKey.startsWith("0x") || strKey.startsWith("0X")
                    ? Integer.parseInt(strKey.substring(2), 16)
                    : Integer.parseInt(strKey);
        }

        byte[] out = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = (byte) ((data[i] & 0xFF) ^ key);
        }
        return out;
    }
}
