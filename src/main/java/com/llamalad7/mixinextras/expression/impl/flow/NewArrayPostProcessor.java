package com.llamalad7.mixinextras.expression.impl.flow;

import org.apache.commons.lang3.tuple.Pair;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.util.Bytecode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class NewArrayPostProcessor implements FlowPostProcessor {
    private final Comparator<Pair<FlowValue, Integer>> insnIndexComparator;

    public NewArrayPostProcessor(MethodNode method) {
        this.insnIndexComparator = Comparator.comparingInt(o -> method.instructions.indexOf(o.getLeft().getInsn()));
    }

    @Override
    public void process(FlowValue node, Consumer<FlowValue> syntheticMarker) {
        AbstractInsnNode insn = node.getInsn();
        if (insn.getOpcode() == Opcodes.ANEWARRAY || insn.getOpcode() == Opcodes.NEWARRAY) {
            List<FlowValue> stores = getCreationStores(node);
            if (stores == null) {
                return;
            }
            for (FlowValue store : stores) {
                syntheticMarker.accept(store);
                syntheticMarker.accept(store.getInput(1));
            }
            node.decorate(FlowDecorations.ARRAY_CREATION_INFO, new ArrayCreationInfo(
                    stores.stream().map(it -> it.getInput(2)).collect(Collectors.toList())
            ));
        }
    }

    private List<FlowValue> getCreationStores(FlowValue array) {
        Integer size = getIntConstant(array.getInput(0));
        if (size == null) {
            return null;
        }
        List<FlowValue> sortedNext = array.getNext().stream()
                .filter(it -> !it.getLeft().isComplex())
                .sorted(insnIndexComparator)
                .map(Pair::getLeft)
                .collect(Collectors.toList());
        if (sortedNext.size() < size) {
            return null;
        }
        List<FlowValue> stores = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            FlowValue store = sortedNext.get(i);
            if (!isStore(array, store, i)) {
                return null;
            }
            stores.add(store);
        }
        return stores;
    }

    private Integer getIntConstant(FlowValue node) {
        if (node.isComplex()) {
            return null;
        }
        Object cst = Bytecode.getConstant(node.getInsn());
        if (!(cst instanceof Integer)) {
            return null;
        }
        return (int) cst;
    }

    private boolean isStore(FlowValue array, FlowValue store, int index) {
        int opcode = store.getInsn().getOpcode();
        if (opcode < Opcodes.IASTORE || opcode > Opcodes.SASTORE) {
            return false;
        }
        if (store.getInput(0) != array) {
            return false;
        }
        return Objects.equals(index, getIntConstant(store.getInput(1)));
    }
}
