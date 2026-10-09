package com.boydti.fawe.forge;

import com.boydti.fawe.Fawe;
import com.boydti.fawe.config.Settings;
import com.boydti.fawe.object.FaweCommand;
import com.boydti.fawe.object.FawePlayer;
import java.io.File;
import java.util.Map;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import org.apache.logging.log4j.Logger;

@Mod(modid = "com.boydti.fawe", name = "FastAsyncWorldEdit", version = "3.5.1", acceptableRemoteVersions = "*", dependencies = "before:worldedit")
public class ForgeMain {
    private static com.boydti.fawe.forge.FaweForge IMP;
    private Logger logger;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        this.logger = event.getModLog();
        File directory = new File(event.getModConfigurationDirectory() + File.separator + "FastAsyncWorldEdit");
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
        this.IMP = new FaweForge(this, event.getModLog(), event.getModMetadata(), directory);
        try {
            Class.forName("org.spongepowered.api.Sponge");
            Settings.IMP.QUEUE.PARALLEL_THREADS = 1;
        } catch (Throwable ignore) {}
    }

    @Mod.EventHandler
    public void serverLoad(FMLServerStartingEvent event) {
        for (Map.Entry<String, FaweCommand> entry : IMP.getCommands().entrySet()) {
            event.registerServerCommand(new ForgeCommand(entry.getKey(), entry.getValue()));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player.world.isRemote) {
            return;
        }
        handleQuit((EntityPlayerMP) event.player);
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        for (EntityPlayerMP player : FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().getPlayers()) {
            handleQuit(player);
        }
    }

    public void handleQuit(EntityPlayerMP player) {
        FawePlayer fp = FawePlayer.wrap(player);
        if (fp != null) {
            fp.unregister();
        }
        Fawe.get().unregister(player.getName());
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        refreshPlayer(event.player);
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        refreshPlayer(event.player);
    }

    /**
     * Point an already cached FawePlayer at the player's current entity (see FaweForge.wrap),
     * so its world and visualisation packets follow the player without waiting for their next
     * command.
     */
    private void refreshPlayer(Object player) {
        if (!(player instanceof EntityPlayerMP) || Fawe.get() == null) {
            return;
        }
        if (Fawe.get().getCachedPlayer(((EntityPlayerMP) player).getName()) != null) {
            Fawe.imp().wrap(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerChangedWorld(EntityJoinWorldEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) entity;
        if (player.world.isRemote) {
            return;
        }
    }
}
