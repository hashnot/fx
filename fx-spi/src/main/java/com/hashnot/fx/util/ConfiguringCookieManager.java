package com.hashnot.fx.util;

import com.gargoylesoftware.htmlunit.CookieManager;
import com.gargoylesoftware.htmlunit.util.Cookie;

import java.net.URL;
import java.util.Collections;
import java.util.Date;
import java.util.Set;

/**
 * @author Rafał Krupiński
 */
public class ConfiguringCookieManager extends CookieManager {
    private CookieManager backend;

    public ConfiguringCookieManager(CookieManager backend) {
        super();
        assert backend != null;
        this.backend = backend;
    }

    @Override
    public synchronized void setCookiesEnabled(boolean enabled) {
        backend.setCookiesEnabled(enabled);
    }

    @Override
    public synchronized boolean isCookiesEnabled() {
        return backend.isCookiesEnabled();
    }

    @Override
    public synchronized Set<Cookie> getCookies() {
        if (!isCookiesEnabled())
            return Collections.emptySet();
        return backend.getCookies();
    }

    @Override
    public synchronized Set<Cookie> getCookies(URL url) {
        if (!isCookiesEnabled())
            return Collections.emptySet();
        return backend.getCookies(url);
    }

    @Override
    public synchronized boolean clearExpired(Date date) {
        if (!isCookiesEnabled())
            return false;
        return backend.clearExpired(date);
    }

    @Override
    public synchronized Cookie getCookie(String name) {
        if (!isCookiesEnabled())
            return null;
        return backend.getCookie(name);
    }

    @Override
    public synchronized void addCookie(Cookie cookie) {
        if (!isCookiesEnabled())
            return;
        backend.addCookie(cookie);
    }

    @Override
    public synchronized void removeCookie(Cookie cookie) {
        if (!isCookiesEnabled())
            return;
        backend.removeCookie(cookie);
    }

    @Override
    public synchronized void clearCookies() {
        if (!isCookiesEnabled())
            return;
        backend.clearCookies();
    }
}
