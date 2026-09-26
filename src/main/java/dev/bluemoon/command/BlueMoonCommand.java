package dev.bluemoon.command;

import dev.bluemoon.BlueMoon;
import dev.bluemoon.mob.ActiveMob;
import dev.bluemoon.mob.MobType;
import dev.bluemoon.skill.Skill;
import dev.bluemoon.skill.SkillMeta;
import dev.bluemoon.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** {@code /bluemoon} (alias {@code /bm}). */
public final class BlueMoonCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("reload", "spawn", "list", "cast", "anim", "pack", "killall");

    private final BlueMoon plugin;

    public BlueMoonCommand(BlueMoon plugin) {
        this.plugin = plugin;
    }

    private static void send(CommandSender to, String message) {
        to.sendMessage(Text.color("&b[BlueMoon] &f" + message));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender, label);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> plugin.reloadAll(sender);
            case "spawn" -> spawn(sender, args);
            case "list" -> list(sender, args);
            case "cast" -> cast(sender, args);
            case "anim" -> anim(sender, args);
            case "pack" -> {
                plugin.models().load();
                plugin.models().buildPack();
                send(sender, "리소스팩을 다시 만들었습니다. sha1 " + plugin.models().packSha1());
                Bukkit.getOnlinePlayers().forEach(plugin::sendPack);
            }
            case "killall" -> send(sender, plugin.mobs().removeAll() + "마리를 제거했습니다.");
            default -> help(sender, label);
        }
        return true;
    }

    private void help(CommandSender sender, String label) {
        send(sender, "명령어 목록");
        sender.sendMessage(Text.color("&7/" + label + " reload &f- 모델/스킬/몹 다시 불러오기 + 리소스팩 생성"));
        sender.sendMessage(Text.color("&7/" + label + " spawn <몹> [수량] &f- 바라보는 위치에 소환"));
        sender.sendMessage(Text.color("&7/" + label + " list [mobs|skills|models] &f- 목록"));
        sender.sendMessage(Text.color("&7/" + label + " cast <스킬> &f- 내가 시전자가 되어 스킬 실행"));
        sender.sendMessage(Text.color("&7/" + label + " anim <애니메이션> &f- 가장 가까운 모델 몹에게 애니메이션 재생"));
        sender.sendMessage(Text.color("&7/" + label + " pack &f- 리소스팩만 다시 생성 후 전송"));
        sender.sendMessage(Text.color("&7/" + label + " killall &f- BlueMoon 몹 모두 제거"));
    }

    private void spawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "플레이어만 사용할 수 있습니다.");
            return;
        }
        if (args.length < 2) {
            send(sender, "사용법: /bm spawn <몹> [수량]");
            return;
        }
        MobType type = plugin.mobs().type(args[1]);
        if (type == null) {
            send(sender, "&c몹 '" + args[1] + "' 이 없습니다.");
            return;
        }
        int amount = 1;
        if (args.length > 2) {
            try {
                amount = Math.max(1, Math.min(50, Integer.parseInt(args[2])));
            } catch (NumberFormatException e) {
                send(sender, "&c수량은 숫자여야 합니다.");
                return;
            }
        }
        Location at = player.getTargetBlockExact(32) != null
                ? player.getTargetBlockExact(32).getLocation().add(0.5, 1, 0.5)
                : player.getLocation();
        at.setYaw(player.getLocation().getYaw() + 180);
        for (int i = 0; i < amount; i++) {
            plugin.mobs().spawn(type, at);
        }
        send(sender, type.id() + " " + amount + "마리를 소환했습니다.");
    }

    private void list(CommandSender sender, String[] args) {
        String what = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "mobs";
        switch (what) {
            case "skills" -> send(sender, "스킬: " + String.join(", ", plugin.skills().skillNames()));
            case "models" -> {
                List<String> names = new ArrayList<>();
                for (String id : plugin.models().ids()) {
                    var bp = plugin.models().get(id);
                    names.add(id + " &7(본 " + bp.bones.size() + ", 애니메이션 " + String.join("/", bp.source.animations.keySet()) + ")&f");
                }
                send(sender, "모델: " + String.join(", ", names));
            }
            default -> {
                List<String> names = new ArrayList<>();
                plugin.mobs().types().forEach(t -> names.add(t.id()));
                send(sender, "몹: " + String.join(", ", names) + " &7(활성 " + plugin.mobs().active().size() + ")");
            }
        }
    }

    private void cast(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "플레이어만 사용할 수 있습니다.");
            return;
        }
        if (args.length < 2) {
            send(sender, "사용법: /bm cast <스킬>");
            return;
        }
        Skill skill = plugin.skills().skill(args[1]);
        if (skill == null) {
            send(sender, "&c스킬 '" + args[1] + "' 이 없습니다.");
            return;
        }
        Entity target = player.getTargetEntity(32);
        boolean ok = skill.execute(SkillMeta.create(plugin, player, null, target));
        send(sender, ok ? skill.name() + " 실행" : "&e조건/쿨타임 때문에 실행되지 않았습니다.");
    }

    private void anim(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "플레이어만 사용할 수 있습니다.");
            return;
        }
        if (args.length < 2) {
            send(sender, "사용법: /bm anim <애니메이션>");
            return;
        }
        Location loc = player.getLocation();
        ActiveMob nearest = plugin.mobs().active().stream()
                .filter(m -> m.model() != null && !m.isDying() && m.location().getWorld().equals(loc.getWorld()))
                .filter(m -> m.location().distanceSquared(loc) < 32 * 32)
                .min(Comparator.comparingDouble(m -> m.location().distanceSquared(loc)))
                .orElse(null);
        if (nearest == null) {
            send(sender, "&c주변 32블록 안에 모델이 있는 몹이 없습니다.");
            return;
        }
        if (nearest.model().play(args[1], 1.0, null, -1, -1, 10)) {
            send(sender, nearest.type().id() + " 에게 '" + args[1] + "' 재생");
        } else {
            send(sender, "&c모델에 '" + args[1] + "' 애니메이션이 없습니다. 있는 것: "
                    + String.join(", ", nearest.model().blueprint.source.animations.keySet()));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(SUBCOMMANDS);
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "spawn" -> plugin.mobs().types().forEach(t -> options.add(t.id()));
                case "cast" -> options.addAll(plugin.skills().skillNames());
                case "list" -> options.addAll(List.of("mobs", "skills", "models"));
                case "anim" -> plugin.models().ids().forEach(id ->
                        options.addAll(plugin.models().get(id).source.animations.keySet()));
                default -> {
                }
            }
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().distinct().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }
}
