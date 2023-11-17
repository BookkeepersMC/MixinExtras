package com.llamalad7.mixinextras.expression.impl.ast.expressions;

import com.google.gson.annotations.SerializedName;
import com.llamalad7.mixinextras.expression.impl.ast.identifiers.Identifier;
import com.llamalad7.mixinextras.expression.impl.flow.FlowValue;
import com.llamalad7.mixinextras.expression.impl.pool.IdentifierPool;
import com.llamalad7.mixinextras.expression.impl.serialization.SerializedTypeName;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;

import java.util.List;

@SerializedTypeName("()")
public class StaticMethodCallExpression implements SimpleExpression {
    @SerializedName("id")
    public final Identifier name;
    @SerializedName("args")
    public final List<Expression> arguments;

    public StaticMethodCallExpression(Identifier name, List<Expression> arguments) {
        this.name = name;
        this.arguments = arguments;
    }

    @Override
    public boolean matches(FlowValue node, IdentifierPool pool, OutputSink sink) {
        AbstractInsnNode insn = node.getInsn();
        return insn.getOpcode() == Opcodes.INVOKESTATIC
                && name.matches(pool, insn) && inputsMatch(node, pool, sink, arguments.toArray(new Expression[0]));
    }
}
