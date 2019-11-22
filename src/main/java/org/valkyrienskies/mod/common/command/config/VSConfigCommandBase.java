package org.valkyrienskies.mod.common.command.config;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import lombok.SneakyThrows;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import org.valkyrienskies.mod.common.command.framework.VSCommandUtil;

/**
 * This class is used to generate a command for a VS/Forge config. It's used by
 * <code>/vsconfig</code>
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class VSConfigCommandBase extends CommandBase {

    private String name;

    private ConfigCommandParentNode root;
    private Method sync;

    private List<String> aliases;

    /**
     * @param name        This is the name of the command that's going to be generated
     * @param configClass This is the class of the config that's going to have a command generated
     *                    for it. It should define a public static void sync().
     * @param aliases     These are the alternative names for the command that's going to be
     *                    generated
     */
    public VSConfigCommandBase(String name, Class<?> configClass, String... aliases) {
        try {
            this.sync = configClass.getMethod("sync");

            if (!Modifier.isStatic(this.sync.getModifiers())) {
                throw new IllegalArgumentException(
                    "That class does not have a public static sync method on it!");
            }
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(
                "That class does not have a public static sync method on it!", e);
        }

        this.name = name;
        this.aliases = Arrays.asList(aliases);
        root = new ConfigCommandParentNode(name, Collections.emptyMap());

        processFields(configClass, root);
    }

    @Override
    public List<String> getAliases() {
        return this.aliases;
    }

    // TODO: allow usage of arrays
    private static void processFields(Class<?> configClass, ConfigCommandParentNode root) {
        List<Class<?>> subcategories = Arrays.asList(configClass.getDeclaredClasses());

        for (Field field : configClass.getFields()) {
            // Ensure the field is public static and supported
            if (Modifier.isStatic(field.getModifiers()) &&
                Modifier.isPublic(field.getModifiers())) {

                if (subcategories.contains(field.getType())) {
                    // If the field is the instance for a subcategory
                    processFieldForSubcategory(field.getType(), field, root);
                } else if (ConfigCommandUtils.isSupportedType(field.getType())) {
                    // Or its a normal field
                    root.addChild(new ConfigCommandEndNode(field.getName(),
                        str -> ConfigCommandUtils.setFieldFromString(str, field),
                        () -> ConfigCommandUtils.getStringFromField(field)));
                } // Ignore fields that aren't supported or a subcategory
            }
        }
    }

    // TODO: allow usage of arrays
    @SneakyThrows(IllegalAccessException.class)
    private static void processFieldForSubcategory(Class<?> subcategory, Field subcatField,
        ConfigCommandParentNode root) {
        // Note: subcatField should always be static
        Object subcategoryObj = subcatField.get(null);

        ShortName subcatShortName = subcatField.getAnnotation(ShortName.class);

        String subcatDisplayName = subcatShortName == null ?
            subcatField.getName() : subcatShortName.value();

        ConfigCommandParentNode subcategoryNode = new ConfigCommandParentNode(subcatDisplayName);
        root.addChild(subcategoryNode);

        for (Field field : subcategory.getFields()) {
            ShortName fieldShortName = field.getAnnotation(ShortName.class);
            String fieldDisplayName = fieldShortName == null ?
                field.getName() : fieldShortName.value();

            // Ensure field is public NOT static and supported
            if (!Modifier.isStatic(field.getModifiers()) &&
                Modifier.isPublic(field.getModifiers()) &&
                ConfigCommandUtils.isSupportedType(field.getType())) {

                subcategoryNode.addChild(new ConfigCommandEndNode(fieldDisplayName,
                    str -> ConfigCommandUtils.setFieldFromString(str, field, subcategoryObj),
                    () -> ConfigCommandUtils.getStringFromField(field, subcategoryObj)));
            }
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return String.format("/%s <option>", name);
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        args = VSCommandUtil.toProperArgs(args);

        ConfigCommandNode currentNode = root;

        for (int i = 0; i < args.length; i++) {
            if (currentNode instanceof ConfigCommandParentNode) {
                currentNode = ((ConfigCommandParentNode) currentNode).getChild(args[i]);
            }

            if (currentNode instanceof ConfigCommandEndNode && i < args.length - 1) {
                // setting the option
                ((ConfigCommandEndNode) currentNode).getOptionSetter().accept(args[i + 1]);
                try {
                    sync.invoke(null);
                } catch (Exception e) {
                    throw new RuntimeException(e); //blah blaah
                }
                sender.sendMessage(new TextComponentString("Set " + currentNode.getName() +
                    " = " + ((ConfigCommandEndNode) currentNode).getOptionGetter().get().toString()
                ));

                break;
            } else if (currentNode instanceof ConfigCommandEndNode && i == args.length - 1) {
                // getting the option
                sender.sendMessage(new TextComponentString(currentNode.getName() +
                    " = " + ((ConfigCommandEndNode) currentNode).getOptionGetter().get().toString()
                ));
                break;
            } else if (i == args.length - 1) {
                sender.sendMessage(new TextComponentString(
                    "That is a subcategory, please specify additional fields"));
            }

            if (currentNode == null) {
                sender.sendMessage(new TextComponentString(
                    String.format("Unrecognized option: %s", args[i])
                ));
            }
        }
    }

    // TODO autocomplete boolean values, enums, autocompletion annotation
    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
        String[] args, @Nullable BlockPos targetPos) {

        args = VSCommandUtil.toProperArgs(args);

        ConfigCommandNode currentNode = root;

        if (args.length == 0) {
            return root.childrenNames();
        }

        for (int i = 0; i < args.length; i++) {
            System.out.println(args[i]);
            if (currentNode instanceof ConfigCommandParentNode) {
                ConfigCommandNode nextNode =
                    ((ConfigCommandParentNode) currentNode).getChild(args[i]);

                if (nextNode == null) {
                    return ((ConfigCommandParentNode) currentNode).getChildrenStartingWith(args[i])
                        .stream()
                        .map(ConfigCommandNode::getName)
                        .collect(Collectors.toList());
                } else if (i == args.length - 1 && nextNode instanceof ConfigCommandParentNode) {
                    // We have reached the last argument, so the user must be looking for all the
                    // values of this subcategory

                    return ((ConfigCommandParentNode) nextNode).childrenNames();
                } else {
                    currentNode = nextNode;
                }
            }
        }

        return Collections.emptyList();
    }
}
