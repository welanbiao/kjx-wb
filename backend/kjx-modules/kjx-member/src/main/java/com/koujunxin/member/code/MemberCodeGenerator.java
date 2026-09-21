package com.koujunxin.member.code;

import java.security.SecureRandom;

import com.koujunxin.member.normalize.MemberCodes;

/**
 * 随机会员编码。格式 {HOST_CODE}-{8 位}，字符集排除 O/0/I/1/L。
 */
public final class MemberCodeGenerator {

    private static final int RANDOM_LEN = 8;
    private final SecureRandom random;

    public MemberCodeGenerator() {
        this(new SecureRandom());
    }

    public MemberCodeGenerator(SecureRandom random) {
        this.random = random;
    }

    public String next(String hostCode) {
        String host = MemberCodes.requireHostCode(hostCode);
        String alphabet = MemberCodes.ALPHABET;
        char[] chars = new char[RANDOM_LEN];
        for (int i = 0; i < RANDOM_LEN; i++) {
            chars[i] = alphabet.charAt(random.nextInt(alphabet.length()));
        }
        return MemberCodes.storedForm(host, new String(chars));
    }
}
