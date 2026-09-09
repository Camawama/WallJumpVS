package net.camacraft.walljumpunbound.compat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Method;

/**
 * Gravity Unbound (gravityunbound) support, reached through reflection so
 * the mod never links against it. Under a gravity field that mod rotates
 * the player's whole frame: "horizontal" and "up" are the frame's axes, the
 * player's velocity is stored in the frame's own coordinates, and yaw is
 * relative to the frame — so wall probes cast along world X/Z found the
 * FLOOR the player stood beside and let them cling to thin air. Every
 * direction the wall-jump logic uses goes through this frame when one is
 * active; without the mod (or under plain gravity) it is the identity and
 * the original code paths run untouched.
 */
public final class GravityCompat {

    private static final boolean LOADED = ModList.get().isLoaded("gravityunbound");
    private static final Quaternionf IDENTITY = new Quaternionf();

    private static boolean resolved;
    private static boolean broken;
    private static Method isAimDefault;
    private static Method getAimRotation;
    private static Method sustainHeldSurface;

    private GravityCompat() {
    }

    private static boolean resolve() {
        if (resolved) return !broken;
        resolved = true;
        try {
            Class<?> api = Class.forName("net.camacraft.gravityunbound.api.GravityChangerAPI");
            isAimDefault = api.getMethod("isAimDefault", Entity.class);
            getAimRotation = api.getMethod("getAimRotation", Entity.class);
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
        }
        try {
            // optional: older Gravity Unbound builds lack it
            Class<?> api = Class.forName("net.camacraft.gravityunbound.api.GravityChangerAPI");
            sustainHeldSurface = api.getMethod("sustainHeldSurface", Entity.class);
        } catch (ReflectiveOperationException | RuntimeException e) {
            sustainHeldSurface = null;
        }
        return !broken;
    }

    /**
     * While clinging to a wall, keep the planet-walk surface the player
     * jumped from held: Gravity Unbound otherwise lets that hold lapse
     * mid-cling (nothing is under the feet) and drops the frame onto the
     * raw field, which turned the cling's "down" away from the surface
     * and cancelled the snap the moment the player let go.
     */
    public static void sustainHeldSurface(Entity entity) {
        if (!LOADED || !resolve() || sustainHeldSurface == null) return;
        try {
            sustainHeldSurface.invoke(null, entity);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    /** True when the entity's gravity frame is rotated away from vanilla. */
    public static boolean isActive(Entity entity) {
        if (!LOADED || !resolve()) return false;
        try {
            return !((Boolean) isAimDefault.invoke(null, entity));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /**
     * The entity's frame: the rotation mapping WORLD directions to the
     * entity's LOCAL frame (local up is world "up" for the entity). A copy;
     * identity when no frame is active.
     */
    public static Quaternionf frame(Entity entity) {
        if (!LOADED || !resolve()) return new Quaternionf(IDENTITY);
        try {
            Object rotation = getAimRotation.invoke(null, entity);
            return rotation instanceof Quaternionf q ? new Quaternionf(q) : new Quaternionf(IDENTITY);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return new Quaternionf(IDENTITY);
        }
    }

    /** A local-frame vector expressed in world coordinates. */
    public static Vec3 toWorld(Vec3 local, Quaternionf frame) {
        Vector3f v = new Vector3f((float) local.x, (float) local.y, (float) local.z);
        v.rotate(new Quaternionf(frame).conjugate());
        return new Vec3(v.x, v.y, v.z);
    }

    /** A world vector expressed in the local frame. */
    public static Vec3 toLocal(Vec3 world, Quaternionf frame) {
        Vector3f v = new Vector3f((float) world.x, (float) world.y, (float) world.z);
        v.rotate(frame);
        return new Vec3(v.x, v.y, v.z);
    }

    /** The frame's up direction in world coordinates (unit). */
    public static Vec3 up(Quaternionf frame) {
        return toWorld(new Vec3(0.0, 1.0, 0.0), frame).normalize();
    }
}
