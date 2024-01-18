package com.llamalad7.mixinextras.expression.impl.ast.identifiers;

import com.llamalad7.mixinextras.expression.impl.pool.IdentifierPool;
import org.objectweb.asm.tree.AbstractInsnNode;

public interface Identifier {
    boolean matches(IdentifierPool pool, AbstractInsnNode insn, Role role);

    enum Role {
        MEMBER, TYPE
    }
}
