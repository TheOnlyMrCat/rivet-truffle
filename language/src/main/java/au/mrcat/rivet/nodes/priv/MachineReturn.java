package au.mrcat.rivet.nodes.priv;

import au.mrcat.rivet.RivetContext;
import au.mrcat.rivet.nodes.RivetDivergentNode;
import au.mrcat.rivet.riscv.ExceptionCause;
import au.mrcat.rivet.riscv.PrivilegeMode;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class MachineReturn extends RivetDivergentNode {
    private final int instruction;
    private final long pc;
    private final short instret;

    public MachineReturn(int instruction, long pc, short instret) {
        this.instruction = instruction;
        this.pc = pc;
        this.instret = instret;
    }

    @Override
    public long executeDivergent(VirtualFrame frame) {
        var ctx = RivetContext.get(this);
        if (ctx.privilegedState.currentMode() != PrivilegeMode.Machine) {
            throw new RiscvTrapException(ExceptionCause.IllegalInstruction, pc, instruction, instret);
        }
        ctx.privilegedState.stepPerformanceCounters(instret);
        return ctx.privilegedState.handleMret();
    }

    @Override
    public Long[] callTargetContinuations() {
        return new Long[0];
    }
}
