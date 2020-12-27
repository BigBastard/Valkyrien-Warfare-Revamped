package org.valkyrienskies.mixin.world.chunk;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.client.render.ITileEntitiesToRenderProvider;
import org.valkyrienskies.mod.common.ships.QueryableShipData;
import org.valkyrienskies.mod.common.ships.ShipData;
import org.valkyrienskies.mod.common.ships.ShipDataMethods;
import org.valkyrienskies.mod.common.ships.chunk_claims.ShipChunkAllocator;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Mixin(value = Chunk.class, priority = 1001)
public abstract class MixinChunk implements ITileEntitiesToRenderProvider {

    @Shadow
    @Final
    public int x;

    @Shadow
    @Final
    public int z;

    @Shadow
    @Final
    public World world;

    @Shadow
    public abstract IBlockState getBlockState(BlockPos pos);

    // We keep track of these so we can quickly update the tile entities that need rendering.
    private List<TileEntity>[] tileEntitiesByExtendedData = new List[16];

    public List<TileEntity> getTileEntitiesToRender(int chunkExtendedDataIndex){
    	List<TileEntity> r = null;
    	synchronized(tileEntitiesByExtendedData) {
    		r = tileEntitiesByExtendedData[chunkExtendedDataIndex];
    	}
    	return r;
    }

    @Inject(method = "addTileEntity(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/tileentity/TileEntity;)V", at = @At("TAIL"))
    private void post_addTileEntity(BlockPos pos, TileEntity tileEntityIn,
        CallbackInfo callbackInfo) {
        int yIndex = pos.getY() >> 4;
        removeTileEntityFromIndex(pos, yIndex);
        tileEntitiesByExtendedData[yIndex].add(tileEntityIn);

        ValkyrienUtils.getPhysoManagingBlock(world, pos).ifPresent(physo -> physo.onSetTileEntity(pos, tileEntityIn));
    }

    @Inject(method = "removeTileEntity(Lnet/minecraft/util/math/BlockPos;)V", at = @At("TAIL"))
    private void post_removeTileEntity(BlockPos pos, CallbackInfo callbackInfo) {
        int yIndex = pos.getY() >> 4;
        removeTileEntityFromIndex(pos, yIndex);
        ValkyrienUtils.getPhysoManagingBlock(world, pos).ifPresent(physo -> physo.onRemoveTileEntity(pos));
    }

    private void removeTileEntityFromIndex(BlockPos pos, int yIndex) {
    	
    	synchronized(tileEntitiesByExtendedData) {

	        if (tileEntitiesByExtendedData[yIndex] == null) {
	            tileEntitiesByExtendedData[yIndex] = new ArrayList<>();
	        }
	        
	        List<TileEntity> current_list = tileEntitiesByExtendedData[yIndex];
	        for(int i = 0; i < current_list.size(); i++) {
	        	TileEntity tile = current_list.get(i);
	        	if(tile == null || tile.getPos().equals(pos) || tile.isInvalid()) {
	        		List<TileEntity> new_list = new ArrayList<>(current_list);
	        		new_list.remove(i);
	        		tileEntitiesByExtendedData[yIndex] = new_list;
	        		/*I am replacing the sub-list altogether, so that if another thread has
	        		a pointer to the original one, I cannot trigger a CME.*/
	        		break;
	        	}
	        }
    	}
        
       
    }

    /**
     * If this chunk is part of a ship, then tell that ship about the IBlockState update.
     *
     * Note that we're assuming that a Chunk cannot deny the setBlockState request. Therefore its safe to assume that
     * the parameter "state" will be the final IBlockState of BlockPos "pos".
     */
    @Inject(method = "setBlockState", at = @At("HEAD"))
    private void pre_setBlockState(BlockPos pos, IBlockState state, CallbackInfoReturnable<IBlockState> cir) {
        if (!world.isRemote) {
            IBlockState oldState = getBlockState(pos);
            QueryableShipData queryableShipData = QueryableShipData.get(world);
            Optional<ShipData> shipDataOptional = queryableShipData.getShipFromChunk(pos.getX() >> 4, pos.getZ() >> 4);
            shipDataOptional.ifPresent(shipData -> ShipDataMethods.onSetBlockState(shipData, pos, oldState, state));
        }
    }

    /**
     * Don't let Minecraft generate terrain near the ships, its a waste of time.
     */
    @Inject(method = "populate(Lnet/minecraft/world/chunk/IChunkProvider;Lnet/minecraft/world/gen/IChunkGenerator;)V", at = @At("HEAD"), cancellable = true)
    private void prePopulateChunk(IChunkProvider provider, IChunkGenerator generator,
        CallbackInfo callbackInfo) {
        if (ShipChunkAllocator.isChunkInShipyard(this.x, this.z)) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "addEntity(Lnet/minecraft/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void preAddEntity(Entity entityIn, CallbackInfo callbackInfo) {
        World world = this.world;

        int i = MathHelper.floor(entityIn.posX / 16.0D);
        int j = MathHelper.floor(entityIn.posZ / 16.0D);

        if (i == this.x && j == this.z) {
            // do nothing, and let vanilla code take over after our injected code is done
            // (now)
        } else {
            Chunk realChunkFor = world.getChunk(i, j);
            if (!realChunkFor.isEmpty() && realChunkFor.loaded) {
                realChunkFor.addEntity(entityIn);
                callbackInfo.cancel(); // don't run the code on this chunk!!!
            }
        }
    }

}
