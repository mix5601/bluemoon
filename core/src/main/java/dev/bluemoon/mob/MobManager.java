package dev.bluemoon.mob;

import dev.bluemoon.BlueMoonPlugin;
import dev.bluemoon.skill.SkillTrigger;
import dev.bluemoon.util.Text;
import dev.bluemoon.util.Yaml;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Loads mob types and keeps track of every living BlueMoon mob. */
public final class MobManager {

    private final BlueMoonPlugin plugin;
    private final Map<String, MobType> types = new LinkedHashMap<>();
    private final Map<UUID, ActiveMob> active = new LinkedHashMap<>();

    public MobManager(BlueMoonPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        types.clear();
        File dir = new File(plugin.getDataFolder(), plugin.mobFolder());
        for (File file : Yaml.files(dir)) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            for (String key : yml.getKeys(false)) {
                ConfigurationSection sec = yml.getConfigurationSection(key);
                if (sec == null) {
                    continue;
                }
                if (types.containsKey(key.toLowerCase(Locale.ROOT))) {
                    plugin.getLogger().warning(file.getName() + ": 같은 이름의 몹이 이미 있습니다: " + key);
                    continue;
                }
                types.put(key.toLowerCase(Locale.ROOT), MobType.load(plugin, key, sec, file.getName()));
            }
        }
    }

    public MobType type(String id) {
        return id == null ? null : types.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<MobType> types() {
        return Collections.unmodifiableCollection(types.values());
    }

    public ActiveMob get(Entity entity) {
        return entity == null ? null : active.get(entity.getUniqueId());
    }

    public Collection<ActiveMob> active() {
        return Collections.unmodifiableCollection(active.values());
    }

    public ActiveMob spawn(MobType type, Location location) {
        return spawn(type, location, null, 0);
    }

    /**
     * Spawns a mob; with an owner it becomes a summon.
     *
     * @param duration summon lifetime in ticks (0 = the type's {@code Summon.Duration})
     */
    public ActiveMob spawn(MobType type, Location location, Entity owner, int duration) {
        Class<? extends Entity> cls = type.entityType().getEntityClass();
        if (cls == null || location.getWorld() == null) {
            return null;
        }
        Entity spawned = location.getWorld().spawn(location, cls, e -> configure(type, (LivingEntity) e));
        LivingEntity living = (LivingEntity) spawned;
        ActiveMob mob = new ActiveMob(plugin, living, type);
        active.put(living.getUniqueId(), mob);
        if (owner != null) {
            mob.setOwner(owner, duration > 0 ? duration : type.summonDuration());
        }
        if (type.modelId() != null) {
            mob.applyModel(type.modelId(), type.modelScale());
        }
        mob.playState("spawn", 20);
        mob.trigger(SkillTrigger.SPAWN, null);
        return mob;
    }

    private void configure(MobType type, LivingEntity e) {
        e.getPersistentDataContainer().set(plugin.keyMobType(), PersistentDataType.STRING, type.id());
        setAttribute(e, Attribute.MAX_HEALTH, type.health());
        e.setHealth(Math.max(1, type.health()));
        setAttribute(e, Attribute.ATTACK_DAMAGE, type.damage());
        setAttribute(e, Attribute.ARMOR, type.armor());
        setAttribute(e, Attribute.MOVEMENT_SPEED, type.movementSpeed());
        setAttribute(e, Attribute.KNOCKBACK_RESISTANCE, type.knockbackResistance());
        setAttribute(e, Attribute.FOLLOW_RANGE, type.followRange());
        setAttribute(e, Attribute.SCALE, type.hitboxScale());
        if (type.display() != null) {
            e.customName(Text.color(type.display()));
            e.setCustomNameVisible(type.showNameplate());
        }
        e.setSilent(type.silent());
        e.setRemoveWhenFarAway(type.despawn());
        e.setPersistent(!type.despawn());
        if (e instanceof Ageable ageable) {
            ageable.setAdult();
        }
        EntityEquipment equipment = e.getEquipment();
        if (equipment != null) {
            equipment.clear();
        }
        e.setCanPickupItems(false);
        if (type.modelId() != null) {
            e.setInvisible(true);
        }
    }

    private static void setAttribute(LivingEntity e, Attribute attribute, Double value) {
        if (value == null) {
            return;
        }
        AttributeInstance inst = e.getAttribute(attribute);
        if (inst != null) {
            inst.setBaseValue(value);
        }
    }

    /** Starts tracking an already existing entity tagged as a BlueMoon mob (chunk load / reload). */
    public void attach(Entity entity) {
        if (!(entity instanceof LivingEntity living) || active.containsKey(entity.getUniqueId()) || entity.isDead()) {
            return;
        }
        String id = living.getPersistentDataContainer().get(plugin.keyMobType(), PersistentDataType.STRING);
        if (id == null) {
            return;
        }
        MobType type = type(id);
        if (type == null) {
            return;
        }
        if (living.getPersistentDataContainer().has(plugin.keyOwner(), PersistentDataType.STRING)) {
            // summons do not survive unloading
            living.remove();
            return;
        }
        ActiveMob mob = new ActiveMob(plugin, living, type);
        active.put(living.getUniqueId(), mob);
        mob.restoreModel();
    }

    public void attachLoaded() {
        for (World world : Bukkit.getWorlds()) {
            for (ItemDisplay d : world.getEntitiesByClass(ItemDisplay.class)) {
                if (isModelPart(d)) {
                    d.remove();
                }
            }
            for (LivingEntity e : world.getLivingEntities()) {
                attach(e);
            }
        }
    }

    /** Living summons owned by an entity. */
    public List<ActiveMob> summonsOf(Entity owner) {
        List<ActiveMob> out = new ArrayList<>();
        if (owner == null) {
            return out;
        }
        UUID id = owner.getUniqueId();
        for (ActiveMob mob : active.values()) {
            if (id.equals(mob.ownerId()) && !mob.isDying() && !mob.isRemoved()) {
                out.add(mob);
            }
        }
        return out;
    }

    /** True for the same entity, an owner and its summon, or two summons of the same owner. */
    public boolean allies(Entity a, Entity b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.equals(b)) {
            return true;
        }
        UUID ownerA = ownerOf(a);
        UUID ownerB = ownerOf(b);
        return (ownerA != null && ownerA.equals(b.getUniqueId()))
                || (ownerB != null && ownerB.equals(a.getUniqueId()))
                || (ownerA != null && ownerA.equals(ownerB));
    }

    private UUID ownerOf(Entity e) {
        ActiveMob mob = active.get(e.getUniqueId());
        return mob == null ? null : mob.ownerId();
    }

    public boolean isModelPart(Entity e) {
        return e.getPersistentDataContainer().has(plugin.keyModelPart(), PersistentDataType.BYTE);
    }

    public void tick() {
        // snapshot: skills run during the tick may spawn new mobs
        List<ActiveMob> finished = new ArrayList<>();
        for (ActiveMob mob : new ArrayList<>(active.values())) {
            boolean alive;
            try {
                alive = mob.tick();
            } catch (RuntimeException ex) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "몹 틱 처리 오류: " + mob.type().id(), ex);
                mob.despawnModel();
                alive = false;
            }
            if (!alive) {
                finished.add(mob);
            }
        }
        for (ActiveMob mob : finished) {
            active.remove(mob.entity().getUniqueId());
            plugin.skills().clearCooldowns(mob.entity().getUniqueId());
        }
    }

    /** Removes every model (entities stay) and forgets all active mobs. */
    public void detachAll() {
        for (ActiveMob mob : active.values()) {
            mob.despawnModel();
        }
        active.clear();
    }

    /** Kills off every BlueMoon mob. */
    public int removeAll() {
        int n = 0;
        for (ActiveMob mob : new ArrayList<>(active.values())) {
            mob.despawnModel();
            mob.entity().remove();
            n++;
        }
        active.clear();
        return n;
    }

    // --- event entry points ----------------------------------------------------

    void damaged(ActiveMob mob, Entity damager) {
        mob.onDamaged(damager);
    }

    void attacked(ActiveMob mob, Entity victim) {
        mob.onAttack(victim);
    }

    void died(ActiveMob mob, Entity killer) {
        mob.onDeath(killer);
    }
}
