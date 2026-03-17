package com.campusfit.modules.auth.support;

import com.campusfit.common.exception.BusinessException;

public final class UserAuthContext {

    private static final ThreadLocal<UserSession> HOLDER = new ThreadLocal<>();

    private UserAuthContext() {
    }

    public static void set(UserSession session) {
        HOLDER.set(session);
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static UserSession getSession() {
        return HOLDER.get();
    }

    public static Long getCurrentUserId() {
        UserSession session = HOLDER.get();
        return session == null ? null : session.userId();
    }

    public static long requireUserId() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            throw new BusinessException("Please login first");
        }
        return userId;
    }
}