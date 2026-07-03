package com.zenith.AIBot.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public class GuiStatePacket implements IMessage  {
    private double x, y, z;
    private int index;
    private boolean intent;

    public GuiStatePacket() {}

    public GuiStatePacket(double x2, double y2, double z2, int Index2, boolean intents) {
        this.x = x2;
        this.y = y2;
        this.z = z2;
        this.index = Index2;
        this.intent = intents;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readDouble();
        y = buf.readDouble();
        z = buf.readDouble();
        index = buf.readInt();
        intent = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeInt(index);
        buf.writeBoolean(intent);
    }

    public double getX() {return x;}
    public double getY() {return y;}
    public double getZ() {return z;}
    public int getIndex() {return index;}
    public boolean getIntents() {return intent;}
}
