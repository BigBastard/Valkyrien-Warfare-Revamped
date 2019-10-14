/*
 * Adapted from the Wizardry License
 *
 * Copyright (c) 2015-2019 the Valkyrien Warfare team
 *
 * Permission is hereby granted to any persons and/or organizations using this software to copy, modify, merge, publish, and distribute it.
 * Said persons and/or organizations are not allowed to use the software or any derivatives of the work for commercial use or any other means to generate income unless it is to be used as a part of a larger project (IE: "modpacks"), nor are they allowed to claim this software as their own.
 *
 * The persons and/or organizations are also disallowed from sub-licensing and/or trademarking this software without explicit permission from the Valkyrien Warfare team.
 *
 * Any persons and/or organizations using this software must disclose their source code and have it publicly available, include this license, provide sufficient credit to the original authors of the project (IE: The Valkyrien Warfare team), as well as provide a link to the original project.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON INFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

package valkyrienwarfare.addon.world;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.event.FMLStateEvent;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraftforge.fml.common.registry.GameRegistry;
import valkyrienwarfare.addon.world.block.BlockEthereumOre;
import valkyrienwarfare.addon.world.capability.ICapabilityAntiGravity;
import valkyrienwarfare.addon.world.capability.ImplCapabilityAntiGravity;
import valkyrienwarfare.addon.world.capability.StorageAntiGravity;
import valkyrienwarfare.addon.world.proxy.ClientProxyWorld;
import valkyrienwarfare.addon.world.proxy.CommonProxyWorld;
import valkyrienwarfare.addon.world.worldgen.ValkyrienWarfareWorldGen;
import valkyrienwarfare.api.addons.Module;
import valkyrienwarfare.api.addons.VWAddon;
import valkyrienwarfare.mod.common.ValkyrienWarfareMod;

@VWAddon
public class ValkyrienWarfareWorld extends Module {

    @CapabilityInject(ICapabilityAntiGravity.class)
    public static final Capability<ICapabilityAntiGravity> ANTI_GRAVITY_CAPABILITY = null;
    private static final WorldEventsCommon worldEventsCommon = new WorldEventsCommon();
    public static ValkyrienWarfareWorld INSTANCE;
    public Block ethereumOre;
    public Item ethereumCrystal;
    public static boolean OREGEN_ENABLED = true;

    public ValkyrienWarfareWorld() {
        super("VW_World", new CommonProxyWorld(), "valkyrienwarfareworld");
        if (ValkyrienWarfareMod.INSTANCE.isRunningOnClient()) {
            this.setClientProxy(new ClientProxyWorld());
        }
        INSTANCE = this;
    }

    @Override
    protected void preInit(FMLStateEvent event) {
    }

    @Override
    protected void init(FMLStateEvent event) {
        EntityRegistry.registerModEntity(new ResourceLocation(ValkyrienWarfareMod.MOD_ID, "FallingUpBlockEntity"), EntityFallingUpBlock.class, "FallingUpBlockEntity", 75, ValkyrienWarfareMod.INSTANCE, 80, 1, true);
        MinecraftForge.EVENT_BUS.register(worldEventsCommon);

        GameRegistry.registerWorldGenerator(new ValkyrienWarfareWorldGen(), 1);
    }

    @Override
    protected void postInit(FMLStateEvent event) {
    }

    @Override
    public void registerBlocks(RegistryEvent.Register<Block> event) {
        ethereumOre = new BlockEthereumOre(Material.ROCK).setHardness(3f)
                .setTranslationKey("ethereumore")
                .setRegistryName(getModID(), "ethereumore")
                .setCreativeTab(ValkyrienWarfareMod.vwTab);

        event.getRegistry().register(ethereumOre);
    }

    @Override
    public void registerItems(RegistryEvent.Register<Item> event) {
        ethereumCrystal = new ItemEthereumCrystal().setTranslationKey("ethereumcrystal")
                .setRegistryName(getModID(), "ethereumcrystal")
                .setCreativeTab(ValkyrienWarfareMod.vwTab)
                .setMaxStackSize(16);

        event.getRegistry().register(ethereumCrystal);

        registerItemBlock(event, ethereumOre);
    }

    @Override
    protected void registerCapabilities() {
        CapabilityManager.INSTANCE.register(ICapabilityAntiGravity.class, new StorageAntiGravity(), ImplCapabilityAntiGravity.class);
    }
}