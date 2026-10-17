package AIHelp;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Loads editable AI instructions and the structured-output schema. */
public class AIKnowledgeBase {

    private static final String CONFIG_ENV = "SMARTSWITCH_AI_CONFIG_DIR";
    private static final String CONFIG_PROPERTY = "smartswitch.ai.config.dir";
    private static final String RULES_FILE = "query-plan-rules.md";
    private static final String SCHEMA_FILE = "query-plan-schema.json";

    private final String rules;
    private final String schema;

    public AIKnowledgeBase() {
        rules = load(RULES_FILE);
        schema = load(SCHEMA_FILE);
    }

    public String getRules() {
        return rules;
    }

    public String getSchema() {
        return schema;
    }

    private String load(String fileName) {
        String configuredDirectory = System.getProperty(CONFIG_PROPERTY);
        if (configuredDirectory == null || configuredDirectory.trim().isEmpty()) {
            configuredDirectory = System.getenv(CONFIG_ENV);
        }
        if (configuredDirectory != null && !configuredDirectory.trim().isEmpty()) {
            File file = new File(configuredDirectory, fileName);
            if (file.isFile()) {
                try (InputStream input = new FileInputStream(file)) {
                    return read(input);
                } catch (IOException e) {
                    throw new IllegalStateException("Cannot read AI configuration: " + file, e);
                }
            }
        }

        String resource = "/AIHelp/" + fileName;
        try (InputStream input = AIKnowledgeBase.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing AI configuration resource: " + resource);
            }
            return read(input);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read AI configuration resource: " + resource, e);
        }
    }

    private String read(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
}
