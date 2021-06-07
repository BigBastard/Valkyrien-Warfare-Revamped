package org.valkyrienskies.mod.common.ships;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.googlecode.cqengine.attribute.Attribute;
import com.googlecode.cqengine.attribute.MultiValueAttribute;
import com.googlecode.cqengine.query.option.QueryOptions;
import lombok.*;
import net.minecraft.util.math.AxisAlignedBB;
import org.joml.Vector3d;
import org.valkyrienskies.mod.common.ships.chunk_claims.VSChunkClaim;
import org.valkyrienskies.mod.common.ships.physics_data.ShipInertiaData;
import org.valkyrienskies.mod.common.ships.physics_data.ShipPhysicsData;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.IPhysObjectWorld;
import org.valkyrienskies.mod.common.util.cqengine.ConcurrentUpdatableIndexedCollection;
import org.valkyrienskies.mod.common.util.datastructures.IBlockPosSet;
import org.valkyrienskies.mod.common.util.datastructures.IBlockPosSetAABB;
import org.valkyrienskies.mod.common.util.datastructures.SmallBlockPosSet;
import org.valkyrienskies.mod.common.util.datastructures.SmallBlockPosSetAABB;
import org.valkyrienskies.mod.common.util.jackson.annotations.PacketIgnore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.googlecode.cqengine.query.QueryFactory.attribute;
import static com.googlecode.cqengine.query.QueryFactory.nullableAttribute;

/**
 * One of these objects will represent a ship. You can obtain a physics object for that ship (if one
 * is available), by calling {@link IPhysObjectWorld#getPhysObjectFromUUID(UUID)}.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE, force = true) // For Jackson
public class ShipData {

    /**
     * The {@link QueryableShipData} that manages this
     */
    @Getter(AccessLevel.NONE)
    private final transient ConcurrentUpdatableIndexedCollection<ShipData> owner;

    // region Data Fields

    /**
     * Physics information -- mutable but final. References to this <strong>should be guaranteed to
     * never change</strong> for the duration of a game.
     */
    private final ShipPhysicsData physicsData;

    private final ShipInertiaData inertiaData;

    /**
     * Do not use this for anything client side! Contains all of the non-air block positions on the ship.
     * This is used for generating AABBs and deconstructing the ship.
     */
    @PacketIgnore
    @Nullable
    @JsonSerialize(as = SmallBlockPosSetAABB.class)
    @JsonDeserialize(as = SmallBlockPosSetAABB.class)
    public IBlockPosSetAABB blockPositions;

    /**
     * Do not use this for anything client side! Contains all the positions of force producing blocks on the ship.
     */
    @PacketIgnore
    @Nullable
    @JsonSerialize(as = SmallBlockPosSet.class)
    @JsonDeserialize(as = SmallBlockPosSet.class)
    public IBlockPosSet activeForcePositions;

    @Setter
    private ShipTransform shipTransform;

    @Setter
    private ShipTransform prevTickShipTransform;

    @Setter
    private AxisAlignedBB shipBB;

    /**
     * Whether or not physics are enabled on this physo
     */
    @Setter
    private boolean physicsEnabled;

    /**
     * The chunks claimed by this physo
     */
    private final VSChunkClaim chunkClaim;

    /**
     * This ships UUID
     */
    private final UUID uuid;

    /**
     * The (unique) name of the physo as displayed to players
     */
    private String name;

    /**
     * Extra data stored by this ship, not sent to clients
     */
    @PacketIgnore
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    private final Map<String, Object> serverTags = new HashMap<>();

    /**
     * Extra data stored by this ship, sent to clients.
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    private final Map<String, Object> commonTags = new HashMap<>();

    // endregion

    private ShipData(@NonNull ConcurrentUpdatableIndexedCollection<ShipData> owner,
                    ShipPhysicsData physicsData, @Nonnull ShipInertiaData inertiaData, @NonNull ShipTransform shipTransform, @NonNull ShipTransform prevTickShipTransform, @NonNull AxisAlignedBB shipBB,
                    boolean physicsEnabled, @NonNull VSChunkClaim chunkClaim, @NonNull UUID uuid, @NonNull String name) {
        this.owner = owner;
        this.physicsData = physicsData;
        this.inertiaData = inertiaData;
        this.shipTransform = shipTransform;
        this.prevTickShipTransform = prevTickShipTransform;
        this.shipBB = shipBB;
        this.physicsEnabled = physicsEnabled;
        this.chunkClaim = chunkClaim;
        this.uuid = uuid;
        this.name = name;

        this.blockPositions = new SmallBlockPosSetAABB(chunkClaim.getCenterPos().getXStart(), 0,
                chunkClaim.getCenterPos().getZStart(), 1024, 1024, 1024);
        this.activeForcePositions = new SmallBlockPosSet(chunkClaim.getCenterPos().getXStart(), chunkClaim.getCenterPos().getZStart());
    }

    public static ShipData createData(ConcurrentUpdatableIndexedCollection<ShipData> owner,
        String name, VSChunkClaim chunkClaim, UUID shipID,
        ShipTransform shipTransform,
        AxisAlignedBB aabb) {

        return new ShipData(owner, new ShipPhysicsData(new Vector3d(), new Vector3d()), new ShipInertiaData(), shipTransform, shipTransform, aabb,
            false, chunkClaim, shipID, name);
    }

    // region Setters

    public ShipData setName(String name) {
        this.name = name;
        owner.updateObjectIndices(this, NAME);
        return this;
    }

    // endregion

    // region Attributes

    public static final Attribute<ShipData, String> NAME = nullableAttribute(ShipData::getName);
    public static final Attribute<ShipData, UUID> UUID = attribute(ShipData::getUuid);
    public static final Attribute<ShipData, Long> CHUNKS = new MultiValueAttribute<ShipData, Long>() {
        @Override
        public Set<Long> getValues(ShipData physo, QueryOptions queryOptions) {
            return physo.getChunkClaim().getClaimedChunks();
        }
    };

    // endregion
}

