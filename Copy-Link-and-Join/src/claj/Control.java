package claj;

import arc.Core;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Threads;
import java.util.Scanner;
/* loaded from: Copy-Link-and-Join.jar:claj/Control.class */
public class Control {
    public final CommandHandler handler = new CommandHandler("");
    public final Distributor distributor;

    public Control(Distributor distributor) {
        this.distributor = distributor;
        registerCommands();
        Threads.daemon("Application Control", () -> {
            Scanner scanner = new Scanner(System.in);
            while (scanner.hasNext()) {
                try {
                    handleCommand(scanner.nextLine());
                } catch (Throwable th) {
                    try {
                        scanner.close();
                    } catch (Throwable th2) {
                        th.addSuppressed(th2);
                    }
                    throw th;
                }
            }
            scanner.close();
        });
    }

    private void handleCommand(String command) {
        CommandHandler.CommandResponse response = this.handler.handleMessage(command);
        if (response.type == CommandHandler.ResponseType.unknownCommand) {
            String closest = (String) this.handler.getCommandList().map(cmd -> {
                return cmd.text;
            }).min(cmd2 -> {
                return Strings.levenshtein(cmd2, command);
            });
            Log.err("Command not found. Did you mean @?", closest);
        } else if (response.type != CommandHandler.ResponseType.noCommand && response.type != CommandHandler.ResponseType.valid) {
            Object[] objArr = new Object[1];
            objArr[0] = response.type == CommandHandler.ResponseType.fewArguments ? "few" : "many";
            Log.err("Too @ command arguments.", objArr);
        }
    }

    private void registerCommands() {
        this.handler.register("help", "Display the command list.", args -> {
            Log.info("Commands:");
            this.handler.getCommandList().each(command -> {
                Object[] objArr = new Object[3];
                objArr[0] = command.text;
                objArr[1] = command.paramText.isEmpty() ? "" : " &lc&fi" + command.paramText;
                objArr[2] = command.description;
                Log.info("  &b&lb@@&fr - @", objArr);
            });
        });
        this.handler.register("list", "Displays all current rooms.", args2 -> {
            Log.info("Rooms:");
            this.distributor.rooms.forEach(entry -> {
                Log.info("  &b&lbRoom @&fr", ((Room) entry.value).link);
                ((Room) entry.value).redirectors.each(r -> {
                    Log.info("    [H] &b&lbConnection @&fr - @", Integer.valueOf(r.host.getID()), Main.getIP(r.host));
                    if (r.client == null) {
                        return;
                    }
                    Log.info("    [C] &b&lbConnection @&fr - @", Integer.valueOf(r.client.getID()), Main.getIP(r.client));
                });
            });
        });
        this.handler.register("limit", "[amount]", "Sets spam packet limit.", args3 -> {
            if (args3.length == 0) {
                Log.info("Current limit - @ packets per 3 seconds.", Integer.valueOf(Core.settings.getInt("spamLimit", 300)));
                return;
            }
            Core.settings.put("spamLimit", args3[0]);
            this.distributor.spamLimit = Core.settings.getInt("spamLimit", 300);
            Log.info("Packet spam limit set to @ packets per 3 seconds.", Integer.valueOf(this.distributor.spamLimit));
        });
        this.handler.register("ban", "<IP>", "Adds the IP to blacklist.", args4 -> {
            Blacklist.add(args4[0]);
            Log.info("IP @ has been blacklisted.", args4[0]);
        });
        this.handler.register("unban", "<IP>", "Removes the IP from blacklist.", args5 -> {
            Blacklist.remove(args5[0]);
            Log.info("IP @ has been removed from blacklist.", args5[0]);
        });
        this.handler.register("refresh", "Unbans all IPs and refresh GitHub Actions IPs.", args6 -> {
            Blacklist.clear();
            Blacklist.refresh();
        });
        this.handler.register("exit", "Stop hosting distributor and exit the application.", args7 -> {
            this.distributor.rooms.forEach(entry -> {
                ((Room) entry.value).sendMessage("[scarlet]⚠[] The server is shutting down.\nTry to reconnect in a minute.");
            });
            Log.info("Shutting down the application.");
            this.distributor.stop();
        });
        this.handler.register("text", "<TEXT>", "Edit the text for joining rooms", args8 -> {
            Core.settings.put("text", args8[0]);
            Log.info("Edit the text for joining rooms. @", args8[0]);
        });
    }
}
