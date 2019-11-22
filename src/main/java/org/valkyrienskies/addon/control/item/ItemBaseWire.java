/*
 * Adapted from the Wizardry License
 *
 * Copyright (c) 2015-2019 the Valkyrien Skies team
 *
 * Permission is hereby granted to any persons and/or organizations using this software to copy, modify, merge, publish, and distribute it.
 * Said persons and/or organizations are not allowed to use the software or any derivatives of the work for commercial use or any other means to generate income unless it is to be used as a part of a larger project (IE: "modpacks"), nor are they allowed to claim this software as their own.
 *
 * The persons and/or organizations are also disallowed from sub-licensing and/or trademarking this software without explicit permission from the Valkyrien Skies team.
 *
 * Any persons and/or organizations using this software must disclose their source code and have it publicly available, include this license, provide sufficient credit to the original authors of the project (IE: The Valkyrien Skies team), as well as provide a link to the original project.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON INFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

package org.valkyrienskies.addon.control.item;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import org.valkyrienskies.addon.control.ValkyrienSkiesControl;
import org.valkyrienskies.addon.control.capability.ICapabilityLastRelay;
import org.valkyrienskies.addon.control.nodenetwork.EnumWireType;
import org.valkyrienskies.addon.control.nodenetwork.IVSNode;
import org.valkyrienskies.addon.control.nodenetwork.IVSNodeProvider;
import org.valkyrienskies.addon.control.util.BaseItem;
import org.valkyrienskies.mod.common.config.VSConfig;

public class ItemBaseWire extends BaseItem {
    private EnumWireType wireType = EnumWireType.RELAY;

    public ItemBaseWire(EnumWireType wireType) {
		super(wireType.toString(), true);
        this.setMaxDamage(80);
        this.wireType = wireType;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player,
        List<String> itemInformation,
        ITooltipFlag advanced) {
        itemInformation.add(TextFormatting.BLUE + I18n.format("tooltip.vs_control." + this.wireType.toString()));
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World worldIn, BlockPos pos,
        EnumHand hand,
        EnumFacing facing, float hitX, float hitY, float hitZ) {
        IBlockState clickedState = worldIn.getBlockState(pos);
        Block block = clickedState.getBlock();
        TileEntity currentTile = worldIn.getTileEntity(pos);
        ItemStack stack = player.getHeldItem(hand);

        if (currentTile instanceof IVSNodeProvider && !worldIn.isRemote) {
            ICapabilityLastRelay inst = stack.getCapability(ValkyrienSkiesControl.lastRelayCapability, null);
            if (inst != null) {
                if (!inst.hasLastRelay()) {
                    inst.setLastRelay(pos);
                    // Draw a wire in the player's hand after this
                } else {
                    BlockPos lastPos = inst.getLastRelay();
                    double distanceSq = lastPos.distanceSq(pos);
                    TileEntity lastPosTile = worldIn.getTileEntity(lastPos);

                    if (!lastPos.equals(pos) && lastPosTile != null && currentTile != null) {
                        if (distanceSq < VSConfig.relayWireLength * VSConfig.relayWireLength) {
                            IVSNode lastPosNode = ((IVSNodeProvider) lastPosTile).getNode();
                            IVSNode currentPosNode = ((IVSNodeProvider) currentTile).getNode();
                            if (lastPosNode != null && currentPosNode != null) {
                                if (currentPosNode.isLinkedToNode(lastPosNode)) {
                                    currentPosNode.breakConnection(lastPosNode);
                                    // Break connection and give player the correct wire back
                                    ItemStack drop = new ItemStack(wireType.toItem());
                                    if (player.inventory.addItemStackToInventory(drop)) {
                                        player.dropItem(drop, false);
                                    }
                                } else if (currentPosNode.canLinkToOtherNode(lastPosNode)) {
                                    currentPosNode.makeConnection(lastPosNode, this.wireType);
                                    stack.damageItem(1, player);
                                } else {
                                    player.sendMessage(new TextComponentString(TextFormatting.RED +
                                        I18n.format("message.vs_control.error_relay_wire_limit", VSConfig.networkRelayLimit)));
                                }
                                inst.setLastRelay(null);
                            }
                        } else {
                            player.sendMessage(new TextComponentString(TextFormatting.RED
                                + I18n.format("message.vs_control.error_relay_wire_length")));
                            inst.setLastRelay(null);
                        }
                    } else {
                        inst.setLastRelay(pos);
                    }
                }
            }
        }

        if (currentTile instanceof IVSNodeProvider) {
            return EnumActionResult.SUCCESS;
        }

        return EnumActionResult.PASS;
    }

}