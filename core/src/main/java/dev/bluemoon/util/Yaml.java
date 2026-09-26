package dev.bluemoon.util;

import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Case-insensitive helpers for MythicMobs style YAML keys ({@code Skills}, {@code skills}, ...). */
public final class Yaml {

    private Yaml() {
    }

    public static String key(ConfigurationSection sec, String name) {
        if (sec.contains(name)) {
            return name;
        }
        for (String k : sec.getKeys(false)) {
            if (k.equalsIgnoreCase(name)) {
                return k;
            }
        }
        return null;
    }

    public static List<String> stringList(ConfigurationSection sec, String name) {
        String k = key(sec, name);
        return k == null ? List.of() : sec.getStringList(k);
    }

    public static String getString(ConfigurationSection sec, String name, String def) {
        String k = key(sec, name);
        return k == null ? def : sec.getString(k, def);
    }

    public static double getDouble(ConfigurationSection sec, String name, double def) {
        String k = key(sec, name);
        return k == null ? def : sec.getDouble(k, def);
    }

    public static boolean getBoolean(ConfigurationSection sec, String name, boolean def) {
        String k = key(sec, name);
        return k == null ? def : sec.getBoolean(k, def);
    }

    public static ConfigurationSection section(ConfigurationSection sec, String name) {
        String k = key(sec, name);
        return k == null ? null : sec.getConfigurationSection(k);
    }

    /** All {@code .yml} files below a directory, sorted by path. */
    public static List<File> files(File dir) {
        List<File> out = new ArrayList<>();
        collect(dir, ".yml", out);
        out.sort(Comparator.comparing(File::getPath));
        return out;
    }

    public static void collect(File dir, String suffix, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        Arrays.sort(children);
        for (File f : children) {
            if (f.isDirectory()) {
                collect(f, suffix, out);
            } else if (f.getName().toLowerCase().endsWith(suffix)) {
                out.add(f);
            }
        }
    }
}
