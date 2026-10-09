/*
 * WorldEdit, a Minecraft world manipulation toolkit
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldEdit team and contributors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.sk89q.worldedit.command;

import com.boydti.fawe.Fawe;
import com.boydti.fawe.FaweAPI;
import com.boydti.fawe.config.BBC;
import com.boydti.fawe.config.Settings;
import com.boydti.fawe.database.DBHandler;
import com.boydti.fawe.database.RollbackDatabase;
import com.boydti.fawe.logging.rollback.RollbackOptimizedHistory;
import com.boydti.fawe.object.FawePlayer;
import com.boydti.fawe.object.FaweQueue;
import com.boydti.fawe.object.MaskedFaweQueue;
import com.boydti.fawe.object.RegionWrapper;
import com.boydti.fawe.object.RunnableVal;
import com.boydti.fawe.object.changeset.DiskStorageHistory;
import com.boydti.fawe.regions.FaweMaskManager;
import com.boydti.fawe.util.EditSessionBuilder;
import com.boydti.fawe.util.MainUtil;
import com.boydti.fawe.util.MathMan;
import com.boydti.fawe.util.SetQueue;
import com.sk89q.minecraft.util.commands.Command;
import com.sk89q.minecraft.util.commands.CommandContext;
import com.sk89q.minecraft.util.commands.CommandPermissions;
import com.sk89q.worldedit.*;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.util.command.binding.Range;
import com.sk89q.worldedit.util.command.binding.Switch;
import com.sk89q.worldedit.util.command.parametric.Optional;
import com.sk89q.worldedit.world.World;
import java.io.File;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Commands to undo, redo, and clear history.
 */
@Command(aliases = {}, desc = "Commands to undo, redo, and clear history: [More Info](http://wiki.sk89q.com/wiki/WorldEdit/Features#History)")
public class HistoryCommands extends MethodCommands {

    /**
     * Create a new instance.
     *
     * @param worldEdit reference to WorldEdit
     */
    public HistoryCommands(WorldEdit worldEdit) {
        super(worldEdit);
    }


    @Command(
            aliases = {"/frb", "frb", "fawerollback", "/fawerollback", "/rollback"},
            usage = "<user=Empire92> <radius=5> <time=3d4h>",
            desc = "Undo a specific edit. " +
                    " - The time uses s, m, h, d, y.\n" +
                    " - Import from disk: /frb #import",
            min = 1,
            max = 3
    )
    @CommandPermissions("worldedit.history.rollback")
    public void faweRollback(final Player player, LocalSession session, final String user, @Optional("0") @Range(min = 0) int radius, @Optional("0") String time, @Switch('r') boolean restore) throws WorldEditException {
        if (!Settings.IMP.HISTORY.USE_DATABASE) {
            BBC.SETTING_DISABLE.send(player, "history.use-database (Import with /frb #import )");
            return;
        }
        switch (user.charAt(0)) {
            case '#': {
                if (user.equals("#import")) {
                    if (!player.hasPermission("fawe.rollback.import")) {
                        BBC.NO_PERM.send(player, "fawe.rollback.import");
                        return;
                    }
                    File folder = MainUtil.getFile(Fawe.imp().getDirectory(), Settings.IMP.PATHS.HISTORY);
                    if (!folder.exists()) {
                        return;
                    }
                    for (File worldFolder : folder.listFiles()) {
                        if (!worldFolder.isDirectory()) {
                            continue;
                        }
                        String worldName = worldFolder.getName();
                        World world = FaweAPI.getWorld(worldName);
                        if (world != null) {
                            for (File userFolder : worldFolder.listFiles()) {
                                if (!userFolder.isDirectory()) {
                                    continue;
                                }
                                String userUUID = userFolder.getName();
                                try {
                                    UUID uuid = UUID.fromString(userUUID);
                                    for (File historyFile : userFolder.listFiles()) {
                                        String name = historyFile.getName();
                                        if (!name.endsWith(".bd")) {
                                            continue;
                                        }
                                        RollbackOptimizedHistory rollback = new RollbackOptimizedHistory(world, uuid, Integer.parseInt(name.substring(0, name.length() - 3)));
                                        DiskStorageHistory.DiskStorageSummary summary = rollback.summarize(RegionWrapper.GLOBAL(), false);
                                        if (summary != null) {
                                            rollback.setDimensions(new Vector(summary.minX, 0, summary.minZ), new Vector(summary.maxX, 255, summary.maxZ));
                                            rollback.setTime(historyFile.lastModified());
                                            RollbackDatabase db = DBHandler.IMP.getDatabase(world);
                                            db.logEdit(rollback);
                                            player.print(BBC.getPrefix() + "Logging: " + historyFile);
                                        }
                                    }
                                } catch (IllegalArgumentException e) {
                                    e.printStackTrace();
                                    continue;
                                }
                            }
                        }
                    }
                    player.print(BBC.getPrefix() + "Done import!");
                    return;
                }
                String toParse = user.substring(1);
                if (!MathMan.isInteger(toParse)) {
                    BBC.COMMAND_SYNTAX.send(player, "/frb #<index>");
                    return;
                }
                int index = Integer.parseInt(toParse);
                final World world = player.getWorld();
                UUID uuid = player.getUniqueId();
                DiskStorageHistory file = new DiskStorageHistory(world, uuid, index);
                if (file.getBDFile().exists()) {
                    if (restore) file.redo(FawePlayer.wrap(player));
                    else file.undo(FawePlayer.wrap(player));
                    BBC.ROLLBACK_ELEMENT.send(player, Fawe.imp().getWorldName(world) + "/" + user + "-" + index);
                } else {
                    BBC.TOOL_INSPECT_INFO_FOOTER.send(player, 0);
                }
                return;
            }
        }
        UUID other = user.equalsIgnoreCase("*") ? null : Fawe.imp().getUUID(user);
        if (other == null && !user.equalsIgnoreCase("*")) {
            BBC.PLAYER_NOT_FOUND.send(player, user);
            return;
        }
        if (radius == 0) {
            BBC.COMMAND_SYNTAX.send(player, "/frb " + user + " <radius> <time>");
            return;
        }
        long timeDiff = MainUtil.timeToSec(time) * 1000;
        if (timeDiff == 0) {
            BBC.COMMAND_SYNTAX.send(player, "/frb " + user + " " + radius + " <time>");
            return;
        }
        radius = Math.max(radius, 0);
        if (radius > 500) {
            if (!player.hasPermission("fawe.admin")) {
                BBC.WORLDEDIT_CANCEL_REASON_MAX_CHECKS.send(player);
                return;
            }
        }
        final World world = player.getWorld();
        WorldVector origin = player.getPosition();
        Vector bot = origin.subtract(radius, radius, radius);
        bot.mutY(Math.max(0, bot.getY()));
        Vector top = origin.add(radius, radius, radius);
        top.mutY(Math.min(255, top.getY()));
        RollbackDatabase database = DBHandler.IMP.getDatabase(world);
        final AtomicInteger count = new AtomicInteger();
        final FawePlayer fp = FawePlayer.wrap(player);

        Region[] allowedRegions = fp.getCurrentRegions(FaweMaskManager.MaskType.OWNER);
        if (allowedRegions == null) {
            BBC.NO_REGION.send(fp);
            return;
        }
        final boolean restricted = allowedRegions.length != 1 || !allowedRegions[0].isGlobal();
        database.getPotentialEdits(other, System.currentTimeMillis() - timeDiff, bot, top, new RunnableVal<DiskStorageHistory>() {
            @Override
            public void run(DiskStorageHistory edit) {
                if (restricted) {
                    // Undo/redo replays changes straight onto the queue, past the EditSession's region
                    // checks, so the queue itself is masked to the regions this player owns
                    FaweQueue queue = new MaskedFaweQueue(SetQueue.IMP.getNewQueue(edit.getWorld(), true, false), allowedRegions);
                    EditSession session = new EditSessionBuilder(edit.getWorld()).player(fp).queue(queue).autoQueue(false).fastmode(false)
                            .checkMemory(false).changeSet(edit).limitUnlimited().allowedRegions(allowedRegions).build();
                    session.setSize(1);
                    // Only part of the edit may have been undone, so its history is kept for a wider rollback
                    if (restore) session.redo(session);
                    else session.undo(session);
                } else {
                    if (restore) edit.redo(fp, allowedRegions);
                    else edit.undo(fp, allowedRegions);
                }
                BBC.ROLLBACK_ELEMENT.send(player, Fawe.imp().getWorldName(edit.getWorld()) + "/" + user + "-" + edit.getIndex());
                count.incrementAndGet();
            }
            }, new Runnable() {
                @Override
                public void run() {
                    BBC.TOOL_INSPECT_INFO_FOOTER.send(player, count);
                }
                // Forget the edits only once their history files are gone (a full rollback); a restore
                // or a rollback limited to owned regions keeps them so they can be rolled back again
            }, !restore && !restricted, restore);
    }

    @Command(
            aliases = {"/fawerestore", "/frestore"},
            usage = "<user=Empire92|*> <radius=5> <time=3d4h>",
            desc = "Redo a specific edit. " +
                    " - The time uses s, m, h, d, y.\n" +
                    " - Import from disk: /frb #import",
            min = 1,
            max = 3
    )
    @CommandPermissions("worldedit.history.rollback")
    public void restore(final Player player, LocalSession session, final String user, @Optional("0") @Range(min = 0) int radius, @Optional("0") String time) throws WorldEditException {
        faweRollback(player, session, user, radius, time, true);
    }

    @Command(
            aliases = {"/undo", "undo"},
            usage = "[times] [player]",
            desc = "Undoes the last action",
            min = 0,
            max = 2
    )
    @CommandPermissions("worldedit.history.undo")
    public void undo(Player player, LocalSession session, CommandContext context) throws WorldEditException {
        int times = Math.max(1, context.getInteger(0, 1));
        FawePlayer.wrap(player).checkConfirmation(() -> {
            LocalSession target = session;
            UUID owner = player.getUniqueId();
            if (context.argsLength() >= 2) {
                player.checkPermission("worldedit.history.undo.other");
                String name = context.getString(1);
                target = worldEdit.getSessionManager().findByName(name);
                if (target == null) {
                    BBC.COMMAND_HISTORY_OTHER_ERROR.send(player, name);
                    return;
                }
                // Load the other player's history, not the history of the player running the command
                owner = Fawe.imp().getUUID(name);
            }
            EditSession lastUndone = null;
            int count = 0;
            for (int i = 0; i < times; ++i) {
                EditSession undone = target.undo(session.getBlockBag(player), player, owner);
                if (undone == null) break;
                lastUndone = undone;
                count++;
            }
            if (count > 0) {
                BBC.COMMAND_UNDO_SUCCESS.send(player, count == 1 ? "" : " x" + count);
                worldEdit.flushBlockBag(player, lastUndone);
            }
            if (count < times) {
                BBC.COMMAND_UNDO_ERROR.send(player);
            }
        }, getArguments(context), times, 50, context);
    }

    @Command(
            aliases = {"/redo", "redo"},
            usage = "[times] [player]",
            desc = "Redoes the last action (from history)",
            min = 0,
            max = 2
    )
    @CommandPermissions("worldedit.history.redo")
    public void redo(Player player, LocalSession session, CommandContext args) throws WorldEditException {

        int times = Math.max(1, args.getInteger(0, 1));

        LocalSession target = session;
        UUID owner = player.getUniqueId();
        if (args.argsLength() >= 2) {
            player.checkPermission("worldedit.history.redo.other");
            String name = args.getString(1);
            target = worldEdit.getSession(name);
            if (target == null) {
                BBC.COMMAND_HISTORY_OTHER_ERROR.send(player, name);
                return;
            }
            // Load the other player's history, not the history of the player running the command
            owner = Fawe.imp().getUUID(name);
        }
        EditSession lastRedone = null;
        int count = 0;
        for (int i = 0; i < times; ++i) {
            EditSession redone = target.redo(session.getBlockBag(player), player, owner);
            if (redone == null) break;
            lastRedone = redone;
            count++;
        }
        if (count > 0) {
            BBC.COMMAND_REDO_SUCCESS.send(player, count == 1 ? "" : " x" + count);
            worldEdit.flushBlockBag(player, lastRedone);
        }
        if (count < times) {
            BBC.COMMAND_REDO_ERROR.send(player);
        }
    }

    @Command(
            aliases = {"/history", "history"},
            usage = "[player] [world]",
            desc = "List recent edits in a player's history",
            help = "Lists the most recent edits stored in a player's history (your own by default),\n" +
                    "newest first, with how long ago each was made, where, and roughly how many\n" +
                    "blocks it changed. Use it before //undo [times] <player> to pick how far to go.\n" +
                    "Other players' history needs worldedit.history.undo.other. Works from the console.",
            min = 0,
            max = 2
    )
    @CommandPermissions("worldedit.history.list")
    public void history(Player player, CommandContext context) throws WorldEditException {
        UUID owner = player.getUniqueId();
        String ownerName = player.getName();
        if (context.argsLength() >= 1) {
            ownerName = context.getString(0);
            if (!ownerName.equalsIgnoreCase(player.getName())) {
                player.checkPermission("worldedit.history.undo.other");
            }
            owner = Fawe.imp().getUUID(ownerName);
            if (owner == null) {
                BBC.COMMAND_HISTORY_OTHER_ERROR.send(player, ownerName);
                return;
            }
        }
        World world = context.argsLength() >= 2 ? FaweAPI.getWorld(context.getString(1)) : player.getWorld();
        if (world == null) {
            player.printError("Unknown world" + (context.argsLength() >= 2 ? ": " + context.getString(1) : "; name one: //history <player> <world>"));
            return;
        }
        if (!Settings.IMP.HISTORY.USE_DISK) {
            player.printError("//history lists history stored on disk; history.use-disk is off in the FAWE config.");
            return;
        }
        String worldName = Fawe.imp().getWorldName(world);
        File folder = MainUtil.getFile(Fawe.imp().getDirectory(), Settings.IMP.PATHS.HISTORY + File.separator + worldName + File.separator + owner);
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".bd"));
        if (files == null || files.length == 0) {
            player.print(BBC.getPrefix() + "No stored history for " + ownerName + " in " + worldName + ".");
            return;
        }
        java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        player.print(BBC.getPrefix() + "History of " + ownerName + " in " + worldName + " (newest first; //undo N undoes the first N):");
        long now = System.currentTimeMillis();
        int shown = 0;
        for (File file : files) {
            if (shown == 15) {
                player.print(BBC.getPrefix() + "  ... and " + (files.length - 15) + " older");
                break;
            }
            String base = file.getName().substring(0, file.getName().length() - 3);
            if (!MathMan.isInteger(base)) {
                continue;
            }
            int index = Integer.parseInt(base);
            DiskStorageHistory edit = new DiskStorageHistory(world, owner, index);
            DiskStorageHistory.DiskStorageSummary summary = edit.summarize(RegionWrapper.GLOBAL(), false);
            String where = summary == null ? "?" : summary.minX + "," + summary.minZ + " to " + summary.maxX + "," + summary.maxZ;
            int size = summary == null ? 0 : summary.getSize();
            player.print(BBC.getPrefix() + "  " + (++shown) + ". #" + index + "  " + MainUtil.secToTime((now - file.lastModified()) / 1000) + " ago  x/z " + where + "  ~" + size + " block(s)");
        }
    }

    @Command(
            aliases = {"/clearhistory", "clearhistory"},
            usage = "",
            desc = "Clear your history",
            min = 0,
            max = 0
    )
    @CommandPermissions("worldedit.history.clear")
    public void clearHistory(Player player, LocalSession session) throws WorldEditException {
        session.clearHistory();
        BBC.COMMAND_HISTORY_CLEAR.send(player);
    }

    public static Class<?> inject() {
        return HistoryCommands.class;
    }
}