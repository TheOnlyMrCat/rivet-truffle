package au.mrcat.rivet.nodes.priv;

import au.mrcat.rivet.RivetContext;
import au.mrcat.rivet.nodes.RivetDivergentNode;
import au.mrcat.rivet.riscv.ExceptionCause;
import au.mrcat.rivet.runtime.RiscvIndirectJumpException;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class SupervisorReturn extends RivetDivergentNode {
    private final int instruction;
    private final long pcOffset;
    private final short instret;

    public SupervisorReturn(int instruction, long pcOffset, short instret) {
        this.instruction = instruction;
        this.pcOffset = pcOffset;
        this.instret = instret;
    }

    @Override
    public int executeDivergent(VirtualFrame frame, long basePc) {
        var ctx = RivetContext.get(this);
        if (ctx.privilegedState.shouldTrapSret()) {
            throw new RiscvTrapException(ExceptionCause.IllegalInstruction, basePc + pcOffset, instruction, instret);
        }
        throw new RiscvIndirectJumpException(ctx.privilegedState.handleSret());
    }

    @Override
    public Long[] callTargetContinuations() {
        return new Long[0];
    }
}
