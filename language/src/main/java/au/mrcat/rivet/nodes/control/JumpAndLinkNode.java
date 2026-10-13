package au.mrcat.rivet.nodes.control;

import au.mrcat.rivet.nodes.RivetDivergentNode;
import au.mrcat.rivet.nodes.RivetInstructionNode;
import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.riscv.PrivilegedContext;
import au.mrcat.rivet.runtime.RiscvIndirectJumpException;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class JumpAndLinkNode extends RivetDivergentNode {
    @Child RivetOpNode targetPc;
    @Child
    RivetInstructionNode link;

    public JumpAndLinkNode(RivetOpNode targetPc, RivetInstructionNode link) {
        this.targetPc = targetPc;
        this.link = link;
    }

    @Override
    public int executeDivergent(VirtualFrame frame, long basePc, PrivilegedContext priv) throws RiscvTrapException {
        long targetPc = 0;
        try {
            targetPc = this.targetPc.executeLong(frame, basePc, priv);
        } catch (au.mrcat.rivet.runtime.RiscvTrapException e) {
            throw new RuntimeException(e);
        }
        try {
            link.executeVoid(frame, basePc, priv);
        } catch (au.mrcat.rivet.runtime.RiscvTrapException e) {
            throw new RuntimeException(e);
        }
        throw new RiscvIndirectJumpException(targetPc & ~0b1);
    }

    @Override
    public Long[] callTargetContinuations() {
        // Deliberately split call targets at jal instructions, even when the target is a constant
        return new Long[0];
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("JumpAndLinkNode{");
        sb.append("targetPc=").append(targetPc);
        sb.append(", link=").append(link);
        sb.append('}');
        return sb.toString();
    }
}
