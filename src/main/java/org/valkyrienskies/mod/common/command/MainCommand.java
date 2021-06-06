package org.valkyrienskies.mod.common.command;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import org.valkyrienskies.mod.common.command.MainCommand.*;
import org.valkyrienskies.mod.common.command.autocompleters.ShipNameAutocompleter;
import org.valkyrienskies.mod.common.command.autocompleters.WorldAutocompleter;
import org.valkyrienskies.mod.common.physics.PhysicsCalculations;
import org.valkyrienskies.mod.common.ships.QueryableShipData;
import org.valkyrienskies.mod.common.ships.ShipData;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.IHasShipManager;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject.DeconstructState;
import org.valkyrienskies.mod.common.ships.ship_world.WorldServerShipManager;
import org.valkyrienskies.mod.common.util.JOML;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import org.valkyrienskies.mod.common.util.multithreaded.VSWorldPhysicsLoop;
import picocli.CommandLine.*;
import picocli.CommandLine.Model.CommandSpec;

import javax.inject.Inject;
import java.util.stream.Collectors;

@Command(name = "valkyrienskies", aliases = "vs",
    synopsisSubcommandLabel = "COMMAND", mixinStandardHelpOptions = true,
    usageHelpWidth = 55,
    subcommands = {
        HelpCommand.class,
        ListShips.class,
        DisableShip.class,
        GC.class,
        TPS.class,
        TeleportTo.class,
        DeconstructShip.class,
        DeleteShip.class,
        TeleportShipTo.class,
        TeleportShipHere.class,
        Rename.class
    })
public class MainCommand implements Runnable {

    @Spec
    private Model.CommandSpec spec;

    @Inject
    private ICommandSender sender;

    @Override
    public void run() {
        String usageMessage = spec.commandLine().getUsageMessage().replace("\r", "");

        sender.sendMessage(new TextComponentString(usageMessage));
    }

    @Command(name = "deconstruct-ship", aliases = "deconstruct")
    static class DeconstructShip implements Runnable {

        @Inject
        ICommandSender sender;

        @Parameters(paramLabel = "name", index = "0", completionCandidates = ShipNameAutocompleter.class)
        ShipData shipData;

        @Override
        public void run() {
            deletePhyso(sender, shipData, DeconstructState.DECONSTRUCT_NORMAL);
        }
    }

    @Command(name = "delete-ship", aliases = "delete")
    static class DeleteShip implements Runnable {

        @Inject
        ICommandSender sender;

        @Parameters(paramLabel = "name", index = "0", completionCandidates = ShipNameAutocompleter.class)
        ShipData shipData;

        @Override
        public void run() {
            deletePhyso(sender, shipData, DeconstructState.DECONSTRUCT_IMMEDIATE_NO_COPY);
        }
    }

    private static void deletePhyso(ICommandSender sender, ShipData shipData, DeconstructState state) {
        final WorldServerShipManager world = ValkyrienUtils.getServerShipManager(sender.getEntityWorld());
        final PhysicsObject obj = world.getPhysObjectFromUUID(shipData.getUuid());
        if (obj != null) {
            obj.setDeconstructState(state);

            switch (state) {
                case DECONSTRUCT_NORMAL:
                    sender.sendMessage(new TextComponentString("That ship is being deconstructed"));
                    break;
                case DECONSTRUCT_IMMEDIATE_NO_COPY:
                    sender.sendMessage(new TextComponentString("That ship will be deleted in the next tick."));
                    break;
            }
        } else {
            sender.sendMessage(new TextComponentString("That ship is not loaded"));
        }
    }

    @Command(name = "teleport-to", aliases = "tpto")
    static class TeleportTo implements Runnable {

        @Inject
        ICommandSender sender;

        @Parameters(paramLabel = "name", index = "0", completionCandidates = ShipNameAutocompleter.class)
        ShipData shipData;

        public void run() {
            if (!(sender instanceof EntityPlayer)) {
                sender.sendMessage(new TextComponentString("You must execute this command as "
                    + "a player!"));
            }

            ShipTransform pos = shipData.getShipTransform();
            ((EntityPlayer) sender).setPositionAndUpdate(pos.getPosX(), pos.getPosY(), pos.getPosZ());
        }

    }

    @Command(name = "gc")
    static class GC implements Runnable {

        @Inject
        ICommandSender sender;

        public void run() {
            System.gc();
            sender.sendMessage(new TextComponentTranslation("commands.vs.gc.success"));
        }

    }

    @Command(name = "rename")
    static class Rename implements Runnable {

        @Inject
        ICommandSender sender;

        @Parameters(paramLabel = "oldName", index = "0", completionCandidates = ShipNameAutocompleter.class)
        ShipData oldShip;

        @Parameters(paramLabel = "newName", index = "1")
        String newName;

        public void run() {
            String oldName = oldShip.getName();
            oldShip.setName(newName);
            sender.sendMessage(new TextComponentTranslation("commands.vs.rename.success", oldName, newName));
        }

    }

    @Command(name = "tps")
    static class TPS implements Runnable {

        @Inject
        ICommandSender sender;

        @Option(names = {"--world", "-w"}, completionCandidates = WorldAutocompleter.class)
        World world;

        @Override
        public void run() {
            if (world == null) {
                world = sender.getEntityWorld();
            }

            VSWorldPhysicsLoop worldPhysicsThread = ((WorldServerShipManager) ((IHasShipManager) world)
                .getManager()).getPhysicsLoop();

            if (worldPhysicsThread != null) {
                long averagePhysTickTimeNano = worldPhysicsThread.getAveragePhysicsTickTimeNano();
                double ticksPerSecond = 1000000000D / ((double) averagePhysTickTimeNano);
                double ticksPerSecondTwoDecimals = Math.floor(ticksPerSecond * 100) / 100;
                sender.sendMessage(new TextComponentString(
                    "Player world: " + ticksPerSecondTwoDecimals + " physics ticks per second"));
            }
        }
    }

    @Command(name = "ship-physics")
    static class DisableShip implements Runnable {

        @Inject
        ICommandSender sender;

        @Spec
        CommandSpec spec;

        @Parameters(paramLabel = "name", index = "0", completionCandidates = ShipNameAutocompleter.class)
        ShipData shipData;

        @Parameters(index = "1", arity = "0..1")
        boolean enabled;

        @Override
        public void run() {
            boolean enabledWasSpecified = spec.commandLine().getParseResult().hasMatchedPositional(1);
            boolean isPhysicsEnabled = shipData.isPhysicsEnabled();
            String physicsState = isPhysicsEnabled ? "enabled" : "disabled";

            if (enabledWasSpecified) {
                shipData.setPhysicsEnabled(enabled);

                if (isPhysicsEnabled == enabled) {
                    sender.sendMessage(new TextComponentString(
                        "That ship's physics were not changed from " + physicsState));
                } else {
                    String newPhysicsState = enabled ? "enabled" : "disabled";

                    sender.sendMessage(new TextComponentString(
                        "That ship's physics were changed from " + physicsState + " to " + newPhysicsState
                    ));
                }
            } else {
                sender.sendMessage(new TextComponentString(
                    "That ship's physics are: " + physicsState
                ));
            }
        }
    }

    @Command(name = "list-ships", aliases = "ls")
    static class ListShips implements Runnable {

        @Inject
        ICommandSender sender;

        @Option(names = {"-v", "--verbose"})
        boolean verbose;

        @Override
        public void run() {
            World world = sender.getEntityWorld();
            QueryableShipData data = ValkyrienUtils.getQueryableData(world);

            if (data.getShips().size() == 0) {
                // There are no ships
                sender.sendMessage(new TextComponentTranslation("commands.vs.list-ships.noships"));
                return;
            }

            String listOfShips;

            if (verbose) {
                listOfShips = data.getShips()
                    .stream()
                    .map(shipData -> {
                        if (shipData.getShipTransform() == null) {
                            // Unknown Location (this should be an error? TODO: look into this)
                            return String.format("%s, Unknown Location", shipData.getName());
                        } else {
                            // Known Location
                            return String.format("%s [%.1f, %.1f, %.1f]", shipData.getName(),
                                shipData.getShipTransform().getPosX(),
                                shipData.getShipTransform().getPosY(),
                                shipData.getShipTransform().getPosZ());
                        }
                    })
                    .collect(Collectors.joining(",\n"));
            } else {
                listOfShips = data.getShips()
                    .stream()
                    .map(ShipData::getName)
                    .collect(Collectors.joining(",\n"));
            }

            sender.sendMessage(new TextComponentTranslation(
                "commands.vs.list-ships.ships", listOfShips));
        }

    }

    @Command(name = "teleport-ship-to", aliases = "tp-ship-to", customSynopsis = "/tp-ship-to ship-name <x,y,z>")
    static class TeleportShipTo implements Runnable {

        @Inject
        ICommandSender sender;

        @Parameters(paramLabel = "name", index = "0", completionCandidates = ShipNameAutocompleter.class)
        ShipData shipData;

        @Parameters(paramLabel = "position", index = "1")
        Vec3d position;

        @Override
        public void run() {
            teleportShipToPosition(shipData, position, sender);
        }
    }

    @Command(name = "teleport-ship-here", aliases = "tp-ship-here", customSynopsis = "/tp-ship-here ship-name")
    static class TeleportShipHere implements Runnable {

        @Inject
        ICommandSender sender;

        @Parameters(paramLabel = "name", index = "0", completionCandidates = ShipNameAutocompleter.class)
        ShipData shipData;

        @Override
        public void run() {
            final Vec3d commandPosition = sender.getPositionVector();
            if (commandPosition == Vec3d.ZERO) {
                sender.sendMessage(new TextComponentString("The command sender doesn't have a position, ignoring the command."));
                return;
            }
            teleportShipToPosition(shipData, commandPosition, sender);
        }
    }

    private static void teleportShipToPosition(final ShipData ship, final Vec3d position, final ICommandSender sender) {
        try {
            final World world = sender.getEntityWorld();
            final WorldServerShipManager shipManager = ValkyrienUtils.getServerShipManager(world);
            final PhysicsObject shipObject = shipManager.getPhysObjectFromUUID(ship.getUuid());

            // Create the new ship transform that moves the ship to this position
            final ShipTransform shipTransform = ship.getShipTransform();
            final ShipTransform newTransform = new ShipTransform(JOML.convert(position), shipTransform.getCenterCoord());

            if (shipObject != null) {
                // The ship already exists in the world, so we need to update the physics transform as well
                final PhysicsCalculations physicsCalculations = shipObject.getPhysicsCalculations();
                physicsCalculations.setForceToUseGameTransform(true);
                // Also update the transform in the ShipTransformationManager
                shipObject.setForceToUseShipDataTransform(true);
                shipObject.setTicksSinceShipTeleport(0);
            }

            // Update the ship transform of the ship data.
            ship.setPhysicsEnabled(false);
            ship.setPrevTickShipTransform(newTransform);
            ship.setShipTransform(newTransform);

            System.out.println(String.format("Teleporting ship %s to %s", ship.getName(), position.toString()));
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

}
