package dev.bluemoon.command;

import dev.bluemoon.BlueMoonPlugin;
import dev.bluemoon.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Subcommand dispatcher shared by the BlueMoon plugins. Built-in: reload, list, pack. */
public final class BaseCommand implements TabExecutor {

    @FunctionalInterface
    public interface Handler {
        void run(CommandSender sender, String[] args);
    }

    @FunctionalInterface
    public interface Completer {
        /** @param args all arguments, including the subcommand at index 0 */
        List<String> complete(CommandSender sender, String[] args);
    }

    private record Sub(String usage, String description, Handler handler, Completer completer) {
    }

    private final BlueMoonPlugin plugin;
    private final Map<String, Sub> subs = new LinkedHashMap<>();

    public BaseCommand(BlueMoonPlugin plugin) {
        this.plugin = plugin;
        register("reload", "", "모델/스킬/몹 다시 불러오기 + 리소스팩 생성", (s, a) -> plugin.reloadAll(s), null);
        register("list", "[mobs|skills|models]", "목록", this::list,
                (s, a) -> a.length == 2 ? List.of("mobs", "skills", "models") : List.of());
        register("pack", "", "리소스팩만 다시 생성 후 접속자에게 전송", (s, a) -> {
            plugin.models().load();
            plugin.models().buildPack();
            send(s, "리소스팩을 다시 만들었습니다. sha1 " + plugin.models().packSha1());
            Bukkit.getOnlinePlayers().forEach(plugin::sendPack);
        }, null);
    }

    public void register(String name, String usage, String description, Handler handler, Completer completer) {
        subs.put(name.toLowerCase(Locale.ROOT), new Sub(usage, description, handler, completer));
    }

    public void send(CommandSender to, String message) {
        to.sendMessage(Text.color("&b[" + plugin.getName() + "] &f" + message));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Sub sub = args.length == 0 ? null : subs.get(args[0].toLowerCase(Locale.ROOT));
        if (sub == null) {
            send(sender, "명령어 목록");
            subs.forEach((name, s) -> sender.sendMessage(Text.color("&7/" + label + " " + name
                    + (s.usage().isEmpty() ? "" : " " + s.usage()) + " &f- " + s.description())));
            return true;
        }
        sub.handler().run(sender, args);
        return true;
    }

    private void list(CommandSender sender, String[] args) {
        String what = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "mobs";
        switch (what) {
            case "skills" -> send(sender, "스킬: " + String.join(", ", plugin.skills().skillNames()));
            case "models" -> {
                List<String> names = new ArrayList<>();
                for (String id : plugin.models().ids()) {
                    var bp = plugin.models().get(id);
                    names.add(id + " &7(본 " + bp.bones.size() + ", 애니메이션 "
                            + String.join("/", bp.source.animations.keySet()) + ")&f");
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(subs.keySet());
        } else {
            Sub sub = subs.get(args[0].toLowerCase(Locale.ROOT));
            if (sub != null && sub.completer() != null) {
                options.addAll(sub.completer().complete(sender, args));
            }
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().distinct().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }
}
