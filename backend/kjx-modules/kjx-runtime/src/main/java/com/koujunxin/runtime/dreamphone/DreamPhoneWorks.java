package com.koujunxin.runtime.dreamphone;

/**
 * 梦女小手机。画面来自 v3 单文件，行为走会话与共享工具。
 */
public final class DreamPhoneWorks {

    public static final String CODE = "dream-phone";
    public static final String PLAYER = "\u68A6\u5973";
    public static final String CHARACTER = "\u987E\u897F";
    public static final String CHARACTER_ID = "guxi";
    public static final String UNCOVERED = "\u6F14\u793A\u6A21\u5F0F\u6682\u672A\u8986\u76D6\u8FD9\u53E5";
    public static final int WALLET = 520;

    private DreamPhoneWorks() {
    }

    public static boolean is(String workCode) {
        return CODE.equals(workCode);
    }
}
