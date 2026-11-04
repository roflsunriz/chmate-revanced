package app.revanced.extension.chmate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Field;
import java.util.Locale;
import org.junit.jupiter.api.Test;

final class IntegrationStringsTest {
    @Test
    void allSupportedLanguagesHaveNonemptyIntegrationLabels() throws Exception {
        Locale original = Locale.getDefault();
        try {
            for (String language : new String[]{"ja", "en", "zh", "hi", "es", "fr", "ar", "pt", "bn", "ru", "ur"}) {
                Locale.setDefault(Locale.forLanguageTag(language));
                IntegrationStrings strings = IntegrationStrings.current();
                for (Field field : IntegrationStrings.class.getDeclaredFields()) {
                    if (field.getType() != String.class) continue;
                    String value = (String) field.get(strings);
                    assertNotNull(value, language + ": " + field.getName());
                    assertFalse(value.trim().isEmpty(), language + ": " + field.getName());
                }
                assertFalse(strings.openBoard().trim().isEmpty(), language);
            }
        } finally {
            Locale.setDefault(original);
        }
    }
}
