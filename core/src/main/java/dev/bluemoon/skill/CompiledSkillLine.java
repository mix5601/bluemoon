package dev.bluemoon.skill;

import dev.bluemoon.skill.parse.SkillLineParser.ParsedLine;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/** A parsed skill line bound to its mechanic, targeter and conditions. */
public final class CompiledSkillLine {

    private final ParsedLine parsed;
    private final Mechanic mechanic;
    private final Targeter targeter;
    private final Targeter defaultTargeter;
    private final List<Condition.Entry> casterConditions;
    private final List<Condition.Entry> targetConditions;
    private final SkillTrigger trigger;
    private final int timerInterval;
    private boolean warned;

    public CompiledSkillLine(ParsedLine parsed, Mechanic mechanic, Targeter targeter, Targeter defaultTargeter,
                             List<Condition.Entry> conditions, SkillTrigger trigger, int timerInterval) {
        this.parsed = parsed;
        this.mechanic = mechanic;
        this.targeter = targeter;
        this.defaultTargeter = defaultTargeter;
        List<Condition.Entry> caster = new ArrayList<>();
        List<Condition.Entry> target = new ArrayList<>();
        for (Condition.Entry c : conditions) {
            (c.target() ? target : caster).add(c);
        }
        this.casterConditions = List.copyOf(caster);
        this.targetConditions = List.copyOf(target);
        this.trigger = trigger;
        this.timerInterval = timerInterval;
    }

    public boolean isDelay() {
        return parsed.isDelay();
    }

    public int delayTicks() {
        return parsed.delayTicks();
    }

    public SkillTrigger trigger() {
        return trigger;
    }

    public int timerInterval() {
        return timerInterval;
    }

    public String raw() {
        return parsed.raw();
    }

    public void execute(SkillMeta meta) {
        if (mechanic == null || meta.depth > SkillMeta.MAX_DEPTH) {
            return;
        }
        if (parsed.chance() < 1 && ThreadLocalRandom.current().nextDouble() >= parsed.chance()) {
            return;
        }
        for (Condition.Entry c : casterConditions) {
            if (!c.check(meta, meta.caster)) {
                return;
            }
        }
        try {
            Targets targets;
            boolean explicit = targeter != null;
            if (explicit) {
                targets = targeter.resolve(meta);
            } else if (mechanic.usesInheritedTargets() && meta.hasInheritedTargets()) {
                targets = new Targets(meta.inheritedEntities, meta.inheritedLocations);
            } else if (mechanic instanceof TargetAwareMechanic) {
                targets = Targets.NONE;
            } else {
                targets = defaultTargeter.resolve(meta);
            }

            List<Entity> entities = new ArrayList<>(targets.entities().size());
            for (Entity e : targets.entities()) {
                if (e == null || !e.isValid() && e != meta.caster) {
                    continue;
                }
                boolean ok = true;
                for (Condition.Entry c : targetConditions) {
                    if (!c.check(meta, e)) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    entities.add(e);
                }
            }
            List<Location> locations = targets.locations();

            if (mechanic instanceof TargetAwareMechanic aware) {
                if (explicit && entities.isEmpty() && locations.isEmpty()) {
                    return;
                }
                aware.castTargets(meta, new Targets(entities, locations), explicit);
                return;
            }

            switch (mechanic.mode()) {
                case NONE -> mechanic.cast(meta);
                case ENTITY -> entities.forEach(e -> mechanic.castEntity(meta, e));
                case LOCATION -> {
                    entities.forEach(e -> mechanic.castLocation(meta, e.getLocation()));
                    locations.forEach(l -> mechanic.castLocation(meta, l.clone()));
                }
                case BOTH -> {
                    entities.forEach(e -> mechanic.castEntity(meta, e));
                    locations.forEach(l -> mechanic.castLocation(meta, l.clone()));
                }
            }
        } catch (RuntimeException ex) {
            if (!warned) {
                warned = true;
                meta.plugin.getLogger().log(Level.WARNING, "스킬 라인 실행 중 오류: " + parsed.raw(), ex);
            }
        }
    }
}
