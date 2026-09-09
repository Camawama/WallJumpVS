package net.cama.walljumpvs.logic;

import net.cama.walljumpvs.WallJumpClient;
import net.cama.walljumpvs.compat.GravityCompat;
import net.cama.walljumpvs.compat.VSCompat;
import net.cama.walljumpvs.init.ModConfig;
import net.cama.walljumpvs.init.ModConfig.BlockListMode;
import net.cama.walljumpvs.init.ModEnchantments;
import net.cama.walljumpvs.init.ServerConfig;
import net.cama.walljumpvs.network.message.MessageFallDistance;
import net.cama.walljumpvs.network.message.MessageWallJump;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Environment(EnvType.CLIENT)
public class WallJumpLogic {

    // VS classes are only touched through VSCompat, and only when the mod is present.
    private static final boolean VS_LOADED = FabricLoader.getInstance().isModLoaded("valkyrienskies");

    public static int ticksWallClinged;
    public static int ticksWallSlid;
    public static boolean stopSlid = false;
    public static int wallJumpCount;
    private static int ticksKeyDown;
    private static double clingX, clingZ;
    private static double lastJumpHeight = Double.MAX_VALUE;
    private static Set<Direction> walls = new HashSet<>();
    private static Set<Direction> staleWalls = new HashSet<>();

    // GRAVITY UNBOUND: while the player's gravity frame is rotated, every
    // direction below is expressed in that frame — "horizontal" is the
    // frame's tangent plane, "up" its up axis, and the player's velocity
    // and yaw are frame-local already. Null under plain gravity.
    private static Quaternionf gravityFrame = null;
    private static Vec3 gravityUp = new Vec3(0.0, 1.0, 0.0);
    // the point the cling holds (frame mode pins the tangential position to it)
    private static Vec3 clingAnchor = null;
    // the wall block each probed direction found (frame mode)
    private static final Map<Direction, BlockPos> FRAME_WALLS = new EnumMap<>(Direction.class);

    private static boolean collidesWithBlock(Level level, AABB box) {
        return !level.noCollision(box);
    }

    // The compat can be switched off in the config even when VS is installed.
    private static boolean vsEnabled() {
        return VS_LOADED && ModConfig.enableVSCompat;
    }

    /** The player's height along its frame's up (plain Y under vanilla gravity). */
    private static double height(LocalPlayer pl) {
        return gravityFrame != null ? pl.position().dot(gravityUp) : pl.getY();
    }

    public static void doWallJump(LocalPlayer pl) {
        if (!canWallJump(pl))
            return;

        gravityFrame = GravityCompat.isActive(pl) ? GravityCompat.frame(pl) : null;
        gravityUp = gravityFrame != null ? GravityCompat.up(gravityFrame) : new Vec3(0.0, 1.0, 0.0);

        if (pl.onGround() || pl.getAbilities().flying || !pl.level().getFluidState(pl.blockPosition()).isEmpty() || pl.isHandsBusy()) {
            ticksWallClinged = 0;
            ticksWallSlid = 0;
            stopSlid = false;
            clingX = Double.NaN;
            clingZ = Double.NaN;
            clingAnchor = null;
            lastJumpHeight = Double.MAX_VALUE;
            staleWalls.clear();
            wallJumpCount = 0;
            FRAME_WALLS.clear();
            if (VS_LOADED) {
                VSCompat.clearShipWalls();
                VSCompat.clearClingAnchor();
            }

            return;
        }

        // In a wall-jump sequence — clinging, or airborne between wall jumps —
        // the planet-walk surface the sequence started from stays snapped
        // (see GravityCompat.sustainHeldSurface); the sequence ends on the
        // ground, where the reset above runs.
        if (gravityFrame != null && (ticksWallClinged > 0 || wallJumpCount > 0)) {
            GravityCompat.sustainHeldSurface(pl);
        }

        if (stopSlid) return;

        updateWalls(pl);
        ticksKeyDown = WallJumpClient.KEY_WALL_JUMP.isDown() ? ticksKeyDown + 1 : 0;

        if (ticksWallClinged < 1) {
            if (ticksKeyDown > 0 && ticksKeyDown < 4 && !walls.isEmpty() && canWallCling(pl)) {
                // A rotated ship's wall normal is rarely axis-aligned, so snapping
                // the camera to a cardinal direction looks wrong there.
                if (ModConfig.autoRotation && !(vsEnabled() && VSCompat.isShipWall(getClingDirection()))) {
                    pl.setYRot(getClingDirection().getOpposite().toYRot());
                    pl.yRotO = pl.getYRot();
                }

                ticksWallClinged = 1;
                clingX = pl.getX();
                clingZ = pl.getZ();
                clingAnchor = pl.position();
                if (vsEnabled()) VSCompat.captureClingAnchor(pl, getClingDirection());

                playHitSound(pl, getWallPos(pl));
                spawnWallParticle(pl, getWallPos(pl));
            }

            return;
        }

        if (!WallJumpClient.KEY_WALL_JUMP.isDown() || pl.onGround() || !pl.level().getFluidState(pl.blockPosition()).isEmpty() || walls.isEmpty() || pl.getFoodData().getFoodLevel() < 1) {
            ticksWallClinged = 0;
            if (VS_LOADED) VSCompat.clearClingAnchor();

            if ((pl.input.forwardImpulse != 0 || pl.input.leftImpulse != 0) && !pl.onGround() && !walls.isEmpty()) {
                if (wallJumpCount >= ServerConfig.maxWallJumps) return;
                pl.resetFallDistance();
                FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
                buffer.writeBoolean(true);
                ClientPlayNetworking.send(MessageWallJump.ID, buffer);

                wallJump(pl, (float) ServerConfig.wallJumpHeight);
                staleWalls = new HashSet<>(walls);
            }

            return;
        }

        Vec3 shipVelocity = Vec3.ZERO;
        Vec3 anchor = clingAnchor;
        if (vsEnabled() && ModConfig.stickToMovingShips) {
            Vec3 shipAnchor = VSCompat.getClingWorldPos();
            if (shipAnchor != null) {
                clingX = shipAnchor.x;
                clingZ = shipAnchor.z;
                anchor = shipAnchor;
                shipVelocity = VSCompat.getClingPointVelocity();
            }
        }
        if (gravityFrame != null && anchor != null) {
            // hold the position in the frame's tangent plane; only motion
            // along the frame's up is free (the slide below)
            Vec3 pos = pl.position();
            double along = pos.subtract(anchor).dot(gravityUp);
            Vec3 pinned = anchor.add(gravityUp.scale(along));
            pl.setPos(pinned.x, pinned.y, pinned.z);
        } else {
            pl.setPos(clingX, pl.getY(), clingZ);
        }
        // the player's velocity is frame-local under a gravity frame; the
        // ship's is world-space
        if (gravityFrame != null) shipVelocity = GravityCompat.toLocal(shipVelocity, gravityFrame);

        // Slide states are judged relative to the ship so its motion neither
        // triggers nor cancels them; the ship's motion is added back at the end.
        double motionY = pl.getDeltaMovement().y - shipVelocity.y;
        if (motionY > 0.0) {
            motionY = 0.0;
        } else if (motionY < -0.6) {
            motionY = motionY + 0.2;
            spawnWallParticle(pl, getWallPos(pl));
        } else if (ticksWallClinged++ > ServerConfig.wallSlideDelay) {
            if (ticksWallSlid++ > ServerConfig.stopWallSlideDelay) stopSlid = true;
            motionY = -0.1;
            spawnWallParticle(pl, getWallPos(pl));
        } else {
            motionY = 0.0;
        }

        if (pl.fallDistance > 2) {
            pl.resetFallDistance();
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            buffer.writeFloat((float) (motionY * motionY * 8));
            ClientPlayNetworking.send(MessageFallDistance.ID, buffer);
        }

        pl.setDeltaMovement(0.0, motionY + shipVelocity.y, 0.0);
    }

    private static boolean canWallJump(LocalPlayer pl) {
        if (ServerConfig.useWallJump) return true;
        if (!ServerConfig.enableEnchantments || !ServerConfig.enableWallJump)
            return false;
        ItemStack stack = pl.getItemBySlot(EquipmentSlot.FEET);
        if (!stack.isEmpty()) {
            Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(stack);
            return enchantments.containsKey(ModEnchantments.WALL_JUMP);
        }

        return false;
    }

    private static boolean canWallCling(LocalPlayer pl) {
        if (pl.onClimbable() || pl.getFoodData().getFoodLevel() < 1) return false;
        Vec3 velocity = clingVelocity(pl);
        if (velocity.y > 0.1) return false;
        if (gravityFrame != null) {
            // ground within 0.8 below the feet, down the FRAME (Valkyrien
            // Skies raycasts through ships natively, so this covers ships)
            if (groundBelow(pl, 0.8 * bodyScale(pl))) return false;
        } else {
            AABB below = pl.getBoundingBox().move(0, -0.8, 0);
            if (collidesWithBlock(pl.level(), below)) return false;
            // Deflated so millimeter penetration into the cling wall itself does not
            // read as ground; ships resolve collisions with a little slop.
            if (vsEnabled() && VSCompat.intersectsShipBlock(pl.level(), below.deflate(0.05, 0.0, 0.05))) return false;
        }
        if (!ServerConfig.onFallWallCling && velocity.y < -0.8) return false;
        if (ServerConfig.allowReClinging || height(pl) < lastJumpHeight - 1) return true;
        return !staleWalls.containsAll(walls);
    }

    private static double bodyScale(LocalPlayer pl) {
        return Math.max(0.05, Math.min(1.0, pl.getBbHeight() / 1.8));
    }

    private static boolean groundBelow(LocalPlayer pl, double depth) {
        Vec3 from = pl.position().add(gravityUp.scale(0.05));
        BlockHitResult hit = pl.level().clip(new ClipContext(
                from, from.subtract(gravityUp.scale(0.05 + depth)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, pl));
        return hit.getType() == HitResult.Type.BLOCK;
    }

    /**
     * Velocity used for cling checks: relative to the ship when the candidate
     * wall belongs to one, so cling behaves the same on a moving ship.
     */
    private static Vec3 clingVelocity(LocalPlayer pl) {
        Vec3 velocity = pl.getDeltaMovement();
        if (vsEnabled() && hasShipWall()) {
            Vec3 shipVelocity = VSCompat.getShipPointVelocity(pl);
            if (gravityFrame != null) shipVelocity = GravityCompat.toLocal(shipVelocity, gravityFrame);
            return velocity.subtract(shipVelocity);
        }
        return velocity;
    }

    private static boolean hasShipWall() {
        for (Direction direction : walls) {
            if (VSCompat.isShipWall(direction)) return true;
        }
        return false;
    }

    private static void updateWalls(LocalPlayer pl) {
        if (gravityFrame != null) {
            updateWallsInFrame(pl);
            return;
        }

        Vec3 pos = pl.position();
        AABB box = new AABB(pos.x - 0.001, pos.y, pos.z - 0.001, pos.x + 0.001, pos.y + pl.getEyeHeight(), pos.z + 0.001);

        double dist = (pl.getBbWidth() / 2) + (ticksWallClinged > 0 ? 0.1 : 0.06);
        AABB[] axes = {box.expandTowards(0, 0, dist), box.expandTowards(-dist, 0, 0), box.expandTowards(0, 0, -dist), box.expandTowards(dist, 0, 0)};

        // A rotated ship stops the player at a corner of their bounding box, out
        // of reach of the thin centered probes above, so ships are probed with
        // the whole box instead: it already touches the wall at any rotation.
        boolean vs = vsEnabled();
        AABB[] shipProbes = null;
        if (vs) {
            double reach = ModConfig.shipWallDetectionRange + (ticksWallClinged > 0 ? 0.04 : 0.0);
            AABB bb = pl.getBoundingBox();
            shipProbes = new AABB[]{bb.expandTowards(0, 0, reach), bb.expandTowards(-reach, 0, 0), bb.expandTowards(0, 0, -reach), bb.expandTowards(reach, 0, 0)};
        }

        walls = new HashSet<>();
        FRAME_WALLS.clear();
        if (VS_LOADED) VSCompat.clearShipWalls();

        for (int i = 0; i < 4; i++) {
            Direction direction = Direction.from2DDataValue(i);

            if (collidesWithBlock(pl.level(), axes[i])) {
                if (ServerConfig.blockListMode == BlockListMode.DISABLED || ServerConfig.blockList.isEmpty() || areBlocksAllowed(getBlockId(pl, pl.blockPosition().relative(direction)), getBlockId(pl, pl.blockPosition().above().relative(direction)))) {
                    walls.add(direction);
                    pl.horizontalCollision = true;
                    continue;
                }
            }

            if (vs) {
                BlockPos shipWall = VSCompat.findShipWall(pl, shipProbes[i], direction);
                if (shipWall != null) {
                    if (ServerConfig.blockListMode == BlockListMode.DISABLED || ServerConfig.blockList.isEmpty() || areBlocksAllowed(getBlockId(pl, shipWall), getBlockId(pl, shipWall.above()))) {
                        walls.add(direction);
                        pl.horizontalCollision = true;
                    } else {
                        VSCompat.forgetShipWall(direction);
                    }
                }
            }
        }
    }

    /**
     * Wall detection under a rotated gravity frame. The world-axis box
     * probes cannot express the frame's tangent plane (and, standing on a
     * plated wall, found the very floor under the player as a "wall" beside
     * it — the cling-to-air). Cast short rays from the body's axis along
     * each frame-horizontal direction instead, at three heights along the
     * frame's up; Valkyrien Skies raycasts ships natively, so a ship wall
     * is found the same way and registered for the moving-ship anchor.
     */
    private static void updateWallsInFrame(LocalPlayer pl) {
        walls = new HashSet<>();
        FRAME_WALLS.clear();
        if (VS_LOADED) VSCompat.clearShipWalls();

        double reach = (pl.getBbWidth() / 2) + (ticksWallClinged > 0 ? 0.1 : 0.06) * bodyScale(pl);
        double h = pl.getBbHeight();
        double[] heights = {0.15 * h, 0.5 * h, 0.85 * h};
        Direction upDir = Direction.getNearest(gravityUp.x, gravityUp.y, gravityUp.z);

        for (int i = 0; i < 4; i++) {
            Direction direction = Direction.from2DDataValue(i);
            Vec3 dirWorld = GravityCompat.toWorld(Vec3.atLowerCornerOf(direction.getNormal()), gravityFrame).normalize();

            BlockPos wallPos = null;
            for (double hh : heights) {
                Vec3 from = pl.position().add(gravityUp.scale(hh));
                BlockHitResult hit = pl.level().clip(new ClipContext(
                        from, from.add(dirWorld.scale(reach)),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, pl));
                if (hit.getType() == HitResult.Type.BLOCK) {
                    wallPos = hit.getBlockPos().immutable();
                    break;
                }
            }
            if (wallPos == null) continue;

            if (ServerConfig.blockListMode != BlockListMode.DISABLED && !ServerConfig.blockList.isEmpty()
                    && !areBlocksAllowed(getBlockId(pl, wallPos), getBlockId(pl, wallPos.relative(upDir)))) {
                continue;
            }

            walls.add(direction);
            FRAME_WALLS.put(direction, wallPos);
            pl.horizontalCollision = true;
            if (vsEnabled()) VSCompat.registerShipWallAt(pl.level(), direction, wallPos);
        }
    }

    private static String getBlockId(LocalPlayer pl, BlockPos pos) {
        BlockState state = pl.level().getBlockState(pos);
        return (state.isSolid()) ? BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString() : null;
    }

    private static boolean areBlocksAllowed(String... blocks) {
        for (String block : blocks)
            if ((block != null) && switch (ServerConfig.blockListMode) {
                case BLACKLIST -> !ServerConfig.blockList.contains(block);
                case WHITELIST -> ServerConfig.blockList.contains(block);
                default -> true;
            }) return true;
        return false;
    }

    private static Direction getClingDirection() {
        return walls.isEmpty() ? Direction.UP : walls.iterator().next();
    }

    private static BlockPos getWallPos(LocalPlayer player) {
        Direction direction = getClingDirection();
        if (gravityFrame != null) {
            BlockPos frameWall = FRAME_WALLS.get(direction);
            if (frameWall != null) return frameWall;
        }
        if (VS_LOADED) {
            BlockPos shipWall = VSCompat.getShipWallPos(direction);
            if (shipWall != null) return shipWall;
        }
        BlockPos blockPos = player.getOnPos().relative(direction);
        return player.level().getBlockState(blockPos).isSolid() ? blockPos : blockPos.relative(Direction.UP);
    }

    private static void wallJump(LocalPlayer pl, float up) {
        float strafe = Math.signum(pl.input.leftImpulse) * up * up;
        float forward = Math.signum(pl.input.forwardImpulse) * up * up;

        float f = (float) (1.0F / Math.sqrt(strafe * strafe + up * up + forward * forward));
        strafe = strafe * f;
        forward = forward * f;

        float f1 = (float) (Math.sin(pl.getYRot() * 0.017453292F) * 0.45F);
        float f2 = (float) (Math.cos(pl.getYRot() * 0.017453292F) * 0.45F);

        int jumpBoostLevel = 0;
        MobEffectInstance jumpBoostEffect = pl.getEffect(MobEffects.JUMP);
        if (jumpBoostEffect != null) jumpBoostLevel = jumpBoostEffect.getAmplifier() + 1;

        // (yaw and velocity are frame-local under a gravity frame, so this
        // composes the jump in the right frame as it is)
        Vec3 motion = pl.getDeltaMovement();
        pl.setDeltaMovement(motion.x + (strafe * f2 - forward * f1), up + (jumpBoostLevel * 0.125), motion.z + (forward * f2 + strafe * f1));

        lastJumpHeight = height(pl);
        playBreakSound(pl, getWallPos(pl));
        spawnWallParticle(pl, getWallPos(pl));
        wallJumpCount++;
    }

    private static void playHitSound(Entity entity, BlockPos blockPos) {
        BlockState state = entity.level().getBlockState(blockPos);
        SoundType soundtype = state.getBlock().getSoundType(state);
        entity.playSound(soundtype.getHitSound(), soundtype.getVolume() * 0.25F, soundtype.getPitch());
    }

    private static void playBreakSound(Entity entity, BlockPos blockPos) {
        BlockState state = entity.level().getBlockState(blockPos);
        SoundType soundtype = state.getBlock().getSoundType(state);
        entity.playSound(soundtype.getFallSound(), soundtype.getVolume() * 0.5F, soundtype.getPitch());
    }

    private static void spawnWallParticle(Entity entity, BlockPos blockPos) {
        BlockState state = entity.level().getBlockState(blockPos);
        if (state.getRenderShape() != RenderShape.INVISIBLE) {
            Vec3 pos = entity.position();
            Vec3i motion = getClingDirection().getNormal();
            Vec3 velocity = new Vec3(motion.getX() * -1.0D, -1.0D, motion.getZ() * -1.0D);
            if (gravityFrame != null) velocity = GravityCompat.toWorld(velocity, gravityFrame);

            entity.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.x, pos.y, pos.z,
                    velocity.x, velocity.y, velocity.z);
        }
    }
}
