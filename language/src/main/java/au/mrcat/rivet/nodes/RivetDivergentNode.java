package au.mrcat.rivet.nodes;

import au.mrcat.rivet.riscv.PrivilegedContext;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class RivetDivergentNode extends RivetInstructionNode {
    public abstract int executeDivergent(VirtualFrame frame, long basePc, PrivilegedContext priv) throws RiscvTrapException;
    public abstract Long[] callTargetContinuations();

    @Override
    public void executeVoid(VirtualFrame frame, long basePc, PrivilegedContext priv) throws RiscvTrapException {
        executeDivergent(frame, basePc, priv);
    }
}
