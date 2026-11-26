package android.content;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/** Only the operations used by the production classes. String sets contain decimal question IDs. */
public final class SharedPreferences {
    public static int writes;
    private final Properties data = new Properties();
    private final Path file;

    SharedPreferences(Path file) {
        this.file = file;
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                data.load(in);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }
    }

    public int getInt(String key, int fallback) {
        return Integer.parseInt(data.getProperty(key, Integer.toString(fallback)));
    }

    public long getLong(String key, long fallback) {
        return Long.parseLong(data.getProperty(key, Long.toString(fallback)));
    }

    public Set<String> getStringSet(String key, Set<String> fallback) {
        String value = data.getProperty(key);
        if (value == null) return fallback;
        if (value.isEmpty()) return Collections.emptySet();
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(value.split(","))));
    }

    public Editor edit() { return new Editor(); }

    public final class Editor {
        private final Properties pending = new Properties();
        public Editor putInt(String key, int value) { pending.setProperty(key, Integer.toString(value)); return this; }
        public Editor putLong(String key, long value) { pending.setProperty(key, Long.toString(value)); return this; }
        public Editor putStringSet(String key, Set<String> values) {
            pending.setProperty(key, String.join(",", values));
            return this;
        }

        public void apply() {
            writes++;
            data.putAll(pending);
            try (OutputStream out = Files.newOutputStream(file)) {
                data.store(out, null);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }
    }
}
