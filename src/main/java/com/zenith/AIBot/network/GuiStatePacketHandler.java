package com.zenith.AIBot.network;

import com.zenith.AIBot.CustomGuiState;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class GuiStatePacketHandler implements IMessageHandler<GuiStatePacket, IMessage> {
    @Override
    public IMessage onMessage(GuiStatePacket message, MessageContext ctx) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            if (message.getIntents()) {
                CustomGuiState.x = message.getX();
                CustomGuiState.y = message.getY();
                CustomGuiState.z = message.getZ();
                CustomGuiState.desenhar = true;
            } else {
                CustomGuiState.desenhar = false;
            }
        });
        return null;
    }
}
