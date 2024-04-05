package com.llamalad7.mixinextras.expression.impl.utils;

import com.llamalad7.mixinextras.utils.Decorations;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.spongepowered.asm.mixin.injection.struct.Target;

import java.util.function.BiConsumer;

public class ComparisonInfo {
    private final int comparison;
    protected final AbstractInsnNode node;
    public final Type input;
    public final boolean jumpOnTrue;

    public ComparisonInfo(int comparison, AbstractInsnNode node, Type input, boolean jumpOnTrue) {
        this.comparison = comparison;
        this.node = node;
        this.input = input;
        this.jumpOnTrue = jumpOnTrue;
    }

    public void attach(BiConsumer<String, Object> decorate, BiConsumer<String, Object> decorateInjectorSpecific) {
        decorateInjectorSpecific.accept(Decorations.COMPARISON_INFO, this);
        decorate.accept(Decorations.SIMPLE_OPERATION_ARGS, new Type[]{input, input});
        decorate.accept(Decorations.SIMPLE_OPERATION_RETURN_TYPE, Type.BOOLEAN_TYPE);
        decorate.accept(Decorations.SIMPLE_OPERATION_PARAM_NAMES, new String[]{"left", "right"});
    }

    public int copyJump(InsnList insns) {
        return comparison;
    }

    public LabelNode getJumpTarget() {
        return ((JumpInsnNode) node).label;
    }

    public void cleanup(Target target) {
    }
}
