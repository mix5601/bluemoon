package dev.bluemoon.skills;

import dev.bluemoon.BlueMoonPlugin;
import dev.bluemoon.command.BaseCommand;
import dev.bluemoon.mob.ActiveMob;
import dev.bluemoon.model.pack.PackGenerator;
import dev.bluemoon.skill.Skill;
import dev.bluemoon.skill.SkillMeta;
import dev.bluemoon.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * BlueMoonSkills: player skills written in MythicMobs syntax, with Blockbench effect models,
 * summons and weapon models. How skills are triggered is up to you: call {@link #cast} from your
 * own plugin, or run {@code /bms cast <skill> <player>} from the console / other plugins.
 */
public final class BlueMoonSkills extends BlueMoonPlugin {

    private static BlueMoonSkills instance;
    private WeaponManager weapons;

    /** The running plugin instance (API entry point). */
    public static BlueMoonSkills get() {
        return instance;
    }

    @Override
    public String namespace() {
        return "bmskills";
    }

    @Override
    protected String commandName() {
        return "bmskills";
    }

    @Override
    public String mobFolder() {
        return "summons";
    }

    @Override
    protected List<String> exampleResources() {
        return List.of(
                "models/slash.bbmodel",
                "models/fireball.bbmodel",
                "models/spirit_wolf.bbmodel",
                "models/star_slash.bbmodel",
                "models/wither_vortex.bbmodel",
                "models/radiant_skull.bbmodel",
                "weapons/moon_sword.bbmodel",
                "weapons/star_greatsword.bbmodel",
                "weapons/star_greatsword.yml",
                "summons/example_summons.yml",
                "skills/example_player_skills.yml",
                "skills/star_greatsword_skills.yml");
    }

    @Override
    protected void setup() {
        instance = this;
        weapons = new WeaponManager(this);
        WeaponListener listener = new WeaponListener(this);
        getServer().getPluginManager().registerEvents(listener, this);
        getServer().getScheduler().runTaskTimer(this, listener::tick, 1L, 1L);
        // ?holding{weapon=star_greatsword}: the player holds that weapon in the main hand
        skills().registerCondition(p -> {
            String id = p.getString("", "weapon", "w", "id");
            return (meta, e) -> e instanceof Player pl
                    && id.equalsIgnoreCase(weapons.weaponOf(pl.getInventory().getItemInMainHand()));
        }, "holding", "holdingweapon");
    }

    @Override
    protected void reloadExtras() {
        weapons.load();
    }

    @Override
    public void contributePack(PackGenerator generator) {
        weapons.contribute(generator);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        instance = null;
    }

    // --- API -------------------------------------------------------------------

    /** Casts a skill as the player; {@code @trigger} / {@code @target} is what the player looks at. */
    public CastResult cast(Player player, String skillName) {
        return cast(player, skillName, player.getTargetEntity(32));
    }

    /**
     * Casts a skill as the player.
     *
     * @param target becomes {@code @trigger}; may be null
     */
    public CastResult cast(Player player, String skillName, Entity target) {
        Skill skill = skills().skill(skillName);
        if (skill == null) {
            return CastResult.UNKNOWN_SKILL;
        }
        double remaining = skills().cooldownRemaining(player, skill.name());
        if (remaining > 0) {
            if (getConfig().getBoolean("messages.show-cooldown", true)) {
                String msg = getConfig().getString("messages.cooldown", "&c<skill> &7재사용 대기 &f<remaining>초")
                        .replace("<skill>", skill.name())
                        .replace("<remaining>", String.format(Locale.ROOT, "%.1f", remaining));
                player.sendActionBar(Text.color(msg));
            }
            return CastResult.COOLDOWN;
        }
        PlayerSkillCastEvent event = new PlayerSkillCastEvent(player, skill.name(), target);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return CastResult.CANCELLED;
        }
        return skill.execute(SkillMeta.create(this, player, null, target)) ? CastResult.SUCCESS : CastResult.CONDITIONS;
    }

    /** Seconds until the skill can be cast again (0 = ready). */
    public double cooldown(Player player, String skillName) {
        Skill skill = skills().skill(skillName);
        return skill == null ? 0 : skills().cooldownRemaining(player, skill.name());
    }

    public boolean hasSkill(String skillName) {
        return skills().skill(skillName) != null;
    }

    /** Summons currently owned by the player. */
    public List<ActiveMob> summons(Player player) {
        return mobs().summonsOf(player);
    }

    public WeaponManager weapons() {
        return weapons;
    }

    // --- commands ----------------------------------------------------------------

    @Override
    protected void registerCommands(BaseCommand command) {
        command.register("cast", "<스킬> [플레이어] [-s]", "플레이어가 스킬 사용 (콘솔 가능, -s 는 결과 메시지 숨김)", (sender, args) -> {
            if (args.length < 2) {
                command.send(sender, "사용법: /bms cast <스킬> [플레이어] [-s]");
                return;
            }
            boolean silent = List.of(args).contains("-s");
            Player player;
            if (args.length > 2 && !args[2].equals("-s")) {
                player = Bukkit.getPlayerExact(args[2]);
                if (player == null) {
                    command.send(sender, "&c플레이어 '" + args[2] + "' 가 접속해 있지 않습니다.");
                    return;
                }
            } else if (sender instanceof Player p) {
                player = p;
            } else {
                command.send(sender, "콘솔에서는 플레이어를 지정하세요.");
                return;
            }
            CastResult result = cast(player, args[1]);
            if (!silent) {
                command.send(sender, switch (result) {
                    case SUCCESS -> args[1] + " 사용";
                    case UNKNOWN_SKILL -> "&c스킬 '" + args[1] + "' 이 없습니다.";
                    case COOLDOWN -> "&e재사용 대기 중입니다.";
                    case CONDITIONS -> "&e조건이 맞지 않아 사용되지 않았습니다.";
                    case CANCELLED -> "&e다른 플러그인이 취소했습니다.";
                });
            }
        }, (sender, args) -> {
            if (args.length == 2) {
                return new ArrayList<>(skills().skillNames());
            }
            if (args.length == 3) {
                List<String> names = new ArrayList<>();
                Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
                return names;
            }
            return List.of();
        });

        command.register("weapon", "<무기> [플레이어] [재질]", "무기 모델 아이템 지급", (sender, args) -> {
            if (args.length < 2 || !weapons.exists(args[1])) {
                command.send(sender, "&c무기를 지정하세요: " + String.join(", ", weapons.ids()));
                return;
            }
            Player player = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : sender instanceof Player p ? p : null;
            if (player == null) {
                command.send(sender, "&c받을 플레이어를 찾을 수 없습니다.");
                return;
            }
            Material base = weapons.type(args[1]).material();
            if (args.length > 3) {
                Material m = Material.matchMaterial(args[3]);
                if (m == null || !m.isItem()) {
                    command.send(sender, "&c알 수 없는 재질: " + args[3]);
                    return;
                }
                base = m;
            }
            player.getInventory().addItem(weapons.item(args[1], base));
            command.send(sender, player.getName() + " 에게 무기 '" + args[1] + "' 지급 (아이템 모델 "
                    + weapons.modelKey(weapons.type(args[1]).model()) + ")");
        }, (sender, args) -> {
            if (args.length == 2) {
                return new ArrayList<>(weapons.ids());
            }
            if (args.length == 3) {
                List<String> names = new ArrayList<>();
                Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
                return names;
            }
            return args.length == 4 ? List.of("netherite_sword", "diamond_sword", "iron_sword", "stick") : List.of();
        });

        command.register("dismiss", "[플레이어]", "소환수 해제", (sender, args) -> {
            Player player = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player p ? p : null;
            if (player == null) {
                command.send(sender, "&c플레이어를 찾을 수 없습니다.");
                return;
            }
            List<ActiveMob> list = summons(player);
            list.forEach(ActiveMob::expire);
            command.send(sender, player.getName() + " 의 소환수 " + list.size() + "마리를 해제했습니다.");
        }, null);
    }
}
