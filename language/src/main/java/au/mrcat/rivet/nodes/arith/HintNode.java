package au.mrcat.rivet.nodes.arith;

import au.mrcat.rivet.nodes.RivetInstructionNode;
import au.mrcat.rivet.riscv.PrivilegedContext;
import com.oracle.truffle.api.frame.VirtualFrame;

public class HintNode extends RivetInstructionNode {
    private final int instruction;

    public HintNode(int instruction) {
        this.instruction = instruction;
    }

    @Override
    public void executeVoid(VirtualFrame frame, long basePc, PrivilegedContext priv) {
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("HintNode{");
        sb.append("instruction=").append(instruction);
        sb.append('}');
        return sb.toString();
    }
}
