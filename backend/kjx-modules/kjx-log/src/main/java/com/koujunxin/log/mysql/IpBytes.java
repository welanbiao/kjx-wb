package com.koujunxin.log.mysql;

import java.net.InetAddress;
import java.util.Arrays;

final class IpBytes {

    private IpBytes() {
    }

    static byte[] from(String ip) {
        if (ip == null || ip.isBlank()) {
            return new byte[16];
        }
        try {
            byte[] raw = InetAddress.getByName(ip.trim()).getAddress();
            if (raw.length == 16) {
                return raw;
            }
            byte[] v6 = new byte[16];
            v6[10] = (byte) 0xff;
            v6[11] = (byte) 0xff;
            System.arraycopy(raw, 0, v6, 12, 4);
            return v6;
        } catch (Exception ex) {
            return new byte[16];
        }
    }

    static String toString(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        try {
            return InetAddress.getByAddress(trim(bytes)).getHostAddress();
        } catch (Exception ex) {
            return Arrays.toString(bytes);
        }
    }

    private static byte[] trim(byte[] bytes) {
        if (bytes.length == 16
                && bytes[10] == (byte) 0xff
                && bytes[11] == (byte) 0xff
                && isZero(bytes, 0, 10)) {
            return Arrays.copyOfRange(bytes, 12, 16);
        }
        return bytes;
    }

    private static boolean isZero(byte[] bytes, int from, int to) {
        for (int i = from; i < to; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return true;
    }
}
