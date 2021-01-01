package org.valkyrienskies.mixin.entity;

import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.entity.EntityShipMovementData;
import org.valkyrienskies.mod.common.ships.ShipData;
import org.valkyrienskies.mod.common.ships.entity_interaction.IDraggable;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.ships.entity_interaction.EntityShipMountData;
import org.valkyrienskies.mod.common.util.JOML;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.TransformType;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.UUID;

@Mixin(Entity.class)
public abstract class MixinEntity implements IDraggable {

    private final Entity thisAsEntity = Entity.class.cast(this);
    @Shadow
    public float rotationYaw;
    @Shadow
    public float rotationPitch;
    @Shadow
    public float prevRotationYaw;
    @Shadow
    public float prevRotationPitch;
    @Shadow
    public World world;
    @Shadow
    public double posX;
    @Shadow
    public double posY;
    @Shadow
    public double posZ;

    private Vector3d searchVector = null;
    
    private EntityShipMovementData entityShipMovementData = new EntityShipMovementData(null, 0, 0, new Vector3d(), 0);

    private int ticksInAirPocket = 0;

    /**
     * This is easier to have as an overwrite because there's less laggy hackery to be done then :P
     *
     * @author DaPorkchop_
     */
    @Overwrite
    public Vec3d getLook(float partialTicks) {
        // BEGIN VANILLA CODE
        Vec3d original;
        if (partialTicks == 1.0F) {
            original = this.getVectorForRotation(this.rotationPitch, this.rotationYaw);
        } else {
            float f = this.prevRotationPitch
                + (this.rotationPitch - this.prevRotationPitch) * partialTicks;
            float f1 =
                this.prevRotationYaw + (this.rotationYaw - this.prevRotationYaw) * partialTicks;
            original = this.getVectorForRotation(f, f1);
        }
        // END VANILLA CODE

        EntityShipMountData mountData = ValkyrienUtils
            .getMountedShipAndPos(Entity.class.cast(this));
        if (mountData.isMounted()) {
            return mountData.getMountedShip()
                .getShipTransformationManager()
                .getRenderTransform()
                .rotate(original, TransformType.SUBSPACE_TO_GLOBAL);
        } else {
            return original;
        }
    }

    /**
     * This is public in order to fix #437
     *
     * @author asdf
     */
    @Overwrite
    public final Vec3d getVectorForRotation(float pitch, float yaw) {
        // BEGIN VANILLA CODE
        float f = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float f1 = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float f2 = -MathHelper.cos(-pitch * 0.017453292F);
        float f3 = MathHelper.sin(-pitch * 0.017453292F);
        Vec3d vanilla = new Vec3d(f1 * f2, f3, f * f2);
        // END VANILLA CODE

        EntityShipMountData mountData = ValkyrienUtils
            .getMountedShipAndPos(Entity.class.cast(this));
        if (mountData.isMounted()) {
            return mountData.getMountedShip()
                .getShipTransformationManager()
                .getRenderTransform()
                .rotate(vanilla, TransformType.SUBSPACE_TO_GLOBAL);
        } else {
            return vanilla;
        }
    }

    @Shadow
    public abstract void move(MoverType type, double x, double y, double z);

    /**
     * This is easier to have as an overwrite because there's less laggy hackery to be done then :P
     *
     * @author DaPorkchop_
     */
    @Overwrite
    public double getDistanceSq(double x, double y, double z) {
        double d0 = this.posX - x;
        double d1 = this.posY - y;
        double d2 = this.posZ - z;
        double vanilla = d0 * d0 + d1 * d1 + d2 * d2;
        if (vanilla < 64.0D) {
            return vanilla;
        } else {
            Optional<PhysicsObject> physicsObject = ValkyrienUtils
                .getPhysoManagingBlock(world, new BlockPos(x, y, z));
            if (physicsObject.isPresent()) {
                Vector3d posVec = new Vector3d(x, y, z);
                physicsObject.get()
                    .getShipTransformationManager()
                    .getCurrentTickTransform()
                    .transformPosition(posVec, TransformType.SUBSPACE_TO_GLOBAL);
                posVec.x -= this.posX;
                posVec.y -= this.posY;
                posVec.z -= this.posZ;
                if (vanilla > posVec.lengthSquared()) {
                    return posVec.lengthSquared();
                }
            }
        }
        return vanilla;
    }

    /**
     * This is easier to have as an overwrite because there's less laggy hackery to be done then :P
     *
     * @author DaPorkchop_
     */
    @Overwrite
    public double getDistanceSq(BlockPos pos) {
        double vanilla = pos.getDistance((int) posX, (int) posY, (int) posZ);
        if (vanilla < 64.0D) {
            return vanilla;
        } else {
            Optional<PhysicsObject> physicsObject = ValkyrienUtils.getPhysoManagingBlock(world, pos);
            if (physicsObject.isPresent()) {
                Vector3d posVec = new Vector3d(pos.getX() + .5D, pos.getY() + .5D, pos.getZ() + .5D);
                physicsObject.get()
                    .getShipTransformationManager()
                    .getCurrentTickTransform()
                    .transformPosition(posVec, TransformType.SUBSPACE_TO_GLOBAL);
                posVec.x -= this.posX;
                posVec.y -= this.posY;
                posVec.z -= this.posZ;
                if (vanilla > posVec.lengthSquared()) {
                    return posVec.lengthSquared();
                }
            }
        }
        return vanilla;
    }

    @Redirect(method = "createRunningParticles", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;floor(D)I", ordinal = 0))
    private int runningParticlesFirstFloor(double d) {
        final ShipData lastTouchedShip = ValkyrienUtils.getLastShipTouchedByEntity(thisAsEntity);
        if (lastTouchedShip == null) {
            searchVector = null;
            return MathHelper.floor(d);
        } else {
            searchVector = new Vector3d(this.posX, this.posY - 0.20000000298023224D, this.posZ);
            lastTouchedShip.getShipTransform()
                .transformPosition(searchVector, TransformType.GLOBAL_TO_SUBSPACE);
            return MathHelper.floor(searchVector.x);
        }
    }

    @Redirect(method = "createRunningParticles", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;floor(D)I", ordinal = 1))
    private int runningParticlesSecondFloor(double d) {
        if (searchVector == null) {
            return MathHelper.floor(d);
        } else {
            return MathHelper.floor(searchVector.y);
        }
    }

    @Redirect(method = "createRunningParticles", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;floor(D)I", ordinal = 2))
    public int runningParticlesThirdFloor(double d) {
        if (searchVector == null) {
            return MathHelper.floor(d);
        } else {
            return MathHelper.floor(searchVector.z);
        }
    }

    @Shadow
    public float getEyeHeight() {
        return 0.0f;
    }

    @Shadow public abstract World getEntityWorld();

    @Shadow public abstract boolean isInWater();

    @Shadow public boolean inWater;

    @Inject(method = "getPositionEyes(F)Lnet/minecraft/util/math/Vec3d;", at = @At("HEAD"), cancellable = true)
    private void getPositionEyesInject(float partialTicks,
        CallbackInfoReturnable<Vec3d> callbackInfo) {
        EntityShipMountData mountData = ValkyrienUtils
            .getMountedShipAndPos(Entity.class.cast(this));

        if (mountData.isMounted()) {
            Vector3d playerPosition = JOML.convert(mountData.getMountPos());
            mountData.getMountedShip()
                .getShipTransformationManager()
                .getRenderTransform()
                .transformPosition(playerPosition, TransformType.SUBSPACE_TO_GLOBAL);

            Vector3d playerEyes = new Vector3d(0, this.getEyeHeight(), 0);
            // Remove the original position added for the player's eyes
            // RotationMatrices.doRotationOnly(wrapper.wrapping.coordTransform.lToWTransform,
            // playerEyes);
            mountData.getMountedShip()
                .getShipTransformationManager()
                .getCurrentTickTransform()
                .transformDirection(playerEyes, TransformType.SUBSPACE_TO_GLOBAL);
            // Add the new rotate player eyes to the position
            playerPosition.add(playerEyes);
            callbackInfo.setReturnValue(JOML.toMinecraft(playerPosition));
            callbackInfo.cancel(); // return the value, as opposed to the default one
        }
    }

    @Nonnull
    @Override
    public EntityShipMovementData getEntityShipMovementData() {
        return entityShipMovementData;
    }

    @Override
    public void setEntityShipMovementData(EntityShipMovementData entityShipMovementData) {
        this.entityShipMovementData = entityShipMovementData;
    }

    @Override
    public boolean getInAirPocket() {
        return ticksInAirPocket > 0;
    }

    @Override
    public void setTicksAirPocket(int ticksInAirPocket) {
        this.ticksInAirPocket = ticksInAirPocket;
    }

    @Override
    public void decrementTicksAirPocket() {
        this.ticksInAirPocket--;
    }

    @Inject(method = "handleWaterMovement", at = @At("HEAD"), cancellable = true)
    private void handleWaterMovement(CallbackInfoReturnable<Boolean> cir) {
        if (getInAirPocket()) {
            this.inWater = false;
            cir.setReturnValue(false);
        }
    }
}
