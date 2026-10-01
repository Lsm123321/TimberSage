package com.arch3d.util;

/**
 * 手拼 JSON 请求体时转义引号/反斜杠/控制字符，防止拼坏 JSON 结构。
 * 就两个字符串参数的事，引 Jackson 有点杀鸡用牛刀，写个小工具方法更直接。
 */
public final class JsonEscape {

    private JsonEscape() {
    }

    public static String quote(String raw) {
        if (raw == null) return "\"\"";
        StringBuilder sb = new StringBuilder(raw.length() + 8);
        sb.append('"');
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    // 0x20 以下的控制字符会弄坏 JSON，统一转成 unicode 转义
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
