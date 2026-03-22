package au.mrcat.rivet.nodes;

import au.mrcat.rivet.nodes.data.EncodedInstructionNode;
import au.mrcat.rivet.riscv.ExceptionCause;
import au.mrcat.rivet.riscv.Opcode;
import au.mrcat.rivet.riscv.RegisterState;
import au.mrcat.rivet.runtime.RiscvExitException;
import au.mrcat.rivet.runtime.RiscvJumpException;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.BlockNode;

import java.util.Objects;

public class RiscvDispatchNode extends RivetNode implements BlockNode.ElementExecutor<RivetNode> {
    @Child BlockNode<RivetNode> instructions;
    private final long baseAddress;

    public RiscvDispatchNode(RivetNode[] instructions, long baseAddress) {
        this.instructions = BlockNode.create(instructions, this);
        this.baseAddress = baseAddress;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        instructions.executeVoid(frame, BlockNode.NO_ARGUMENT);
        throw new RiscvJumpException(this.baseAddress + 4L * instructions.getElements().length);
    }

    @Override
    public void executeVoid(VirtualFrame frame, RivetNode node, int index, int argument) {
        long pc = 4L * index;
        if (Objects.requireNonNull(node) instanceof EncodedInstructionNode instructionNode) {
            int instruction = instructionNode.instruction;
            System.err.printf("Executing %08x @ 0x%08x\n", instruction, this.baseAddress + pc);
            int opcode = instruction & 0x7f;
            switch (opcode) {
                case Opcode.MISC_MEM -> handleMiscMem(frame, index, instruction);
                case Opcode.SYSTEM -> handleSystem(frame, instruction);
                case Opcode.LOAD, Opcode.OP_IMM, Opcode.AUIPC, Opcode.OP_IMM_32,
                     Opcode.STORE, Opcode.OP, Opcode.LUI, Opcode.OP_32,
                     Opcode.BRANCH, Opcode.JAL, Opcode.JALR ->
                        throw new IllegalStateException("Instruction should have been parsed");
                default -> throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
            }
        } else {
            node.executeVoid(frame);
        }
        this.currentLanguageContext().dumpRegisterState();
    }

    void handleMiscMem(VirtualFrame frame, int i, int instruction) {
        var ctx = currentLanguageContext();

        int rd = (instruction >> 7) & 0b11111;
        int funct3 = (instruction >> 12) & 0b111;
        int rs1 = (instruction >> 15) & 0b11111;
        long immUnsigned = instruction >>> 20;

        if (rd != 0 || rs1 != 0) {
            throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
        }

        switch (funct3) {
            case Opcode.MiscMem.FENCE -> {}
            case Opcode.MiscMem.FENCE_I -> throw new RiscvJumpException(this.baseAddress + 4L * (i + 1));
            default -> throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
        }
    }

    void handleSystem(VirtualFrame frame, int instruction) {
        var ctx = currentLanguageContext();

        int rd = (instruction >> 7) & 0b11111;
        int funct3 = (instruction >> 12) & 0b111;
        int rs1 = (instruction >> 15) & 0b11111;
        int funct12 = instruction >>> 20;

        switch (funct3) {
            case Opcode.System.PRIV -> {
                switch (funct12) {
                    case Opcode.Priv.EBREAK -> throw new RiscvTrapException(ExceptionCause.Breakpoint);
                    case Opcode.Priv.ECALL -> {
                        switch ((int) ctx.getRegister(RegisterState.A7)) {
                            case 93 -> throw new RiscvExitException(ctx.getRegister(RegisterState.A0));
                            default -> throw new RuntimeException(String.format("Unimplemented syscall: %d", ctx.getRegister(RegisterState.A7)));
                        }
                    }
                    case Opcode.Priv.WFI -> {}
                    default -> throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
                }
            }
            default -> throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
        }
    }
}
