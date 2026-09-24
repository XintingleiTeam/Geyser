/*
 * Copyright (c) 2026 Xintinglei
 */

package org.geysermc.geyser.xtl.coordinate;

import org.cloudburstmc.math.vector.Vector3d;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CoordinateVirtualizerTest {

    @Test
    void defaultSpawnPacketPreservesProtocolSentinel() {
        CoordinateVirtualizer virtualizer = new CoordinateVirtualizer();
        virtualizer.apply(virtualizer.planInitialOrigin(Vector3d.from(50_000, 64, 50_000), CoordinateVirtualizer.RebaseReason.DIMENSION_CHANGE));
        var session = org.mockito.Mockito.mock(org.geysermc.geyser.session.GeyserSession.class);
        org.mockito.Mockito.when(session.getCoordinateVirtualizer()).thenReturn(virtualizer);
        var packet = new org.cloudburstmc.protocol.bedrock.packet.SetSpawnPositionPacket();
        packet.setBlockPosition(Vector3i.from(32, 64, 32));
        Vector3i sentinel = packet.getSpawnPosition();
        Assertions.assertTrue(BedrockCoordinatePacketTranslator.translate(session, packet));
        Assertions.assertEquals(sentinel, packet.getSpawnPosition());
        Assertions.assertEquals(Vector3i.from(-49_968, 64, -49_968), packet.getBlockPosition());
    }

    @Test
    void unsetSpawnSurvivesPositiveAndNegativeRebases() {
        Vector3i unset = Vector3i.from(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
        for (int x : new int[]{-29_000_000, -50_000, 0, 50_000, 29_000_000}) {
            CoordinateVirtualizer virtualizer = new CoordinateVirtualizer();
            virtualizer.apply(virtualizer.planInitialOrigin(Vector3d.from(x, 64, -x), CoordinateVirtualizer.RebaseReason.DIMENSION_CHANGE));
            Assertions.assertEquals(unset, virtualizer.toBedrockSpawn(unset));
            Assertions.assertNull(virtualizer.toBedrockSpawn(null));
            Vector3i real = Vector3i.from(x + 32, 64, -x + 32);
            Assertions.assertEquals(real, virtualizer.toJava(virtualizer.toBedrockSpawn(real)));
        }
    }

    @Test
    void normalBoundaryMovesOnePageAndPreservesRoundTrip() {
        CoordinateVirtualizer virtualizer = new CoordinateVirtualizer();
        Vector3d destination = Vector3d.from(40_000, 64, -40_000);

        CoordinateVirtualizer.RebasePlan plan = virtualizer.planRebase(Vector3d.from(39_999, 64, -39_999), destination);
        Assertions.assertNotNull(plan);
        Assertions.assertEquals(50_000, plan.originX());
        Assertions.assertEquals(-50_000, plan.originZ());
        Assertions.assertEquals(CoordinateVirtualizer.RebaseReason.NORMAL_BOUNDARY, plan.reason());

        virtualizer.apply(plan);
        Vector3f bedrock = virtualizer.toBedrock(destination);
        Assertions.assertEquals(-10_000, bedrock.getX());
        Assertions.assertEquals(10_000, bedrock.getZ());
        Assertions.assertEquals(destination, virtualizer.toJava(bedrock));
    }

    @Test
    void longDistanceTeleportJumpsDirectlyToTargetPage() {
        CoordinateVirtualizer virtualizer = new CoordinateVirtualizer();
        Vector3d destination = Vector3d.from(2_023_417, 120, -2_023_417);

        CoordinateVirtualizer.RebasePlan plan = virtualizer.planRebase(Vector3d.from(20_000, 120, -20_000), destination);
        Assertions.assertNotNull(plan);
        Assertions.assertEquals(2_000_000, plan.originX());
        Assertions.assertEquals(-2_000_000, plan.originZ());
        Assertions.assertEquals(CoordinateVirtualizer.RebaseReason.LONG_DISTANCE_TELEPORT, plan.reason());

        virtualizer.apply(plan);
        Vector3f bedrock = virtualizer.toBedrock(destination);
        Assertions.assertEquals(23_417, bedrock.getX());
        Assertions.assertEquals(-23_417, bedrock.getZ());
        Assertions.assertTrue(virtualizer.isWithinHardLimit(destination));
    }

    @Test
    void negativeCoordinatesUseCenteredPagesAndChunkOffsets() {
        CoordinateVirtualizer virtualizer = new CoordinateVirtualizer();
        virtualizer.apply(virtualizer.planInitialOrigin(Vector3d.from(-1, 64, -50_001), CoordinateVirtualizer.RebaseReason.DIMENSION_CHANGE));

        Assertions.assertEquals(0, virtualizer.originX());
        Assertions.assertEquals(-50_000, virtualizer.originZ());
        Assertions.assertEquals(Vector3i.from(0, 0, 3_125), virtualizer.toBedrockChunk(0, 0));
        Assertions.assertEquals(Vector3f.from(-1, 64, -1), virtualizer.toBedrock(Vector3d.from(-1, 64, -50_001)));
    }

    @Test
    void unsafeCoordinatesAreDetectedBeforePacketsAreSent() {
        CoordinateVirtualizer virtualizer = new CoordinateVirtualizer();
        Assertions.assertTrue(virtualizer.isWithinHardLimit(Vector3d.from(65_535, 64, -65_535)));
        Assertions.assertFalse(virtualizer.isWithinHardLimit(Vector3d.from(65_536, 64, 0)));
        Assertions.assertFalse(virtualizer.isWithinHardLimit(Vector3d.from(0, 64, -65_536)));
    }

    @Test
    void forgottenChunkUsesTheWindowThatOriginallySentIt() {
        CoordinateVirtualizer virtualizer = new CoordinateVirtualizer();
        Vector3i firstWindowChunk = virtualizer.rememberSentChunk(125_000, -125_000);
        virtualizer.apply(virtualizer.planInitialOrigin(Vector3d.from(2_000_000, 64, 2_000_000), CoordinateVirtualizer.RebaseReason.LONG_DISTANCE_TELEPORT));

        Assertions.assertEquals(Vector3i.from(125_000, 0, -125_000), firstWindowChunk);
        Assertions.assertEquals(firstWindowChunk, virtualizer.removeSentChunk(125_000, -125_000));
        Assertions.assertEquals(Vector3i.from(0, 0, -250_000), virtualizer.removeSentChunk(125_000, -125_000));
    }

    @Test
    void hidesEntitiesOutsideTheSafeClientWindow() {
        CoordinateVirtualizer virtualizer = new CoordinateVirtualizer();
        virtualizer.apply(virtualizer.planInitialOrigin(Vector3d.from(200_000, 80, 0), CoordinateVirtualizer.RebaseReason.SERVER_SWITCH));

        Assertions.assertTrue(virtualizer.isWithinEntityWindow(Vector3f.from(249_999, 80, 0)));
        Assertions.assertFalse(virtualizer.isWithinEntityWindow(Vector3f.from(250_000, 80, 0)));

        virtualizer.markEntityVisible(47L);
        Assertions.assertTrue(virtualizer.isEntityVisible(47L));
        Assertions.assertTrue(virtualizer.markEntityHidden(47L));
        Assertions.assertFalse(virtualizer.isEntityVisible(47L));
        Assertions.assertFalse(virtualizer.markEntityHidden(47L));
    }
}
