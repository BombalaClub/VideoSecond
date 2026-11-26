package android.content;

import android.content.res.AssetManager;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Host-test context. Each run uses a private temporary directory. */
public final class Context {
    public static final int MODE_PRIVATE = 0;
    private final Path state;
    private final AssetManager assets;
    private final Map<String, SharedPreferences> files = new HashMap<>();

    public Context(Path state, Path assets) {
        this.state = state;
        this.assets = new AssetManager(assets);
    }

    public AssetManager getAssets() { return assets; }

    public synchronized SharedPreferences getSharedPreferences(String name, int mode) {
        if (mode != MODE_PRIVATE) throw new AssertionError("Preferences must remain private");
        return files.computeIfAbsent(name, key -> new SharedPreferences(state.resolve(key + ".properties")));
    }
}
