package io.unicid.registry.enums;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum ConfigProperties {
    WELCOME, WELCOME2, WELCOME3, ROOT_MENU_TITLE;

    public static List<String> toStringList() {
        return Arrays.stream(ConfigProperties.values())
                .map(Enum::name)
                .collect(Collectors.toList());
    }

    
}
