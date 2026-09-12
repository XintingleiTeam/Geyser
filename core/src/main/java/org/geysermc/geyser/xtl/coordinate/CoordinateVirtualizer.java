/*
 * Copyright (c) 2026 Xintinglei
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.geysermc.geyser.xtl.coordinate;

import org.cloudburstmc.math.vector.Vector3d;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;

/**
 * Per-session translation between the real Java world and the bounded coordinate window shown
 * to a Bedrock client.
 *
 * <p>Only protocol boundaries may apply this translation. Geyser's world, chunk and entity
 * caches continue to use real Java coordinates. Keeping that separation is essential: a change
 * of origin is a Bedrock presentation refresh, never a Java-world teleport.</p>
 */
public final class CoordinateVirtualizer {
    public static final int DEFAULT_PAGE_SIZE = 50_000;
    public static final int DEFAULT_REBASE_THRESHOLD = 40_000;
    public static final int DEFAULT_HARD_LIMIT = 65_536;
    public static final int DEFAULT_LONG_DISTANCE_TELEPORT_THRESHOLD = 10_000;

    private final int pageSize;
    private final int rebaseThreshold;
    private final int hardLimit;
    private final int longDistanceTeleportThreshold;

    private long originX;
    private long originZ;

    public CoordinateVirtualizer() {
        this(DEFAULT_PAGE_SIZE, DEFAULT_REBASE_THRESHOLD, DEFAULT_HARD_LIMIT, DEFAULT_LONG_DISTANCE_TELEPORT_THRESHOLD);
    }

    public CoordinateVirtualizer(int pageSize, int rebaseThreshold, int hardLimit, int longDistanceTeleportThreshold) {
        if (pageSize <= 0 || pageSize % 16 != 0) {
            throw new IllegalArgumentException("pageSize must be positive and chunk-aligned");
        }
        if (rebaseThreshold <= 0 || rebaseThreshold >= hardLimit) {
            throw new IllegalArgumentException("rebaseThreshold must be inside the hard limit");
        }
        if (hardLimit <= rebaseThreshold || longDistanceTeleportThreshold <= 0) {
            throw new IllegalArgumentException("invalid coordinate virtualizer limits");
        }
        this.pageSize = pageSize;
        this.rebaseThreshold = rebaseThreshold;
        this.hardLimit = hardLimit;
        this.longDistanceTeleportThreshold = longDistanceTeleportThreshold;
    }

    public Vector3f toBedrock(Vector3f javaPosition) {
        return Vector3f.from(javaPosition.getX() - originX, javaPosition.getY(), javaPosition.getZ() - originZ);
    }

    public Vector3f toBedrock(Vector3d javaPosition) {
        return Vector3f.from((float) (javaPosition.getX() - originX), (float) javaPosition.getY(), (float) (javaPosition.getZ() - originZ));
    }

    public Vector3d toJava(Vector3f bedrockPosition) {
        return Vector3d.from(bedrockPosition.getX() + originX, bedrockPosition.getY(), bedrockPosition.getZ() + originZ);
    }

    public Vector3i toBedrockChunk(int javaChunkX, int javaChunkZ) {
        return Vector3i.from(javaChunkX - originChunkX(), 0, javaChunkZ - originChunkZ());
    }

    public int toBedrockBlockX(int javaX) {
        return Math.toIntExact(javaX - originX);
    }

    public int toBedrockBlockZ(int javaZ) {
        return Math.toIntExact(javaZ - originZ);
    }

    public long originX() {
        return originX;
    }

    public long originZ() {
        return originZ;
    }

    public int originChunkX() {
        return Math.toIntExact(originX / 16);
    }

    public int originChunkZ() {
        return Math.toIntExact(originZ / 16);
    }

    public boolean isWithinHardLimit(Vector3d javaPosition) {
        return Math.abs(javaPosition.getX() - originX) < hardLimit && Math.abs(javaPosition.getZ() - originZ) < hardLimit;
    }

    /**
     * Computes a rebase without mutating the current origin. The caller must complete the
     * Bedrock world refresh before applying the result.
     */
    public RebasePlan planRebase(Vector3d previousJavaPosition, Vector3d nextJavaPosition) {
        double deltaX = Math.abs(nextJavaPosition.getX() - previousJavaPosition.getX());
        double deltaZ = Math.abs(nextJavaPosition.getZ() - previousJavaPosition.getZ());
        if (deltaX >= longDistanceTeleportThreshold || deltaZ >= longDistanceTeleportThreshold) {
            return new RebasePlan(originFor(nextJavaPosition.getX()), originFor(nextJavaPosition.getZ()), RebaseReason.LONG_DISTANCE_TELEPORT);
        }

        long targetOriginX = originX;
        long targetOriginZ = originZ;
        if (Math.abs(nextJavaPosition.getX() - originX) >= rebaseThreshold) {
            targetOriginX += nextJavaPosition.getX() >= originX ? pageSize : -pageSize;
        }
        if (Math.abs(nextJavaPosition.getZ() - originZ) >= rebaseThreshold) {
            targetOriginZ += nextJavaPosition.getZ() >= originZ ? pageSize : -pageSize;
        }

        if (targetOriginX == originX && targetOriginZ == originZ) {
            return null;
        }
        return new RebasePlan(targetOriginX, targetOriginZ, RebaseReason.NORMAL_BOUNDARY);
    }

    public RebasePlan planInitialOrigin(Vector3d javaPosition, RebaseReason reason) {
        return new RebasePlan(originFor(javaPosition.getX()), originFor(javaPosition.getZ()), reason);
    }

    public void apply(RebasePlan plan) {
        this.originX = plan.originX();
        this.originZ = plan.originZ();
    }

    private long originFor(double coordinate) {
        if (!Double.isFinite(coordinate)) {
            throw new IllegalArgumentException("coordinate must be finite");
        }
        // A floor-only page would map Java X=-1 to Bedrock X=49,999. Center the
        // selected page instead, so a new session never immediately crosses the
        // rebase threshold merely because it started just below a page boundary.
        return Math.multiplyExact((long) Math.floor((coordinate + pageSize / 2d) / pageSize), pageSize);
    }

    public enum RebaseReason {
        NORMAL_BOUNDARY,
        LONG_DISTANCE_TELEPORT,
        DEATH_RESPAWN,
        DIMENSION_CHANGE,
        SERVER_SWITCH,
        EMERGENCY_SAFETY
    }

    public record RebasePlan(long originX, long originZ, RebaseReason reason) {
    }
}
