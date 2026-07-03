package com.zenith.AIBot;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

@Mod.EventBusSubscriber
public class ouvinteEventos {
    @SubscribeEvent
    public static void playerAdded(EntityJoinWorldEvent event) {
        Entity entidade = event.getEntity();

        if (entidade instanceof EntityPlayerMP && !entidade.world.isRemote) {
            EntityPlayerMP jogador = (EntityPlayerMP) entidade;

            PegarWorldServer.ServerWorld = jogador.getServerWorld();

            TextComponentString mensagemPrincipal = new TextComponentString("§7[Zenith] Escolha uma opção: ");
            TextComponentString botaoSpawn = new TextComponentString("[Spawn Bot]");

            Style botaoStyle = new Style();
            botaoStyle.setColor(TextFormatting.GREEN);
            botaoStyle.setBold(true);

            ClickEvent acaoClique = new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/zenithplayer");
            botaoStyle.setClickEvent(acaoClique);
            botaoSpawn.setStyle(botaoStyle);

            mensagemPrincipal.appendSibling(botaoSpawn);

            jogador.sendMessage(mensagemPrincipal);
        }
    }

    @SubscribeEvent
    public static void playerExited(PlayerEvent.PlayerLoggedOutEvent event) {
        EntityPlayerMP entidade = (EntityPlayerMP) event.player;
        if (!entidade.world.isRemote)
        for (ComandoFakePlayer.EntityPlayerSigma bot : Bots.bots) {
            bot.cerebro.salvarMemoria();
        }
    }
}
