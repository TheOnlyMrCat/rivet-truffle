package au.mrcat.rivet.nodes;

import au.mrcat.rivet.riscv.PrivilegedContext;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class RivetOpNode extends RivetInstructionNode {
    public abstract long executeLong(VirtualFrame frame, long basePc, PrivilegedContext priv) throws RiscvTrapException;

    @Override
    public void executeVoid(VirtualFrame frame, long basePc, PrivilegedContext priv) throws RiscvTrapException {
        executeLong(frame, basePc, priv);
    }
}
