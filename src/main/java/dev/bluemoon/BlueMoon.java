package dev.bluemoon;

import dev.bluemoon.command.BlueMoonCommand;
import dev.bluemoon.mob.MobListener;
import dev.bluemoon.mob.MobManager;
import dev.bluemoon.model.ModelManager;
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
import java.util.UUID;

/**
 * BlueMoon: MythicMobs style skills for mobs rendered with Blockbench models.
 */
public final class BlueMoon extends JavaPlugin {

    /** Resource pack namespace of generated models. */
    public static final String NAMESPACE = "bluemoon";
    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("bluemoon-resource-pack".getBytes(StandardCharsets.UTF_8));

    private Settings settings;
    private ModelManager models;
    private SkillManager skills;
    private MobManager mobs;
    private PackServer packServer;
    private BukkitTask tickTask;
    private NamespacedKey keyMobType;
    private NamespacedKey keyModel;
    private NamespacedKey keyModelPart;

    @Override
    public void onEnable() {
        keyMobType = new NamespacedKey(this, "mob_type");
        keyModel = new NamespacedKey(this, "model");
        keyModelPart = new NamespacedKey(this, "model_part");

        saveDefaultConfig();
        installExamples();

        models = new ModelManager(this);
        skills = new SkillManager(this);
        mobs = new MobManager(this);
        packServer = new PackServer(getLogger());

        getServer().getPluginManager().registerEvents(new MobListener(this), this);
        BlueMoonCommand command = new BlueMoonCommand(this);
        PluginCommand pc = getCommand("bluemoon");
        if (pc != null) {
            pc.setExecutor(command);
            pc.setTabCompleter(command);
        }

        reloadAll(Bukkit.getConsoleSender());
        tickTask = getServer().getScheduler().runTaskTimer(this, mobs::tick, 1L, 1L);
    }

    @Override
    public void onDisable() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        if (mobs != null) {
            mobs.detachAll();
        }
        if (packServer != null) {
            packServer.stop();
        }
    }

    /** Copies the example model, mob and skill files on first start. */
    private void installExamples() {
        String[] examples = {"models/golem.bbmodel", "mobs/example_mobs.yml", "skills/example_skills.yml"};
        for (String path : examples) {
            String folder = path.substring(0, path.indexOf('/'));
            File dir = new File(getDataFolder(), folder);
            if (!dir.exists()) {
                saveResource(path, false);
            }
        }
        for (String folder : new String[]{"models", "mobs", "skills", "pack-extra"}) {
            new File(getDataFolder(), folder).mkdirs();
        }
    }

    public void reloadAll(CommandSender reporter) {
        reloadConfig();
        settings = Settings.load(getConfig());
        mobs.detachAll();

        models.load();
        models.buildPack();
        skills.load();
        mobs.load();

        packServer.update(models.packZip());
        if (settings.packServerEnabled()) {
            packServer.start(settings.packServerBind(), settings.packServerPort());
        } else {
            packServer.stop();
        }
        mobs.attachLoaded();

        int warnings = models.warnings().size() + skills.errors().size();
        reporter.sendMessage(Text.color("&b[BlueMoon] &f모델 " + models.ids().size() + "개, 스킬 "
                + skills.skillNames().size() + "개, 몹 " + mobs.types().size() + "개를 불러왔습니다."
                + (warnings > 0 ? " &e(경고 " + warnings + "개 - 콘솔 확인)" : "")));
        reporter.sendMessage(Text.color("&b[BlueMoon] &7리소스팩: plugins/BlueMoon/resourcepack.zip (sha1 "
                + models.packSha1() + ")"));
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
        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(PACK_ID, URI.create(base + "/" + sha1 + ".zip"), sha1);
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

    public NamespacedKey keyMobType() {
        return keyMobType;
    }

    public NamespacedKey keyModel() {
        return keyModel;
    }

    public NamespacedKey keyModelPart() {
        return keyModelPart;
    }
}
