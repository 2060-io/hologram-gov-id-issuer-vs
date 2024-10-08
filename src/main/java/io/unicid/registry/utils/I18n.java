package io.unicid.registry.utils;

import java.util.List;
import java.util.UUID;

import io.twentysixty.sa.client.I18n.I18nManager;
import io.unicid.registry.enums.ConfigProperties;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class I18n extends I18nManager {

    private static List<String> defaultMessages = ConfigProperties.toStringList();

    public String getMessage(String messageName, UUID connectionId, String language) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        this.setMessages(defaultMessages);
        return get(messageName, connectionId, language, classLoader);
    }

}

