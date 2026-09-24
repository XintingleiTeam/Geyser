/*
 * Copyright (c) 2026 Xintinglei
 */

package org.geysermc.geyser.xtl.coordinate;

import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.AddItemEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.AddPaintingPacket;
import org.cloudburstmc.protocol.bedrock.packet.AddPlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.BlockEntityDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.BlockEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerOpenPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelSoundEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.MoveEntityAbsolutePacket;
import org.cloudburstmc.protocol.bedrock.packet.MoveEntityDeltaPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkChunkPublisherUpdatePacket;
import org.cloudburstmc.protocol.bedrock.packet.OpenSignPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlaySoundPacket;
import org.cloudburstmc.protocol.bedrock.packet.RespawnPacket;
import org.cloudburstmc.protocol.bedrock.packet.RemoveEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.SpawnParticleEffectPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetSpawnPositionPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateSubChunkBlocksPacket;
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

    /**
     * Keeps the zero-origin path byte-for-byte on Geyser's normal outbound route. A level
     * chunk is the sole exception: we retain its Java/client coordinate association so a later
     * page rebase can unload the correct client chunk.
     */
    public static boolean requiresTranslation(GeyserSession session, BedrockPacket packet) {
        return session.getCoordinateVirtualizer().hasOffset() || packet instanceof LevelChunkPacket;
    }

    public static boolean translate(GeyserSession session, BedrockPacket packet) {
        CoordinateVirtualizer coordinates = session.getCoordinateVirtualizer();
        // Chunk unloads may arrive after a rebase. Remember their original client coordinate
        // even while the first window happens to be the zero-origin window.
        if (packet instanceof LevelChunkPacket levelChunkPacket) {
            Vector3i chunk = coordinates.rememberSentChunk(levelChunkPacket.getChunkX(), levelChunkPacket.getChunkZ());
            levelChunkPacket.setChunkX(chunk.getX());
            levelChunkPacket.setChunkZ(chunk.getZ());
            return true;
        }
        if (!coordinates.hasOffset()) {
            return true;
        }

        if (packet instanceof NetworkChunkPublisherUpdatePacket publisherUpdatePacket) {
            publisherUpdatePacket.setPosition(coordinates.toBedrock(publisherUpdatePacket.getPosition()));
        } else if (packet instanceof UpdateBlockPacket updateBlockPacket) {
            updateBlockPacket.setBlockPosition(coordinates.toBedrock(updateBlockPacket.getBlockPosition()));
        } else if (packet instanceof UpdateSubChunkBlocksPacket updateSubChunkBlocksPacket) {
            updateSubChunkBlocksPacket.setPosition(coordinates.toBedrock(updateSubChunkBlocksPacket.getPosition()));
        } else if (packet instanceof BlockEventPacket blockEventPacket) {
            blockEventPacket.setBlockPosition(coordinates.toBedrock(blockEventPacket.getBlockPosition()));
        } else if (packet instanceof ContainerOpenPacket containerOpenPacket) {
            containerOpenPacket.setBlockPosition(coordinates.toBedrock(containerOpenPacket.getBlockPosition()));
        } else if (packet instanceof SetSpawnPositionPacket spawnPositionPacket) {
            spawnPositionPacket.setBlockPosition(coordinates.toBedrockSpawn(spawnPositionPacket.getBlockPosition()));
            spawnPositionPacket.setSpawnPosition(coordinates.toBedrockSpawn(spawnPositionPacket.getSpawnPosition()));
        } else if (packet instanceof OpenSignPacket openSignPacket) {
            openSignPacket.setPosition(coordinates.toBedrock(openSignPacket.getPosition()));
        } else if (packet instanceof BlockEntityDataPacket blockEntityPacket) {
            Vector3i position = coordinates.toBedrock(blockEntityPacket.getBlockPosition());
            blockEntityPacket.setBlockPosition(position);
            blockEntityPacket.setData(translateBlockEntityTag(blockEntityPacket.getData(), position));
        } else if (packet instanceof AddEntityPacket addEntityPacket) {
            if (!prepareEntitySpawn(session, addEntityPacket.getRuntimeEntityId(), addEntityPacket.getPosition())) {
                return false;
            }
            addEntityPacket.setPosition(coordinates.toBedrock(addEntityPacket.getPosition()));
        } else if (packet instanceof AddPlayerPacket addPlayerPacket) {
            if (!prepareEntitySpawn(session, addPlayerPacket.getRuntimeEntityId(), addPlayerPacket.getPosition())) {
                return false;
            }
            addPlayerPacket.setPosition(coordinates.toBedrock(addPlayerPacket.getPosition()));
        } else if (packet instanceof AddItemEntityPacket addItemEntityPacket) {
            if (!prepareEntitySpawn(session, addItemEntityPacket.getRuntimeEntityId(), addItemEntityPacket.getPosition())) {
                return false;
            }
            addItemEntityPacket.setPosition(coordinates.toBedrock(addItemEntityPacket.getPosition()));
        } else if (packet instanceof AddPaintingPacket addPaintingPacket) {
            if (!prepareEntitySpawn(session, addPaintingPacket.getRuntimeEntityId(), addPaintingPacket.getPosition())) {
                return false;
            }
            addPaintingPacket.setPosition(coordinates.toBedrock(addPaintingPacket.getPosition()));
        } else if (packet instanceof MoveEntityAbsolutePacket moveEntityPacket) {
            if (!prepareEntityMove(session, moveEntityPacket.getRuntimeEntityId(), moveEntityPacket.getPosition())) {
                return false;
            }
            moveEntityPacket.setPosition(coordinates.toBedrock(moveEntityPacket.getPosition()));
        } else if (packet instanceof MoveEntityDeltaPacket moveEntityPacket) {
            // MoveEntityDelta's X/Z fields are *deltas*, rather than world positions.
            // An origin offset must never be applied to them: doing so turns an ordinary
            // 0.1-block update into a 50,000-block jump after a page rebase.
            //
            // Unlike an absolute move, this packet cannot respawn an entity that was hidden
            // outside the current window; wait for its next absolute update instead.
            long geyserId = moveEntityPacket.getRuntimeEntityId();
            if (geyserId != session.getPlayerEntity().geyserId()
                && !coordinates.isEntityVisible(geyserId)) {
                return false;
            }
        } else if (packet instanceof MovePlayerPacket movePlayerPacket) {
            movePlayerPacket.setPosition(coordinates.toBedrock(movePlayerPacket.getPosition()));
        } else if (packet instanceof RespawnPacket respawnPacket) {
            respawnPacket.setPosition(coordinates.toBedrock(respawnPacket.getPosition()));
        } else if (packet instanceof LevelEventPacket levelEventPacket) {
            levelEventPacket.setPosition(coordinates.toBedrock(levelEventPacket.getPosition()));
        } else if (packet instanceof LevelSoundEventPacket levelSoundEventPacket) {
            levelSoundEventPacket.setPosition(coordinates.toBedrock(levelSoundEventPacket.getPosition()));
        } else if (packet instanceof PlaySoundPacket playSoundPacket) {
            playSoundPacket.setPosition(coordinates.toBedrock(playSoundPacket.getPosition()));
        } else if (packet instanceof SpawnParticleEffectPacket particleEffectPacket) {
            particleEffectPacket.setPosition(coordinates.toBedrock(particleEffectPacket.getPosition()));
        } else if (packet instanceof RemoveEntityPacket removeEntityPacket) {
            coordinates.markEntityHidden(removeEntityPacket.getUniqueEntityId());
        }
        return true;
    }

    private static boolean prepareEntitySpawn(GeyserSession session, long geyserId, Vector3f javaPosition) {
        CoordinateVirtualizer coordinates = session.getCoordinateVirtualizer();
        if (!coordinates.isWithinEntityWindow(javaPosition)) {
            removeEntityFromWindow(session, geyserId);
            return false;
        }
        coordinates.markEntityVisible(geyserId);
        return true;
    }

    private static boolean prepareEntityMove(GeyserSession session, long geyserId, Vector3f javaPosition) {
        CoordinateVirtualizer coordinates = session.getCoordinateVirtualizer();
        if (!coordinates.isWithinEntityWindow(javaPosition)) {
            removeEntityFromWindow(session, geyserId);
            return false;
        }
        if (geyserId == session.getPlayerEntity().geyserId() || coordinates.isEntityVisible(geyserId)) {
            return true;
        }
        // A previously hidden entity has returned to the safe window. Its cache still owns the
        // real Java position, so regenerate its complete Bedrock spawn before accepting moves.
        var entity = session.getEntityCache().getEntityByGeyserId(geyserId);
        if (entity != null) {
            entity.spawnEntity();
        }
        return false;
    }

    private static void removeEntityFromWindow(GeyserSession session, long geyserId) {
        if (!session.getCoordinateVirtualizer().markEntityHidden(geyserId)) {
            return;
        }
        RemoveEntityPacket removal = new RemoveEntityPacket();
        removal.setUniqueEntityId(geyserId);
        session.sendUpstreamPacketVirtualized(removal);
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
