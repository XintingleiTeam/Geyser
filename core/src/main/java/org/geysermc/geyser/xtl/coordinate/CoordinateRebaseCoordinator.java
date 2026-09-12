/*
 * Copyright (c) 2026 Xintinglei
 */

package org.geysermc.geyser.xtl.coordinate;

import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.java.level.JavaLevelChunkWithLightTranslator;
import org.geysermc.geyser.util.ChunkUtils;

/** Performs the Bedrock-only refresh required when a session changes coordinate pages. */
public final class CoordinateRebaseCoordinator {
    private CoordinateRebaseCoordinator() {
    }

    public static void rebase(GeyserSession session, CoordinateVirtualizer.RebasePlan plan) {
        CoordinateVirtualizer coordinates = session.getCoordinateVirtualizer();

        // Remove the old client window before emitting its replacement. These packets have
        // already been virtualized, so they deliberately bypass the ordinary outbound boundary.
        for (Vector3i oldChunk : coordinates.drainSentChunks()) {
            ChunkUtils.sendEmptyChunk(session, oldChunk.getX(), oldChunk.getZ(), false, true);
        }

        coordinates.apply(plan);
        session.setLastChunkPosition(null);

        // Re-encode cached Java chunk packets so their embedded block-entity positions receive
        // the new origin as well as their LevelChunk coordinates.
        for (var chunkPacket : coordinates.cachedChunkPackets()) {
            JavaLevelChunkWithLightTranslator.translateCachedChunk(session, chunkPacket);
        }

        // The entity cache intentionally remains real-world state. Reposition every visible
        // entity in the new window rather than relying on Java's tracker to resend spawns.
        for (Entity entity : session.getEntityCache().getEntitiesUnsafe().values()) {
            entity.moveAbsolute(entity.position(), entity.getYaw(), entity.getPitch(), entity.getHeadYaw(), entity.isOnGround(), true);
        }

        Vector3f playerPosition = session.getPlayerEntity().position();
        session.getPlayerEntity().moveAbsolute(playerPosition, session.getPlayerEntity().getYaw(), session.getPlayerEntity().getPitch(), false, true);
        ChunkUtils.updateChunkPosition(session, playerPosition.toInt());
    }
}
