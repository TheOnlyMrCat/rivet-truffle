package au.mrcat.rivet.nodes.priv;

import au.mrcat.rivet.nodes.RivetDivergentNode;
import au.mrcat.rivet.riscv.ExceptionCause;
import au.mrcat.rivet.runtime.RiscvInstructionFenceException;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class VirtualMemoryFence extends RivetDivergentNode {
    private final int instruction;
    private final long pc;
    private final short instret;

    public VirtualMemoryFence(int instruction, long pc, short instret) {
        this.instruction = instruction;
        this.pc = pc;
        this.instret = instret;
    }

    @Override
    public int executeDivergent(VirtualFrame frame) {
        var ctx = currentLanguageContext();
        if (ctx.privilegedState.shouldTrapSatpAccess()) {
            throw new RiscvTrapException(ExceptionCause.IllegalInstruction, pc, Integer.toUnsignedLong(instruction), instret);
        }
        ctx.privilegedState.fenceVirtualMemory();
        // We don't have any concept of 'address spaces' in our instruction cache, so we need to flush the whole thing
        throw new RiscvInstructionFenceException(pc + 4, instret);
    }

    @Override
    public Long[] callTargetContinuations() {
        return new Long[0];
    }
}
