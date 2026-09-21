package au.mrcat.rivet.nodes;

import au.mrcat.rivet.riscv.PrivilegedContext;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class RivetOpNode extends RivetInstructionNode {
    public abstract long executeLong(VirtualFrame frame, long basePc, PrivilegedContext priv);

    @Override
    public void executeVoid(VirtualFrame frame, long basePc, PrivilegedContext priv) {
        executeLong(frame, basePc, priv);
    }
}
