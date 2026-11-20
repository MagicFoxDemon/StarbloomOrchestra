package dev.starbloom.orchestra.music;

import dev.starbloom.orchestra.StarbloomOrchestra;
import dev.starbloom.orchestra.music.orchestra.EnsemblePayloads;
import dev.starbloom.orchestra.music.orchestra.Orchestra;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.jetbrains.annotations.Nullable;

/** 1.20.1 Forge 的网络通道：登记乐器与乐团的全部网络包并提供发送入口。 */
public final class OrchestraNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        StarbloomOrchestra.id("main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private static int nextId;
    private static boolean registered;

    private OrchestraNetwork() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        c2s(MusicPayloads.LiveNotes.class, MusicPayloads.LiveNotes::encode, MusicPayloads.LiveNotes::decode, Instruments::handleLive);
        c2s(MusicPayloads.HeldNotes.class, MusicPayloads.HeldNotes::encode, MusicPayloads.HeldNotes::decode, Instruments::handleHeld);
        c2s(MusicPayloads.Control.class, MusicPayloads.Control::encode, MusicPayloads.Control::decode, Instruments::handleControl);
        c2s(MusicPayloads.RollRequest.class, MusicPayloads.RollRequest::encode, MusicPayloads.RollRequest::decode, Instruments::handleRequest);
        c2s(MusicPayloads.Upload.class, MusicPayloads.Upload::encode, MusicPayloads.Upload::decode, Instruments::handleUpload);
        c2s(EnsemblePayloads.PodiumControl.class, EnsemblePayloads.PodiumControl::encode, EnsemblePayloads.PodiumControl::decode, Orchestra::handleControl);
        c2s(EnsemblePayloads.ConductBeat.class, EnsemblePayloads.ConductBeat::encode, EnsemblePayloads.ConductBeat::decode, Orchestra::handleBeat);

        s2c(MusicPayloads.LiveNotesOut.class, MusicPayloads.LiveNotesOut::encode, MusicPayloads.LiveNotesOut::decode);
        s2c(MusicPayloads.HeldNotesOut.class, MusicPayloads.HeldNotesOut::encode, MusicPayloads.HeldNotesOut::decode);
        s2c(MusicPayloads.Playback.class, MusicPayloads.Playback::encode, MusicPayloads.Playback::decode);
        s2c(MusicPayloads.RollData.class, MusicPayloads.RollData::encode, MusicPayloads.RollData::decode);
        s2c(EnsemblePayloads.EnsembleState.class, EnsemblePayloads.EnsembleState::encode, EnsemblePayloads.EnsembleState::decode);
    }

    private static <T> void c2s(Class<T> type, BiConsumer<T, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, T> decoder,
                                BiConsumer<T, ServerPlayer> handler) {
        CHANNEL.registerMessage(nextId++, type, encoder, decoder, (msg, ctx) -> {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player != null) handler.accept(msg, player);
            });
            context.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    private static <T> void s2c(Class<T> type, BiConsumer<T, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, T> decoder) {
        CHANNEL.registerMessage(nextId++, type, encoder, decoder, ClientPayloadHandlers::dispatch, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendToServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }

    public static void sendToPlayer(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void sendToPlayersNear(ServerLevel level, @Nullable ServerPlayer excluded, double x, double y, double z, double range, Object msg) {
        Supplier<PacketDistributor.TargetPoint> target = () -> new PacketDistributor.TargetPoint(excluded, x, y, z, range, level.dimension());
        CHANNEL.send(PacketDistributor.NEAR.with(target), msg);
    }

    public static void sendToPlayersTrackingChunk(ServerLevel level, ChunkPos pos, Object msg) {
        CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunk(pos.x, pos.z)), msg);
    }
}
