package dev.bluemoon.model;

import dev.bluemoon.BlueMoon;
import dev.bluemoon.Settings;
import dev.bluemoon.model.bbmodel.BBModel;
import dev.bluemoon.model.bbmodel.BBModelParser;
import dev.bluemoon.model.pack.PackGenerator;
import dev.bluemoon.model.runtime.ModelBlueprint;
import dev.bluemoon.util.Yaml;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Loads {@code .bbmodel} files from {@code plugins/BlueMoon/models} and builds the resource pack
 * ({@code plugins/BlueMoon/resourcepack.zip}).
 */
public final class ModelManager {

    private final BlueMoon plugin;
    private final Map<String, ModelBlueprint> blueprints = new LinkedHashMap<>();
    private final List<String> warnings = new ArrayList<>();
    private byte[] packZip = new byte[0];
    private String packSha1 = "";

    public ModelManager(BlueMoon plugin) {
        this.plugin = plugin;
    }

    public void load() {
        blueprints.clear();
        warnings.clear();
        File dir = new File(plugin.getDataFolder(), "models");
        List<File> files = new ArrayList<>();
        Yaml.collect(dir, ".bbmodel", files);
        for (File file : files) {
            String id = ModelBlueprint.sanitize(file.getName().replaceFirst("(?i)\\.bbmodel$", ""));
            if (blueprints.containsKey(id)) {
                warn(file.getName() + ": 같은 이름의 모델이 이미 있습니다 (" + id + ")");
                continue;
            }
            try {
                BBModel model = BBModelParser.parse(file.toPath());
                ModelBlueprint bp = ModelBlueprint.build(id, model);
                for (String w : bp.warnings) {
                    warn(file.getName() + ": " + w);
                }
                blueprints.put(id, bp);
            } catch (IOException | RuntimeException e) {
                warn(file.getName() + ": 읽기 실패 - " + e.getMessage());
            }
        }
    }

    public void buildPack() {
        Settings s = plugin.settings();
        PackGenerator gen = new PackGenerator(BlueMoon.NAMESPACE, s.packMinFormat(), s.packMaxFormat(), s.packDescription());
        for (ModelBlueprint bp : blueprints.values()) {
            gen.addModel(bp);
        }
        // user files merged into the pack (sounds, extra textures, ...)
        Path extra = new File(plugin.getDataFolder(), "pack-extra").toPath();
        if (Files.isDirectory(extra)) {
            try (Stream<Path> walk = Files.walk(extra)) {
                for (Path p : walk.filter(Files::isRegularFile).toList()) {
                    gen.addFile(extra.relativize(p).toString(), Files.readAllBytes(p));
                }
            } catch (IOException e) {
                warn("pack-extra 폴더를 읽지 못했습니다: " + e.getMessage());
            }
        }
        for (String w : PackGenerator.validate(gen.files())) {
            warn(w);
        }
        try {
            packZip = gen.buildZip();
            packSha1 = PackGenerator.sha1(packZip);
            Files.write(new File(plugin.getDataFolder(), "resourcepack.zip").toPath(), packZip);
        } catch (IOException e) {
            warn("리소스팩 생성 실패: " + e.getMessage());
        }
    }

    private void warn(String message) {
        warnings.add(message);
        plugin.getLogger().warning(message);
    }

    public ModelBlueprint get(String id) {
        return id == null ? null : blueprints.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<String> ids() {
        return Collections.unmodifiableSet(blueprints.keySet());
    }

    public List<String> warnings() {
        return Collections.unmodifiableList(warnings);
    }

    public byte[] packZip() {
        return packZip;
    }

    public String packSha1() {
        return packSha1;
    }
}
