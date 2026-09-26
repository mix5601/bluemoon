package dev.bluemoon;

import dev.bluemoon.command.BaseCommand;
import dev.bluemoon.mob.ActiveMob;
import dev.bluemoon.mob.MobType;
import dev.bluemoon.skill.Skill;
import dev.bluemoon.skill.SkillMeta;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** BlueMoon: MythicMobs style mobs rendered with Blockbench models. */
public final class BlueMoon extends BlueMoonPlugin {

    @Override
    public String namespace() {
        return "bluemoon";
    }

    @Override
    protected String commandName() {
        return "bluemoon";
    }

    @Override
    protected List<String> exampleResources() {
        return List.of("models/golem.bbmodel", "mobs/example_mobs.yml", "skills/example_skills.yml");
    }

    @Override
    protected void registerCommands(BaseCommand command) {
        command.register("spawn", "<몹> [수량]", "바라보는 위치에 소환", (sender, args) -> {
            if (!(sender instanceof Player player)) {
                command.send(sender, "플레이어만 사용할 수 있습니다.");
                return;
            }
            if (args.length < 2) {
                command.send(sender, "사용법: /bm spawn <몹> [수량]");
                return;
            }
            MobType type = mobs().type(args[1]);
            if (type == null) {
                command.send(sender, "&c몹 '" + args[1] + "' 이 없습니다.");
                return;
            }
            int amount = 1;
            if (args.length > 2) {
                try {
                    amount = Math.max(1, Math.min(50, Integer.parseInt(args[2])));
                } catch (NumberFormatException e) {
                    command.send(sender, "&c수량은 숫자여야 합니다.");
                    return;
                }
            }
            var block = player.getTargetBlockExact(32);
            Location at = block != null ? block.getLocation().add(0.5, 1, 0.5) : player.getLocation();
            at.setYaw(player.getLocation().getYaw() + 180);
            for (int i = 0; i < amount; i++) {
                mobs().spawn(type, at);
            }
            command.send(sender, type.id() + " " + amount + "마리를 소환했습니다.");
        }, (sender, args) -> {
            List<String> ids = new ArrayList<>();
            if (args.length == 2) {
                mobs().types().forEach(t -> ids.add(t.id()));
            }
            return ids;
        });

        command.register("cast", "<스킬>", "내가 시전자가 되어 스킬 실행", (sender, args) -> {
            if (!(sender instanceof Player player)) {
                command.send(sender, "플레이어만 사용할 수 있습니다.");
                return;
            }
            Skill skill = args.length < 2 ? null : skills().skill(args[1]);
            if (skill == null) {
                command.send(sender, "&c스킬이 없습니다. 사용법: /bm cast <스킬>");
                return;
            }
            Entity target = player.getTargetEntity(32);
            boolean ok = skill.execute(SkillMeta.create(this, player, null, target));
            command.send(sender, ok ? skill.name() + " 실행" : "&e조건/쿨타임 때문에 실행되지 않았습니다.");
        }, (sender, args) -> args.length == 2 ? new ArrayList<>(skills().skillNames()) : List.of());

        command.register("anim", "<애니메이션>", "가장 가까운 모델 몹에게 애니메이션 재생", (sender, args) -> {
            if (!(sender instanceof Player player) || args.length < 2) {
                command.send(sender, "사용법: /bm anim <애니메이션> (플레이어 전용)");
                return;
            }
            Location loc = player.getLocation();
            ActiveMob nearest = mobs().active().stream()
                    .filter(m -> m.model() != null && !m.isDying() && m.location().getWorld().equals(loc.getWorld()))
                    .filter(m -> m.location().distanceSquared(loc) < 32 * 32)
                    .min(Comparator.comparingDouble(m -> m.location().distanceSquared(loc)))
                    .orElse(null);
            if (nearest == null) {
                command.send(sender, "&c주변 32블록 안에 모델이 있는 몹이 없습니다.");
            } else if (nearest.model().play(args[1], 1.0, null, -1, -1, 10)) {
                command.send(sender, nearest.type().id() + " 에게 '" + args[1] + "' 재생");
            } else {
                command.send(sender, "&c모델에 '" + args[1] + "' 애니메이션이 없습니다. 있는 것: "
                        + String.join(", ", nearest.model().blueprint.source.animations.keySet()));
            }
        }, (sender, args) -> {
            List<String> names = new ArrayList<>();
            if (args.length == 2) {
                models().ids().forEach(id -> names.addAll(models().get(id).source.animations.keySet()));
            }
            return names;
        });

        command.register("killall", "", "BlueMoon 몹 모두 제거",
                (sender, args) -> command.send(sender, mobs().removeAll() + "마리를 제거했습니다."), null);
    }
}
