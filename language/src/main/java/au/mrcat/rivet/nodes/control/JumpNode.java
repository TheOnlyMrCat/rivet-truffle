package au.mrcat.rivet.nodes.control;

import au.mrcat.rivet.nodes.RivetDivergentNode;
import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.nodes.arith.PcOffsetNode;
import au.mrcat.rivet.nodes.data.ConstantNode;
import au.mrcat.rivet.riscv.PrivilegedContext;
import au.mrcat.rivet.runtime.RiscvIndirectJumpException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class JumpNode extends RivetDivergentNode {
    @Child RivetOpNode targetPc;

    public JumpNode(RivetOpNode targetPc) {
        this.targetPc = targetPc;
    }

    @Override
    public int executeDivergent(VirtualFrame frame, long basePc, PrivilegedContext priv) {
        if (targetPc instanceof PcOffsetNode) {
            return 0;
        }
        throw new RiscvIndirectJumpException(targetPc.executeLong(frame, basePc, priv) & ~0b1);
    }

    @Override
    public Long[] callTargetContinuations() {
        if (targetPc instanceof PcOffsetNode offset) {
            return new Long[] {offset.getOffset() & ~0b1};
        }
        return new Long[0];
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("JumpNode{");
        sb.append("targetPc=").append(targetPc);
        sb.append('}');
        return sb.toString();
    }
}
