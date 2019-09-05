package org.valkyrienskies.mod.common.command;

import java.util.stream.Collectors;
import javax.inject.Inject;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import org.valkyrienskies.mod.common.multithreaded.VWThread;
import org.valkyrienskies.mod.common.physmanagement.shipdata.QueryableShipData;
import org.valkyrienskies.mod.common.physmanagement.shipdata.ShipData;
import org.valkyrienskies.mod.common.ship_handling.IHasShipManager;
import org.valkyrienskies.mod.common.ship_handling.WorldServerShipManager;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Model;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "valkyrienskies", aliases = "vs",
    synopsisSubcommandLabel = "COMMAND", mixinStandardHelpOptions = true,
    usageHelpWidth = 55,
    subcommands = {
        HelpCommand.class,
        MainCommand.ListShips.class,
        MainCommand.TPS.class})
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

    @Command(name = "tps")
    static class TPS implements Runnable {

        @Inject
        ICommandSender sender;

        @Option(names = {"--world", "-w"})
        World world;

        @Override
        public void run() {
            if (world == null) {
                world = sender.getEntityWorld();
            }

            VWThread worldPhysicsThread = ((WorldServerShipManager) ((IHasShipManager) world)
                .getManager()).getPhysicsThread();

            if (worldPhysicsThread != null) {
                long averagePhysTickTimeNano = worldPhysicsThread.getAveragePhysicsTickTimeNano();
                double ticksPerSecond = 1000000000D / ((double) averagePhysTickTimeNano);
                double ticksPerSecondTwoDecimals = Math.floor(ticksPerSecond * 100) / 100;
                sender.sendMessage(new TextComponentString(
                    "Player world: " + ticksPerSecondTwoDecimals + " physics ticks per second"));
            }
        }
    }

    @Command(name = "list-ships", aliases = "ls")
    static class ListShips implements Runnable {

        @Inject
        ICommandSender sender;

        @Option(names = {"-v", "--verbose"})
        boolean verbose;

        public void run() {
            World world = sender.getEntityWorld();
            QueryableShipData data = ValkyrienUtils.getQueryableData(world);

            if (data.getShips().size() == 0) {
                // There are no ships
                sender.sendMessage(new TextComponentTranslation(
                    "commands.valkyrienskies.list-ships.noships"));
                return;
            }

            String listOfShips;

            if (verbose) {
                listOfShips = data.getShips()
                    .stream()
                    .map(shipData -> {
                        if (shipData.getPositionData() == null) {
                            // Unknown Location (this should be an error? TODO: look into this)
                            return String.format("%s, Unknown Location", shipData.getName());
                        } else {
                            // Known Location
                            return String.format("%s [%.1f, %.1f, %.1f]", shipData.getName(),
                                shipData.getPositionData().getPosX(),
                                shipData.getPositionData().getPosY(),
                                shipData.getPositionData().getPosZ());
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
                "commands.valkyrienskies.list-ships.ships", listOfShips));
        }

    }

}
