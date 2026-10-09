package com.boydti.fawe.config;

import com.boydti.fawe.configuration.file.YamlConfiguration;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bundled translations must parse and use the plain ASCII keys that BBC looks up.
 */
class MessageFileTest {

    @ParameterizedTest
    @ValueSource(strings = {"cn", "de", "es", "fr", "it", "nl", "ru", "tr"})
    void translationLoadsWithAsciiKeys(String locale) throws Exception {
        try (InputStream in = MessageFileTest.class.getClassLoader().getResourceAsStream(locale + "/message.yml")) {
            assertNotNull(in, "missing " + locale + "/message.yml");
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            assertNotNull(yml.getString("info.prefix"), locale + " has no info.prefix");
            for (String key : yml.getKeys(true)) {
                assertTrue(key.chars().allMatch(c -> c < 128), locale + " has a non-ASCII key: " + key);
            }
        }
    }
}