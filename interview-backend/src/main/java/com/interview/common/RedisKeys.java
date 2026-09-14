package com.interview.common;

/**
 * Redis Key 统一管理：浏览量、缓存、验证码、限流等 key 前缀与拼装集中于此，避免散落魔法字符串。
 */
public final class RedisKeys {

    private RedisKeys() {
    }

    /** 浏览量未落库增量 Hash：field = articleId。 */
    public static final String VIEW_COUNTER = "view:counter";

    public static final String CACHE_CATEGORY_TREE = "cache:category:tree";
    public static final String CACHE_CONTENT_VERSION = "cache:content:version";

    private static final String ARTICLE_LIST_PREFIX = "cache:article:list:v";
    private static final String ARTICLE_DETAIL_PREFIX = "cache:article:detail:v";
    private static final String HOME_OVERVIEW_PREFIX = "cache:home:overview:v";

    /** 浏览去重：同一用户 24h 内同一题目只计一次。 */
    public static String viewDedup(Long userId, Long articleId) {
        return "view:user:" + userId + ":article:" + articleId;
    }

    public static String loginRate(String account) {
        return "rate:login:" + account;
    }

    /** 登录失败累计次数（24 小时窗口）：用于阶梯式冷却判定。 */
    public static String loginFail(String account) {
        return "login:fail:" + account;
    }

    /** 登录冷却标记：存在即处于冷却中，TTL 即剩余冷却时间。 */
    public static String loginLock(String account) {
        return "login:lock:" + account;
    }

    /** 用户投稿：当日已提交篇数（每天上限 3 篇）。 */
    public static String userUploadDay(Long userId) {
        return "rate:user-upload:day:" + userId;
    }

    public static String emailCode(String email, String scene) {
        return "code:email:" + email + ":" + scene;
    }

    public static String emailCodeTries(String email, String scene) {
        return emailCode(email, scene) + ":tries";
    }

    public static String emailCodeCooldown(String email, String scene) {
        return emailCode(email, scene) + ":cooldown";
    }

    public static String emailDailyRate(String email) {
        return "rate:code:email:" + email + ":day";
    }

    public static String articleListKey(long version, Long categoryId, String difficulty, long page, long size) {
        return ARTICLE_LIST_PREFIX + version + ":" + categoryId + ":" + (difficulty == null ? "" : difficulty)
                + ":" + page + ":" + size;
    }

    public static String articleDetailKey(long version, String slug) {
        return ARTICLE_DETAIL_PREFIX + version + ":" + slug;
    }

    public static String homeOverviewKey(long version) {
        return HOME_OVERVIEW_PREFIX + version;
    }
}
