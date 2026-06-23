package dev.davimf.basebot.modules.sales.pix;

/**
 * CRC-16/CCITT-FALSE (polynomial 0x1021, initial value 0xFFFF, no reflection,
 * xor-out 0x0000) — the checksum required at the end of a Pix BR Code (tag 63).
 */
public final class Crc16 {

    private Crc16() {}

    public static int ccittFalse(byte[] data) {
        int crc = 0xFFFF;
        for (byte b : data) {
            crc ^= (b & 0xFF) << 8;
            for (int i = 0; i < 8; i++) {
                if ((crc & 0x8000) != 0) {
                    crc = (crc << 1) ^ 0x1021;
                } else {
                    crc <<= 1;
                }
                crc &= 0xFFFF;
            }
        }
        return crc & 0xFFFF;
    }

    public static String hex4(byte[] data) {
        return String.format("%04X", ccittFalse(data));
    }
}
