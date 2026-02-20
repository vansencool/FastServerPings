package net.vansen.fastserverpings.servers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;

public class PresetServers {
    public static final String[][] SERVERS = {
            {"bee.mc-complex.com", "25565"},
            {"play.sunrealms.net", "25565"},
            {"buzz.vortexnetwork.net", "25565"},
            {"mcl.blossomcraft.org", "25565"},
            {"buzz.akumamc.net", "25565"},
            {"buzz.manacube.com", "25565"},
            {"mcsl.mellowcraft.org", "25565"},
            {"join.wildwoodsmp.com", "25565"},
            {"mc.smilemorecraft.com", "25565"},
            {"buzz.provim.org", "25565"},
            {"buzz.pika.host", "25565"},
            {"buzz.mysticmc.co", "25565"},
            {"play.bashybashy.com", "25565"},
            {"mslc.wildnetwork.net", "25565"},
            {"mc.cobblegalaxy.com", "25565"},
            {"play.cobblemon.gg", "25565"},
            {"server.chunklock.com", "25565"},
            {"msl.mmorealms.gg", "25565"},
            {"lifesteal.net", "25565"},
            {"owl.snailcraftmc.com", "25565"},
            {"skyblock.net", "25565"},
            {"buzz.extremecraft.net", "25565"},
            {"go.minepeak.org", "25565"},
            {"play.minemalia.com", "25565"},
            {"buzzu.jartex.fun", "25565"},
            {"purpleprison.net", "25565"},
            {"mc.gamster.org", "25565"},
            {"buzz.minewind.net", "25565"},
            {"buzz.twerion.net", "25565"},
            {"join.totemmc.net", "25565"},
            {"lobby.havoc.games", "25565"},
            {"play.penguin.gg", "25565"},
            {"mcsl.lemoncloud.net", "25565"},
            {"mc.thealater.com", "25565"},
            {"msl.augustamc.com", "25565"},
            {"msl.simplesurvival.gg", "25565"},
            {"msl.smashmc.co", "25565"},
            {"play.pokefind.co", "25565"},
            {"allthemons.moddedmc.net", "25565"},
            {"msl.flamefrags.com", "25565"},
            {"play.boxpvp.net", "25565"},
            {"play.cobblemonrivals.com", "25565"},
            {"cobble.pokeclash.com", "25565"},
            {"mc.faeriessmp.com", "25565"},
            {"play.earthpol.com", "25565"},
            {"fun.oplegends.com", "25565"},
            {"go.mineberry.org", "25565"},
            {"msl.vaultsmp.com", "25565"},
            {"msl.enchantedmc.net", "25565"},
            {"msl.the-perch.net", "25565"},
            {"play.jackpotmc.com", "25565"},
            {"mc.safesurvival.net", "25565"},
            {"msl.catcraft.net", "25565"},
            {"msl.vulengate.com", "25565"},
            {"msl.tulipsmp.com", "25565"},
            {"msl.twenture.net", "25565"},
            {"minecraftonline.com", "25565"},
            {"mcvanilla.net", "25565"},
            {"play.blockemon.gg", "25565"},
            {"epic.newwindserver.com", "25565"},
            {"play.roanoke.network", "25565"},
            {"mcsl.nebulamc.gg", "25565"},
            {"diplomaticamc.com", "25565"},
            {"play.voidsent.net", "25565"},
            {"play.prismforge.com", "25565"}
    };

    public static void addServers() {
        ServerList list = new ServerList(Minecraft.getInstance());

        list.load();

        for (String[] s : SERVERS) {
            String addr = s[0] + ":" + s[1];

            boolean exists = false;
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).ip.equalsIgnoreCase(addr)) {
                    exists = true;
                    break;
                }
            }

            if (!exists) {
                list.add(new ServerData(s[0], addr, ServerData.Type.OTHER), false);
            }
        }

        list.save();
    }

    public static void removeServers() {
        ServerList list = new ServerList(Minecraft.getInstance());

        list.load();

        for (int i = list.size() - 1; i >= 0; i--) {
            ServerData info = list.get(i);
            String addr = info.ip;

            for (String[] s : SERVERS) {
                if (addr.equalsIgnoreCase(s[0] + ":" + s[1])) {
                    list.remove(info);
                    break;
                }
            }
        }

        list.save();
    }
}
