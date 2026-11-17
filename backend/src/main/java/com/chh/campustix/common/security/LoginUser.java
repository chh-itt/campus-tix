package com.chh.campustix.common.security;

/** 当前登录用户（JWT 解析产物，存活于请求线程） */
public record LoginUser(Long id, String username, String role) {

    public static final String ROLE_STUDENT = "STUDENT";
    public static final String ROLE_ORGANIZER = "ORGANIZER";
    public static final String ROLE_ADMIN = "ADMIN";

    /** Lombok 风格访问器，便于与实体调用点混用 */
    public String getRole() {
        return role;
    }
}
