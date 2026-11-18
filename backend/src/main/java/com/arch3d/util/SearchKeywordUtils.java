package com.arch3d.util;

import java.util.regex.Pattern;

/**
 * 搜索关键词清洗。
 *
 * 公网扫描器会把 Log4Shell、JNDI/LDAP、HTML 标签等载荷塞进搜索框，
 * 这些字符串会进 search_log，再被热搜聚合原样搬到首页，变成一屏
 * "${jndi:ldap://...}"。这里在入库和展示两个环节都做一次白名单清洗：
 * 只保留“像人输入的搜索词”，扫描器载荷直接丢弃。
 */
public final class SearchKeywordUtils {

    /** 热搜词没必要太长，超过这个长度基本是载荷而不是搜索意图 */
    private static final int MAX_LENGTH = 40;

    /** 明显的脚本/协议/模板注入特征 */
    private static final Pattern DANGEROUS = Pattern.compile(
        "(?i)(jndi|ldap|rmi:|dnslog|javascript:|vbscript:|<script|onerror\\s*=|onload\\s*=|alert\\s*\\(|eval\\s*\\()"
    );

    /** 控制字符（换行、制表、NUL 等）一律不接受 */
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}]");

    /** 至少要有一个中文/字母/数字，纯符号不配当热搜词 */
    private static final Pattern HAS_WORD = Pattern.compile("[\\p{IsHan}A-Za-z0-9]");

    private SearchKeywordUtils() {
    }

    /**
     * 清洗并返回可安全入库/展示的关键词；不合法返回 null。
     */
    public static String normalize(String keyword) {
        if (keyword == null) {
            return null;
        }

        String value = keyword.trim().replaceAll("\\s+", " ");
        if (value.isEmpty() || value.length() > MAX_LENGTH) {
            return null;
        }
        if (CONTROL.matcher(value).find() || !HAS_WORD.matcher(value).find()) {
            return null;
        }

        // 模板/表达式注入、HTML 标签、协议拼接，都不是正常搜索词
        if (value.contains("${") || value.contains("{{") || value.contains("}}")
            || value.contains("<") || value.contains(">") || value.contains("://")) {
            return null;
        }

        // SQL 注入里常见的引号/注释符，热搜展示层也不需要
        if (value.contains("'") || value.contains("\"") || value.contains("`")
            || value.contains(";") || value.contains("--") || value.contains("/*") || value.contains("*/")) {
            return null;
        }

        if (DANGEROUS.matcher(value).find()) {
            return null;
        }

        return value;
    }

    public static boolean isValid(String keyword) {
        return normalize(keyword) != null;
    }
}