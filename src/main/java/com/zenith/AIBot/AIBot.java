package com.zenith.AIBot;

 import com.zenith.AIBot.network.GuiStatePacket;
 import com.zenith.AIBot.network.GuiStatePacketHandler;
 import net.minecraftforge.common.MinecraftForge;
 import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
 import net.minecraftforge.fml.common.network.NetworkRegistry;
 import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
 import net.minecraft.entity.player.EntityPlayerMP;
 import net.minecraft.server.MinecraftServer;
 import net.minecraftforge.fml.common.FMLCommonHandler;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
 import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
 import net.minecraftforge.fml.relauncher.Side;

@Mod(modid = AIBot.MODID, name = AIBot.NAME, version = AIBot.VERSION)
public class AIBot {
    public static final String network_Channel = "aibot_gui_channel";
    public static SimpleNetworkWrapper network;
    int packetId = 0;
    public AIBot() {
        MinecraftForge.EVENT_BUS.register(CustomGuiState.class);
    }

    public static final String MODID = "aibot";
    public static final String NAME = "AIBot";
    public static final String VERSION = "6.8";

    public static void sendPacketToAllPlayers(IMessage packet) {
        if (network == null) return;
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return;
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            if (!(player instanceof ComandoFakePlayer.EntityPlayerSigma)) {
                network.sendTo(packet, player);
            }
        }
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        network = NetworkRegistry.INSTANCE.newSimpleChannel(network_Channel);

        network.registerMessage(GuiStatePacketHandler.class, GuiStatePacket.class, packetId, Side.CLIENT);

        System.out.println("[AIBot] Network channel inicializado com sucesso!");
        MinecraftForge.EVENT_BUS.register(CustomGuiState.class);
    }

    @Mod.EventHandler
    public void onServerStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new ComandoFakePlayer());

     System.out.println("[AIBot] Inicializando canal no servidor...");
        if (network == null) {
            try {
                network = NetworkRegistry.INSTANCE.newSimpleChannel(network_Channel);
                System.out.println("[AIBot] Network inicializado no onServerStarting (Servidor)!");

            } catch (Exception e) {
                System.err.println("[AIBot] ERRO ao inicializar network no onServerStarting: " + e.getMessage());
            }
        } else {
            System.out.println("[AIBot] Canal já registrado!");
        }

        if (network != null) {
            network.registerMessage(GuiStatePacketHandler.class, GuiStatePacket.class, 0, Side.CLIENT);

            System.out.println("[AIBot] Handler registrado no cliente (singleplayer)!");
        }
    }
}
