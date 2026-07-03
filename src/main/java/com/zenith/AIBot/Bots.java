package com.zenith.AIBot;

import java.util.ArrayList;
import java.util.List;

public class Bots {
    static List<ComandoFakePlayer.EntityPlayerSigma> bots = new ArrayList<>();

    static public ComandoFakePlayer.EntityPlayerSigma PegarBot(String nomeDoBot) {
        return Bots.bots.stream().filter(bot -> bot.getName().equalsIgnoreCase(nomeDoBot)).findFirst().orElse(null);
    }
}
