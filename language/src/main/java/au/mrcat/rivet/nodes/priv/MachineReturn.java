package au.mrcat.rivet.nodes.priv;

import au.mrcat.rivet.RivetContext;
import au.mrcat.rivet.nodes.RivetNode;
import au.mrcat.rivet.riscv.ExceptionCause;
import au.mrcat.rivet.riscv.PrivilegeMode;
import au.mrcat.rivet.runtime.RiscvJumpException;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class MachineReturn extends RivetNode {
    private final int instruction;
    private final long pc;
    private final short instret;

    public MachineReturn(int instruction, long pc, short instret) {
        this.instruction = instruction;
        this.pc = pc;
        this.instret = instret;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        var ctx = RivetContext.get(this);
        if (ctx.privilegedState.currentMode() != PrivilegeMode.Machine) {
            throw new RiscvTrapException(ExceptionCause.IllegalInstruction, pc, instruction, instret);
        }
        ctx.privilegedState.stepPerformanceCounters(instret);
        throw new RiscvJumpException(ctx.privilegedState.handleMret());
    }
}
