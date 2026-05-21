package au.mrcat.rivet.nodes.priv;

import au.mrcat.rivet.RivetContext;
import au.mrcat.rivet.nodes.RivetNode;
import au.mrcat.rivet.runtime.RiscvJumpException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class MachineReturn extends RivetNode {
    private final int instruction;
    private final long pc;

    public MachineReturn(int instruction, long pc) {
        this.instruction = instruction;
        this.pc = pc;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        var ctx = RivetContext.get(this);
        throw new RiscvJumpException(ctx.privilegedState.handleMret());
    }
}
