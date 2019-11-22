package com.best108.atom_animation_reader.impl;

import com.best108.atom_animation_reader.IAtomAnimation;
import com.best108.atom_animation_reader.IAtomAnimationBuilder;
import com.best108.atom_animation_reader.IModelRenderer;
import com.best108.atom_animation_reader.parsers.AtomParser;
import com.best108.atom_animation_reader.parsers.AtomParserElement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.valkyrienskies.mod.common.coordinates.VectorImmutable;
import org.valkyrienskies.mod.common.math.Vector;

public class BasicAtomAnimationBuilder implements IAtomAnimationBuilder {

    private final int minKeyFrame;
    private final int maxKeyFrame;
    private final List<DagNode> renderNodes;

    public BasicAtomAnimationBuilder(AtomParser parser) {
        minKeyFrame = Integer.parseInt(parser.head.properties.get(6)[1]);
        maxKeyFrame = Integer.parseInt(parser.head.properties.get(7)[1]);
        this.renderNodes = new ArrayList<DagNode>();

        for (AtomParserElement dagNodesParsed : parser.head.branches) {
            renderNodes.add(new DagNode(dagNodesParsed));
        }

        for (DagNode dagNode : renderNodes) {
            // System.out.println(dagNode.modelName);
            for (AnimationDataNode animationNode : dagNode.animationNodes) {
                // System.out.println("     " + animationNode.animationType);
                for (String[] keyFrame : animationNode.animKeyframes.keyframes) {
                    // System.out.println("          " + Arrays.toString(keyFrame));
                }
            }
        }
    }

    @Override
    public IAtomAnimation build(IModelRenderer modelRenderer) {
        // Generate the compiled IAtomAnimation
        List<BasicDagNodeRenderer> dagNodeRenderers = new ArrayList<>();
        Map<String, VectorImmutable> modelNamesToPivots = new HashMap<String, VectorImmutable>();
        for (DagNode dagNode : renderNodes) {
            // Is this node defining a pivot, or an animation?
            if (dagNode.modelName.endsWith("_pivot")) {
                // This is a pivot, add it to the local registry.
                Vector pivotPoint = new Vector();
                for (AnimationDataNode animData : dagNode.animationNodes) {
                    if (animData.animationType.equals("translateX")) {
                        pivotPoint.x = Double.valueOf(animData.animKeyframes.keyframes.get(0)[1]);
                    }
                    if (animData.animationType.equals("translateY")) {
                        pivotPoint.y = Double.valueOf(animData.animKeyframes.keyframes.get(0)[1]);
                    }
                    if (animData.animationType.equals("translateZ")) {
                        pivotPoint.z = Double.valueOf(animData.animKeyframes.keyframes.get(0)[1]);
                    }
                }
                // Put the pivot in the local registry.
                modelNamesToPivots
                    .put(dagNode.modelName.substring(0, dagNode.modelName.length() - 6),
                        pivotPoint.toImmutable());
            } else {
                // This is an animation node.
                List<BasicAnimationTransform> animations = new ArrayList<BasicAnimationTransform>();
                for (AnimationDataNode animationNode : dagNode.animationNodes) {
                    BasicAnimationTransform basicTransform = new BasicAnimationTransform(
                        animationNode.animationType, animationNode.animKeyframes.keyframes);
                    animations.add(basicTransform);
                }
                BasicDagNodeRenderer dagRenderer = new BasicDagNodeRenderer(dagNode.modelName,
                    animations, modelRenderer);
                dagNodeRenderers.add(dagRenderer);
            }
        }

        // Put the pivots into the animation nodes.
        for (BasicDagNodeRenderer dagNodeRenderer : dagNodeRenderers) {
            String modelName = dagNodeRenderer.getModelName();
            if (modelNamesToPivots.containsKey(modelName)) {
                dagNodeRenderer.setPivot(modelNamesToPivots.get(modelName));
            }
        }

        boolean hasChanged = true;
        while (hasChanged) {
            hasChanged = false;
            for (BasicDagNodeRenderer dagNodeRenderer : dagNodeRenderers) {
                String modelName = dagNodeRenderer.getModelName();
                String[] split = modelName.split("_");

                if (split.length > 0) {
                    // Then we could possibly be a group
                    if (modelName.contains("defgroup")) {
                        int index = -1;
                        for (int i = 0; i < split.length; i++) {
                            if (split[i].contains("defgroup")) {
                                index = i;
                                break;
                            }
                        }
                        if (index == -1) {
                            throw new IllegalStateException(
                                "This is a total mystery!\nOffending model name: " + modelName
                                    + "\n");
                        }
                        // We are a group; let us gather the children.
                        String groupName = split[index].replaceAll("defgroup", "");
                        // Check all of the dagNodeRenderers for any children
                        List<BasicDagNodeRenderer> children = new ArrayList<>();

                        for (BasicDagNodeRenderer potentialChild : dagNodeRenderers) {
                            // Check if the possible child belongs to a group
                            if (potentialChild.getModelName()
                                .contains("_grp")) {
                                String[] possibleChildNameSplit = potentialChild.getModelName()
                                    .split("_");
                                // Check if the suffix is in a group
                                if (possibleChildNameSplit[possibleChildNameSplit.length - 1]
                                    .contains("grp")) {
                                    String possibleChildGroup = possibleChildNameSplit[
                                        possibleChildNameSplit.length - 1].replaceAll("grp", "");
                                    // Check if this potential child is in this group
                                    if (possibleChildGroup.equals(groupName)) {
                                        children.add(potentialChild);
                                    }
                                }
                            }
                        }
                        hasChanged = !children.isEmpty();
                        if (hasChanged) {
                            List<BasicDagNodeRenderer> newDagRenderers = new ArrayList<>(
                                dagNodeRenderers);
                            newDagRenderers.removeAll(children);
                            newDagRenderers.remove(dagNodeRenderer);
                            newDagRenderers.add(
                                new GroupedDagNodeRenderer(dagNodeRenderer.getModelName(),
                                    dagNodeRenderer.transformations, children,
                                    dagNodeRenderer.pivot));

                            dagNodeRenderers = newDagRenderers;
                            break;
                        }
                    }
                }
            }
        }

        return new BasicAtomAnimation(dagNodeRenderers, minKeyFrame, maxKeyFrame);
    }

    public Set<String> getModelObjsUsed() {
        Set<String> toReturn = new HashSet<>();
        for (DagNode dagNode : renderNodes) {
            if (!dagNode.modelName.endsWith("_pivot")) {
                toReturn.add(dagNode.modelName);
            }
        }
        return toReturn;
    }

    static class DagNode {

        final String modelName;
        // Nodes in the order their transform will be applied.
        final List<AnimationDataNode> animationNodes;

        DagNode(AtomParserElement parserElement) {
            List<String[]> properties = parserElement.properties;
            if (!properties.get(0)[0].contains("|")) {
                this.modelName = properties.get(0)[0];
            } else {
                // To handle any group names in the atom files.
                this.modelName = properties.get(0)[0].split(Pattern.quote("|"))[1];
            }

            this.animationNodes = new ArrayList<AnimationDataNode>();
            int currentBranch = 0;
            for (String[] line : properties) {
                if (line[0].equals("anim")) {
                    // Create a new animation node.
                    List<String[]> animKeyframes = parserElement.branches
                        .get(currentBranch).properties;
                    Keyframes keyFrames = new Keyframes(
                        parserElement.branches.get(currentBranch).branches.get(0).properties);
                    AnimationDataNode animationNode = new AnimationDataNode(line[2], keyFrames);
                    animationNodes.add(animationNode);
                    currentBranch++;
                }
            }
        }
    }

    static class AnimationDataNode {

        final String animationType;
        final Keyframes animKeyframes;

        AnimationDataNode(String animationType, Keyframes animKeyframes) {
            this.animationType = animationType;
            this.animKeyframes = animKeyframes;
        }
    }

    static class Keyframes {

        final List<String[]> keyframes;

        Keyframes(List<String[]> keyframes) {
            this.keyframes = keyframes;
        }
    }

}
