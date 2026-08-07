package me.jonycape.dev.flamecore.giveaway;

import lombok.Getter;
import me.jonycape.dev.flamecore.Main;
import me.jonycape.dev.flamecore.config.ConfigKeys;
import me.jonycape.dev.flamecore.database.GiveawayDAO;
import me.jonycape.dev.flamecore.database.ParticipantDAO;
import me.jonycape.dev.flamecore.utils.IdGenerator;
import me.jonycape.dev.flamecore.utils.MessageUtils;
import me.jonycape.dev.flamecore.utils.TimeUtils;
import org.bukkit.Bukkit;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class GiveawayManager {

    @Getter
    private final Main plugin;
    @Getter
    private final GiveawayDAO giveawayDAO;
    @Getter
    private final ParticipantDAO participantDAO;

    public GiveawayManager(Main plugin) {
        this.plugin = plugin;
        this.giveawayDAO = new GiveawayDAO(plugin);
        this.participantDAO = new ParticipantDAO(plugin);
    }

    public void init() {
        giveawayDAO.init();
    }

    public Giveaway create(String prize, String timeRaw) {
        long duration = TimeUtils.parse(timeRaw);
        if (duration < 0) {
            return null;
        }
        Giveaway giveaway = Giveaway.builder()
                .id(IdGenerator.generate())
                .prize(prize)
                .endTime(System.currentTimeMillis() + duration)
                .build();
        giveawayDAO.insertGiveaway(giveaway);

        String message = buildMessage(ConfigKeys.MESSAGE_GIVEAWAY_CREATED,
                "id", giveaway.getId(),
                "prize", prize,
                "time", TimeUtils.format(duration));
        Bukkit.broadcastMessage(MessageUtils.color(message));
        return giveaway;
    }

    public JoinResult join(String giveawayId, String playerNick) {
        Giveaway giveaway = giveawayDAO.findByGameId(giveawayId);
        if (giveaway == null || giveaway.getEndTime() <= System.currentTimeMillis()) {
            return JoinResult.NOT_FOUND;
        }
        if (participantDAO.hasParticipant(giveawayId, playerNick)) {
            return JoinResult.ALREADY_JOINED;
        }
        participantDAO.addParticipant(giveawayId, playerNick);
        return JoinResult.OK;
    }

    public void finish(Giveaway giveaway) {
        List<String> participants = participantDAO.getParticipants(giveaway.getId());
        if (participants.isEmpty()) {
            String message = buildMessage(ConfigKeys.MESSAGE_GIVEAWAY_NO_PARTICIPANTS,
                    "id", giveaway.getId(),
                    "prize", giveaway.getPrize());
            Bukkit.broadcastMessage(MessageUtils.color(message));
        } else {
            String winner = participants
                    .get(ThreadLocalRandom.current().nextInt(participants.size()));
            String message = buildMessage(ConfigKeys.MESSAGE_GIVEAWAY_WINNER,
                    "winner", winner,
                    "id", giveaway.getId(),
                    "prize", giveaway.getPrize());
            Bukkit.broadcastMessage(MessageUtils.color(message));
        }
        giveawayDAO.deleteGiveaway(giveaway.getId());
    }

    public List<Giveaway> getActiveGiveaways() {
        return giveawayDAO.loadAll();
    }

    private String buildMessage(String key, String... pairs) {
        String body = MessageUtils.replace(Main.getCfg().getMultiLine(key), pairs);
        String prefix = Main.getCfg().getString(ConfigKeys.MESSAGE_PREFIX, "");
        return prefix + body;
    }

    public enum JoinResult {
        OK,
        NOT_FOUND,
        ALREADY_JOINED
    }
}