package dev.bluemoon;

import dev.bluemoon.command.BaseCommand;
import dev.bluemoon.mob.MobListener;
import dev.bluemoon.mob.MobManager;
import dev.bluemoon.model.ModelManager;
import dev.bluemoon.model.effect.EffectManager;
import dev.bluemoon.model.pack.PackGenerator;
import dev.bluemoon.model.pack.PackServer;
import dev.bluemoon.skill.SkillManager;
import dev.bluemoon.util.Text;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Shared plugin skeleton: Blockbench models, resource pack, skills and model-backed mobs.
 * The mob plugin (BlueMoon) and the player skill plugin (BlueMoonSkills) both extend it.
 */
public abstract class BlueMoonPlugin extends JavaPlugin {

    private Settings settings;
    private ModelManager models;
    private SkillManager skills;
    private MobManager mobs;
    private EffectManager effects;
    private PackServer packServer;
    private BukkitTask tickTask;
    private NamespacedKey keyMobType;
    private NamespacedKey keyModel;
    private NamespacedKey keyModelPart;
    private NamespacedKey keyOwner;
    private UUID packId;

    /** Resource pack namespace for generated models, e.g. {@code bluemoon}. */
    public abstract String namespace();

    /** Name of the command declared in plugin.yml. */
    protected abstract String commandName();

    /** Resources copied to the data folder on first start, e.g. {@code models/golem.bbmodel}. */
    protected abstract List<String> exampleResources();

    /** Folder holding mob definitions (relative to the data folder). */
    public String mobFolder() {
        return "mobs";
    }

    /** Called once after the managers exist; register extra listeners here. */
    protected void setup() {
    }

    /** Called during reload after models and skills are loaded, before the pack is built. */
    protected void reloadExtras() {
    }

    /** Adds plugin specific files (e.g. weapon models) to the resource pack. */
    public void contributePack(PackGenerator generator) {
    }

    /** Adds plugin specific subcommands. */
    protected void registerCommands(BaseCommand command) {
    }

    @Override
    public void onEnable() {
        keyMobType = new NamespacedKey(this, "mob_type");
        keyModel = new NamespacedKey(this, "model");
        keyModelPart = new NamespacedKey(this, "model_part");
        keyOwner = new NamespacedKey(this, "owner");
        packId = UUID.nameUUIDFromBytes((namespace() + "-resource-pack").getBytes(StandardCharsets.UTF_8));

        saveDefaultConfig();
        installExamples();

        models = new ModelManager(this);
        skills = new SkillManager(this);
        mobs = new MobManager(this);
        effects = new EffectManager(this);
        packServer = new PackServer(getLogger());

        getServer().getPluginManager().registerEvents(new MobListener(this), this);
        setup();

        BaseCommand command = new BaseCommand(this);
        registerCommands(command);
        PluginCommand pc = getCommand(commandName());
        if (pc != null) {
            pc.setExecutor(command);
            pc.setTabCompleter(command);
        }

        reloadAll(Bukkit.getConsoleSender());
        tickTask = getServer().getScheduler().runTaskTimer(this, () -> {
            mobs.tick();
            effects.tick();
        }, 1L, 1L);
    }

    @Override
    public void onDisable() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        if (effects != null) {
            effects.removeAll();
        }
        if (mobs != null) {
            mobs.detachAll();
        }
        if (packServer != null) {
            packServer.stop();
        }
    }

    private void installExamples() {
        List<String> folders = new ArrayList<>(List.of("models", mobFolder(), "skills", "pack-extra"));
        for (String path : exampleResources()) {
            String folder = path.substring(0, path.indexOf('/'));
            if (!new File(getDataFolder(), folder).exists() || !new File(getDataFolder(), path).exists()
                    && !new File(getDataFolder(), ".installed").exists()) {
                saveResource(path, false);
            }
            if (!folders.contains(folder)) {
                folders.add(folder);
            }
        }
        for (String folder : folders) {
            new File(getDataFolder(), folder).mkdirs();
        }
        try {
            new File(getDataFolder(), ".installed").createNewFile();
        } catch (java.io.IOException ignored) {
            // only used to avoid re-installing deleted examples
        }
    }

    public void reloadAll(CommandSender reporter) {
        reloadConfig();
        settings = Settings.load(getConfig());
        effects.removeAll();
        mobs.detachAll();

        models.load();
        skills.load();
        mobs.load();
        reloadExtras();
        models.buildPack();

        packServer.update(models.packZip());
        if (settings.packServerEnabled()) {
            packServer.start(settings.packServerBind(), settings.packServerPort());
        } else {
            packServer.stop();
        }
        mobs.attachLoaded();

        int warnings = models.warnings().size() + skills.errors().size();
        reporter.sendMessage(Text.color("&b[" + getName() + "] &f모델 " + models.ids().size() + "개, 스킬 "
                + skills.skillNames().size() + "개, 몹 " + mobs.types().size() + "개를 불러왔습니다."
                + (warnings > 0 ? " &e(경고 " + warnings + "개 - 콘솔 확인)" : "")));
        reporter.sendMessage(Text.color("&b[" + getName() + "] &7리소스팩: plugins/" + getName()
                + "/resourcepack.zip (sha1 " + models.packSha1() + ")"));
    }

    /** Sends the generated pack to a player when the built-in pack server is enabled. */
    public void sendPack(Player player) {
        if (!settings.packServerEnabled() || !packServer.isRunning() || models.packZip().length == 0) {
            return;
        }
        String base = settings.packPublicUrl();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String sha1 = models.packSha1();
        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(packId, URI.create(base + "/" + sha1 + ".zip"), sha1);
        player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(info)
                .required(settings.packRequired())
                .prompt(Text.color(settings.packPrompt()))
                .replace(false)
                .build());
    }

    public Settings settings() {
        return settings;
    }

    public ModelManager models() {
        return models;
    }

    public SkillManager skills() {
        return skills;
    }

    public MobManager mobs() {
        return mobs;
    }

    public EffectManager effects() {
        return effects;
    }

    public NamespacedKey keyMobType() {
        return keyMobType;
    }

    public NamespacedKey keyModel() {
        return keyModel;
    }

    public NamespacedKey keyModelPart() {
        return keyModelPart;
    }

    public NamespacedKey keyOwner() {
        return keyOwner;
    }
}
