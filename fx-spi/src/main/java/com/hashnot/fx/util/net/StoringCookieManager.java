package com.hashnot.fx.util.net;

import com.gargoylesoftware.htmlunit.CookieManager;
import com.gargoylesoftware.htmlunit.util.Cookie;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.util.Set;

/**
 * @author Rafał Krupiński
 */
public class StoringCookieManager extends CookieManager {
    private static final Type TYPE = new TypeToken<Set<Cookie>>() {
    }.getType();
    private File store;
    private Gson gson = new Gson();

    public StoringCookieManager(File store) throws IOException {
        this.store = store;
        if (!store.exists())
            return;
        try (FileReader reader = new FileReader(store)) {
            Set<Cookie> read = gson.fromJson(reader, TYPE);
            for (Cookie cookie : read)
                addCookie(cookie);
        }
    }

    public void close() throws IOException {
        try (Writer reader = new FileWriter(store)) {
            gson.toJson(getCookies(), reader);
        }
    }
}
