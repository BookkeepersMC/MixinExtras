package com.llamalad7.mixinextras.expression.impl.flow;

import com.llamalad7.mixinextras.utils.ASMUtils;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Interpreter;
import org.spongepowered.asm.util.Locals;
import org.spongepowered.asm.util.asm.ASM;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.objectweb.asm.Opcodes.*;

public class FlowInterpreter extends Interpreter<FlowValue> {
    private final Map<AbstractInsnNode, FlowValue> cache = new IdentityHashMap<>();
    private final ClassNode classNode;
    private final MethodNode methodNode;

    public FlowInterpreter(ClassNode classNode, MethodNode methodNode) {
        super(ASM.API_VERSION);
        this.classNode = classNode;
        this.methodNode = methodNode;
        // Computing this during the analysis causes mixin to add labels and screw up the instruction indices.
        // Do it ahead of time and let mixin cache it.
        Locals.getGeneratedLocalVariableTable(classNode, methodNode);
    }

    public Map<AbstractInsnNode, FlowValue> finish() {
        for (Map.Entry<AbstractInsnNode, FlowValue> entry : cache.entrySet()) {
            entry.getValue().finish();
        }
        return Collections.unmodifiableMap(cache);
    }

    @Override
    public FlowValue newValue(final Type type) {
        if (type == null) {
            return ComplexFlowValue.UNINITIALIZED;
        }
        if (type == Type.VOID_TYPE) {
            return null;
        }
        return new ComplexFlowValue(type);
    }

    @Override
    public FlowValue newOperation(final AbstractInsnNode insn) {
        Type type;
        switch (insn.getOpcode()) {
            case ACONST_NULL:
                type = ASMUtils.NULL_TYPE;
                break;
            case ICONST_M1:
            case ICONST_0:
            case ICONST_1:
            case ICONST_2:
            case ICONST_3:
            case ICONST_4:
            case ICONST_5:
            case BIPUSH:
            case SIPUSH:
                type = Type.INT_TYPE;
                break;
            case LCONST_0:
            case LCONST_1:
                type = Type.LONG_TYPE;
                break;
            case FCONST_0:
            case FCONST_1:
            case FCONST_2:
                type = Type.FLOAT_TYPE;
                break;
            case DCONST_0:
            case DCONST_1:
                type = Type.DOUBLE_TYPE;
                break;
            case LDC:
                Object cst = ((LdcInsnNode) insn).cst;
                if (cst instanceof Integer) {
                    type = Type.INT_TYPE;
                    break;
                }
                if (cst instanceof Float) {
                    type = Type.FLOAT_TYPE;
                    break;
                }
                if (cst instanceof Long) {
                    type = Type.LONG_TYPE;
                    break;
                }
                if (cst instanceof Double) {
                    type = Type.DOUBLE_TYPE;
                    break;
                }
                if (cst instanceof String) {
                    type = Type.getType(String.class);
                    break;
                }
                if (cst instanceof Type) {
                    int sort = ((Type) cst).getSort();
                    if (sort == Type.OBJECT || sort == Type.ARRAY) {
                        type = Type.getType(Class.class);
                        break;
                    }
                    if (sort == Type.METHOD) {
                        type = Type.getType(MethodType.class);
                        break;
                    }
                }
                if (cst instanceof Handle) {
                    type = Type.getType(MethodHandle.class);
                    break;
                }
                throw new IllegalArgumentException("Illegal LDC constant "
                        + cst);
            case GETSTATIC:
                type = Type.getType(((FieldInsnNode) insn).desc);
                break;
            case NEW:
                type = Type.getObjectType(((TypeInsnNode) insn).desc);
                break;
            default:
                throw new Error("Internal error.");
        }
        return recordFlow(type, insn);
    }

    @Override
    public FlowValue copyOperation(final AbstractInsnNode insn, FlowValue value) {
        switch (insn.getOpcode()) {
            case DUP:
            case DUP_X1:
            case DUP_X2:
            case DUP2:
            case DUP2_X1:
            case DUP2_X2:
            case SWAP:
                return value;
            case ISTORE:
            case LSTORE:
            case FSTORE:
            case DSTORE:
            case ASTORE:
                recordFlow(Type.VOID_TYPE, insn, value);
                return new ComplexFlowValue(value.getType());
        }
        VarInsnNode varNode = (VarInsnNode) insn;
        LocalVariableNode local = Locals.getLocalVariableAt(classNode, methodNode, insn, varNode.var);
        Type type;
        if (local == null || local.desc == null) {
            System.err.println("Failed to find local variable type");
            type = value.getType();
        } else {
            type = Type.getType(local.desc);
        }
        return recordFlow(type, insn);
    }

    @Override
    public FlowValue unaryOperation(final AbstractInsnNode insn, final FlowValue value) {
        Type type;
        switch (insn.getOpcode()) {
            case INEG:
            case L2I:
            case F2I:
            case D2I:
            case ARRAYLENGTH:
                type = Type.INT_TYPE;
                break;
            case I2B:
                type = Type.BYTE_TYPE;
                break;
            case I2C:
                type = Type.CHAR_TYPE;
                break;
            case I2S:
                type = Type.SHORT_TYPE;
                break;
            case FNEG:
            case I2F:
            case L2F:
            case D2F:
                type = Type.FLOAT_TYPE;
                break;
            case LNEG:
            case I2L:
            case F2L:
            case D2L:
                type = Type.LONG_TYPE;
                break;
            case DNEG:
            case I2D:
            case L2D:
            case F2D:
                type = Type.DOUBLE_TYPE;
                break;
            case IFEQ:
            case IFNE:
            case IFLT:
            case IFGE:
            case IFGT:
            case IFLE:
            case TABLESWITCH:
            case LOOKUPSWITCH:
            case IRETURN:
            case LRETURN:
            case FRETURN:
            case DRETURN:
            case ARETURN:
            case PUTSTATIC:
            case ATHROW:
            case MONITORENTER:
            case MONITOREXIT:
            case IFNULL:
            case IFNONNULL:
                type = Type.VOID_TYPE;
                break;
            case GETFIELD:
                type = Type.getType(((FieldInsnNode) insn).desc);
                break;
            case NEWARRAY:
                switch (((IntInsnNode) insn).operand) {
                    case T_BOOLEAN:
                        type = Type.getType("[Z");
                        break;
                    case T_CHAR:
                        type = Type.getType("[C");
                        break;
                    case T_BYTE:
                        type = Type.getType("[B");
                        break;
                    case T_SHORT:
                        type = Type.getType("[S");
                        break;
                    case T_INT:
                        type = Type.getType("[I");
                        break;
                    case T_FLOAT:
                        type = Type.getType("[F");
                        break;
                    case T_DOUBLE:
                        type = Type.getType("[D");
                        break;
                    case T_LONG:
                        type = Type.getType("[J");
                        break;
                    default:
                        throw new Error("Invalid array type " + ((IntInsnNode) insn).operand);
                }
                break;
            case ANEWARRAY:
                String desc = ((TypeInsnNode) insn).desc;
                type = Type.getType("[" + Type.getObjectType(desc));
                break;
            case CHECKCAST:
                desc = ((TypeInsnNode) insn).desc;
                type = Type.getObjectType(desc);
                break;
            case INSTANCEOF:
                type = Type.BOOLEAN_TYPE;
                break;
            case IINC:
                recordFlow(Type.VOID_TYPE, insn);
                return new ComplexFlowValue(Type.INT_TYPE);
            default:
                throw new Error("Internal error.");
        }
        return recordFlow(type, insn, value);
    }

    @Override
    public FlowValue binaryOperation(
            final AbstractInsnNode insn, final FlowValue value1, final FlowValue value2) {
        Type type;
        switch (insn.getOpcode()) {
            case LALOAD:
            case DALOAD:
            case IALOAD:
            case FALOAD:
            case AALOAD:
            case BALOAD:
            case CALOAD:
            case SALOAD:
                type = Type.getType(value1.getType().getDescriptor().substring(1));
                break;
            case IADD:
            case ISUB:
            case IMUL:
            case IDIV:
            case IREM:
            case ISHL:
            case ISHR:
            case IUSHR:
            case IAND:
            case IOR:
            case IXOR:
            case LCMP:
            case FCMPL:
            case FCMPG:
            case DCMPL:
            case DCMPG:
                type = Type.INT_TYPE;
                break;
            case FADD:
            case FSUB:
            case FMUL:
            case FDIV:
            case FREM:
                type = Type.FLOAT_TYPE;
                break;
            case LADD:
            case LSUB:
            case LMUL:
            case LDIV:
            case LREM:
            case LSHL:
            case LSHR:
            case LUSHR:
            case LAND:
            case LOR:
            case LXOR:
                type = Type.LONG_TYPE;
                break;
            case DADD:
            case DSUB:
            case DMUL:
            case DDIV:
            case DREM:
                type = Type.DOUBLE_TYPE;
                break;
            case IF_ICMPEQ:
            case IF_ICMPNE:
            case IF_ICMPLT:
            case IF_ICMPGE:
            case IF_ICMPGT:
            case IF_ICMPLE:
            case IF_ACMPEQ:
            case IF_ACMPNE:
            case PUTFIELD:
                type = Type.VOID_TYPE;
                break;
            default:
                throw new Error("Internal error.");
        }
        return recordFlow(type, insn, value1, value2);
    }

    @Override
    public FlowValue ternaryOperation(
            final AbstractInsnNode insn,
            final FlowValue value1,
            final FlowValue value2,
            final FlowValue value3) {
        return recordFlow(Type.VOID_TYPE, insn, value1, value2, value3);
    }

    @Override
    public FlowValue naryOperation(
            final AbstractInsnNode insn, final List<? extends FlowValue> values) {
        int opcode = insn.getOpcode();
        Type type;
        switch (opcode) {
            case MULTIANEWARRAY:
                type = Type.getType(((MultiANewArrayInsnNode) insn).desc);
                break;
            case INVOKEDYNAMIC:
                type = Type.getReturnType(((InvokeDynamicInsnNode) insn).desc);
                break;
            default:
                type = Type.getReturnType(((MethodInsnNode) insn).desc);
                break;
        }
        return recordFlow(type, insn, values.toArray(new FlowValue[0]));
    }

    @Override
    public void returnOperation(
            final AbstractInsnNode insn, final FlowValue value, final FlowValue expected) {
        // Nothing to do.
    }

    @Override
    public FlowValue merge(final FlowValue value1, final FlowValue value2) {
        return value1.mergeWith(value2);
    }

    private FlowValue recordFlow(Type type, AbstractInsnNode insn, FlowValue... inputs) {
        FlowValue cached = cache.get(insn);
        if (cached == null) {
            cached = new FlowValue(type, insn, inputs);
            cache.put(insn, cached);
        } else {
            cached.mergeInputs(inputs);
        }
        return cached;
    }
}
