/*
 * Copyright (c) 2026 Xintinglei
 */

package org.geysermc.geyser.xtl.coordinate;

import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.AddPlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.BlockEntityDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket;
import org.cloudburstmc.protocol.bedrock.packet.MoveEntityAbsolutePacket;
import org.cloudburstmc.protocol.bedrock.packet.MoveEntityDeltaPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkChunkPublisherUpdatePacket;
import org.cloudburstmc.protocol.bedrock.packet.RespawnPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket;
import org.geysermc.geyser.session.GeyserSession;

/**
 * The single outbound boundary for coordinate virtualization.
 *
 * <p>Geyser's entities and caches deliberately continue to contain Java-world coordinates.
 * Translating packets here prevents a page rebase from leaking into collision, entity tracking,
 * or Java serverbound packets.</p>
 */
public final class BedrockCoordinatePacketTranslator {
    private BedrockCoordinatePacketTranslator() {
    }

    public static void translate(GeyserSession session, BedrockPacket packet) {
        CoordinateVirtualizer coordinates = session.getCoordinateVirtualizer();
        // Chunk unloads may arrive after a rebase. Remember their original client coordinate
        // even while the first window happens to be the zero-origin window.
        if (packet instanceof LevelChunkPacket levelChunkPacket) {
            Vector3i chunk = coordinates.rememberSentChunk(levelChunkPacket.getChunkX(), levelChunkPacket.getChunkZ());
            levelChunkPacket.setChunkX(chunk.getX());
            levelChunkPacket.setChunkZ(chunk.getZ());
            return;
        }
        if (!coordinates.hasOffset()) {
            return;
        }

        if (packet instanceof NetworkChunkPublisherUpdatePacket publisherUpdatePacket) {
            publisherUpdatePacket.setPosition(coordinates.toBedrock(publisherUpdatePacket.getPosition()));
        } else if (packet instanceof UpdateBlockPacket updateBlockPacket) {
            updateBlockPacket.setBlockPosition(coordinates.toBedrock(updateBlockPacket.getBlockPosition()));
        } else if (packet instanceof BlockEntityDataPacket blockEntityPacket) {
            Vector3i position = coordinates.toBedrock(blockEntityPacket.getBlockPosition());
            blockEntityPacket.setBlockPosition(position);
            blockEntityPacket.setData(translateBlockEntityTag(blockEntityPacket.getData(), position));
        } else if (packet instanceof AddEntityPacket addEntityPacket) {
            addEntityPacket.setPosition(coordinates.toBedrock(addEntityPacket.getPosition()));
        } else if (packet instanceof AddPlayerPacket addPlayerPacket) {
            addPlayerPacket.setPosition(coordinates.toBedrock(addPlayerPacket.getPosition()));
        } else if (packet instanceof MoveEntityAbsolutePacket moveEntityPacket) {
            moveEntityPacket.setPosition(coordinates.toBedrock(moveEntityPacket.getPosition()));
        } else if (packet instanceof MoveEntityDeltaPacket moveEntityPacket) {
            if (moveEntityPacket.getFlags().contains(MoveEntityDeltaPacket.Flag.HAS_X)) {
                moveEntityPacket.setX(coordinates.toBedrock(Vector3f.from(moveEntityPacket.getX(), 0, 0)).getX());
            }
            if (moveEntityPacket.getFlags().contains(MoveEntityDeltaPacket.Flag.HAS_Z)) {
                moveEntityPacket.setZ(coordinates.toBedrock(Vector3f.from(0, 0, moveEntityPacket.getZ())).getZ());
            }
        } else if (packet instanceof MovePlayerPacket movePlayerPacket) {
            movePlayerPacket.setPosition(coordinates.toBedrock(movePlayerPacket.getPosition()));
        } else if (packet instanceof RespawnPacket respawnPacket) {
            respawnPacket.setPosition(coordinates.toBedrock(respawnPacket.getPosition()));
        }
    }

    private static NbtMap translateBlockEntityTag(NbtMap tag, Vector3i position) {
        if (tag == null || (!tag.containsKey("x") && !tag.containsKey("z"))) {
            return tag;
        }
        return tag.toBuilder()
            .putInt("x", position.getX())
            .putInt("z", position.getZ())
            .build();
    }
}
