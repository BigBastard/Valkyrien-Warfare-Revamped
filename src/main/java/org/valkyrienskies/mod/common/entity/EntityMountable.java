package org.valkyrienskies.mod.common.entity;

import io.netty.buffer.ByteBuf;
import java.util.Optional;
import javax.annotation.ParametersAreNonnullByDefault;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import org.valkyrienskies.mod.common.coordinates.CoordinateSpaceType;
import org.valkyrienskies.mod.common.physics.management.physo.PhysicsObject;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.TransformType;

/**
 * A simple entity whose only purpose is to allow mounting onto chairs, as well as fixing entities
 * onto ships.
 */
@ParametersAreNonnullByDefault
public class EntityMountable extends Entity implements IEntityAdditionalSpawnData {

    private static final DataParameter<NBTTagCompound> SHARED_NBT = EntityDataManager
        .createKey(EntityMountable.class,
            DataSerializers.COMPOUND_TAG);

    // The position (in global or subspace) of where this entity mounts to.
    private Vec3d mountPos;
    // This field tells us if mountPos is in global or a ship subspace
    private CoordinateSpaceType mountPosSpace;
    // A BlockPos that is a means of getting the parent ship of this entity mountable. Can be any position in the ship, and in the case of chairs is the same as the chair block position.
    private BlockPos referencePos;

    @SuppressWarnings("WeakerAccess")
    public EntityMountable(World worldIn) {
        super(worldIn);
        // default value
        this.mountPos = null;
        this.referencePos = null;
        this.mountPosSpace = null;
        this.width = 0.01f;
        this.height = 0.01f;
        dataManager.register(SHARED_NBT, new NBTTagCompound());
    }

    private EntityMountable(World worldIn, Vec3d chairPos,
        CoordinateSpaceType coordinateSpaceType) {
        this(worldIn);
        this.mountPos = chairPos;
        this.setPosition(chairPos.x, chairPos.y, chairPos.z);
        this.mountPosSpace = coordinateSpaceType;
    }

    public EntityMountable(World worldIn, Vec3d chairPos, CoordinateSpaceType coordinateSpaceType,
        BlockPos shipReferencePos) {
        this(worldIn, chairPos, coordinateSpaceType);
        this.referencePos = shipReferencePos;
    }

    public void setMountValues(Vec3d mountPos, CoordinateSpaceType mountPosSpace,
        BlockPos referencePos) {
        this.mountPos = mountPos;
        this.mountPosSpace = mountPosSpace;
        this.referencePos = referencePos;
        updateSharedNBT();
    }

    private void updateSharedNBT() {
        NBTTagCompound tagCompound = new NBTTagCompound();
        writeEntityToNBT(tagCompound);
        dataManager.set(SHARED_NBT, tagCompound);
    }

    public Vec3d getMountPos() {
        return mountPos;
    }

    @Override
    public void notifyDataManagerChange(DataParameter<?> key) {
        if (key == SHARED_NBT) {
            readEntityFromNBT(dataManager.get(SHARED_NBT));
        }
    }

    @Override
    public void onUpdate() {
        if (this.firstUpdate) {
            updateSharedNBT();
        }
        super.onUpdate();
        // Check that this entity isn't broken.
        if (mountPos == null) {
            throw new IllegalStateException("Mounting position not present!");
        }
        if (mountPosSpace == null) {
            throw new IllegalStateException("Mounting space type not present!");
        }
        if (!getEntityWorld().isRemote && !this.isBeingRidden()) {
            this.setDead();
        }
        // Now update the position of this mounting entity.
        Vec3d entityPos = mountPos;
        if (mountPosSpace == CoordinateSpaceType.SUBSPACE_COORDINATES) {
            if (referencePos == null) {
                throw new IllegalStateException(
                    "Mounting reference position for ship not present!");
            }
            Optional<PhysicsObject> mountedOnto = getMountedShip();
            if (mountedOnto.isPresent()) {
                entityPos = mountedOnto.get()
                    .transformVector(entityPos, TransformType.SUBSPACE_TO_GLOBAL);
            } else {
                new IllegalStateException(
                    "Couldn't access ship with reference coordinates " + referencePos)
                    .printStackTrace();
                return;
            }

        }

        setPosition(entityPos.x, entityPos.y, entityPos.z);
    }

    @Override
    protected void entityInit() {

    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {
        mountPos = new Vec3d(compound.getDouble("vs_mount_pos_x"),
            compound.getDouble("vs_mount_pos_y"), compound.getDouble("vs_mount_pos_z"));
        mountPosSpace = CoordinateSpaceType.values()[compound.getInteger("vs_coord_type")];

        if (compound.getBoolean("vs_ref_pos_present")) {
            referencePos = new BlockPos(compound.getInteger("vs_ref_pos_x"),
                compound.getInteger("vs_ref_pos_y"), compound.getInteger("vs_ref_pos_z"));
        } else {
            referencePos = null;
        }
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {
        // Try to prevent data race
        Vec3d mountPosLocal = mountPos;
        compound.setDouble("vs_mount_pos_x", mountPosLocal.x);
        compound.setDouble("vs_mount_pos_y", mountPosLocal.y);
        compound.setDouble("vs_mount_pos_z", mountPosLocal.z);

        compound.setInteger("vs_coord_type", mountPosSpace.ordinal());

        compound.setBoolean("vs_ref_pos_present", referencePos != null);
        if (referencePos != null) {
            compound.setInteger("vs_ref_pos_x", referencePos.getX());
            compound.setInteger("vs_ref_pos_y", referencePos.getY());
            compound.setInteger("vs_ref_pos_z", referencePos.getZ());
        }
    }

    @Override
    public void writeSpawnData(ByteBuf buffer) {
        PacketBuffer packetBuffer = new PacketBuffer(buffer);
        Vec3d mountPosLocal = mountPos;
        packetBuffer.writeDouble(mountPosLocal.x);
        packetBuffer.writeDouble(mountPosLocal.y);
        packetBuffer.writeDouble(mountPosLocal.z);
        packetBuffer.writeInt(mountPosSpace.ordinal());
        packetBuffer.writeBoolean(referencePos != null);
        if (referencePos != null) {
            packetBuffer.writeBlockPos(referencePos);
        }
    }

    @Override
    public void readSpawnData(ByteBuf additionalData) {
        PacketBuffer packetBuffer = new PacketBuffer(additionalData);
        mountPos = new Vec3d(packetBuffer.readDouble(), packetBuffer.readDouble(),
            packetBuffer.readDouble());
        mountPosSpace = CoordinateSpaceType.values()[packetBuffer.readInt()];
        if (packetBuffer.readBoolean()) {
            referencePos = packetBuffer.readBlockPos();
        } else {
            referencePos = null;
        }
    }

    Optional<BlockPos> getReferencePos() {
        return Optional.ofNullable(referencePos);
    }

    public Optional<PhysicsObject> getMountedShip() {
        if (referencePos != null) {
            return ValkyrienUtils.getPhysoManagingBlock(world, referencePos);
        } else {
            return Optional.empty();
        }
    }

    @Override
    public void updatePassenger(Entity passenger) {
        if (this.isPassenger(passenger)) {
            passenger.setPosition(this.posX, this.posY, this.posZ);
        }
    }
}
