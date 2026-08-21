package com.feijimiao.xianyuassistant.utils;

import okhttp3.Cookie;
import okhttp3.HttpUrl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SessionCookieJarTest {

    private static final HttpUrl GOOFISH_URL = HttpUrl.get("https://www.goofish.com/im");

    @Test
    void appliesRotatedCookieValue() {
        SessionCookieJar jar = new SessionCookieJar("session=old; keep=value");

        jar.saveFromResponse(GOOFISH_URL, List.of(
                new Cookie.Builder()
                        .name("session")
                        .value("new")
                        .domain("www.goofish.com")
                        .path("/")
                        .build()
        ));

        assertEquals("new", jar.getCookie("session"));
        assertEquals("value", jar.getCookie("keep"));
    }

    @Test
    void removesCookieWhenServerExpiresIt() {
        SessionCookieJar jar = new SessionCookieJar("session=old; keep=value");

        jar.saveFromResponse(GOOFISH_URL, List.of(
                new Cookie.Builder()
                        .name("session")
                        .value("")
                        .domain("www.goofish.com")
                        .path("/")
                        .expiresAt(0)
                        .build()
        ));

        assertFalse(jar.getCookieMap().containsKey("session"));
        assertEquals("value", jar.getCookie("keep"));
    }
}
