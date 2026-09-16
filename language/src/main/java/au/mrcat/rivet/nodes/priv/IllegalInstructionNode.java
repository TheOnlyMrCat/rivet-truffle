package au.mrcat.rivet.nodes.priv;

import au.mrcat.rivet.nodes.RivetTrapNode;
import au.mrcat.rivet.riscv.ExceptionCause;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class IllegalInstructionNode extends RivetTrapNode {
    private final int instruction;
    private final long pcOffset;

    public IllegalInstructionNode(int instruction, long pcOffset, short instret) {
        super(instret);
        this.instruction = instruction;
        this.pcOffset = pcOffset;
    }

    @Override
    public int executeDivergent(VirtualFrame frame, long basePc) {
        throw new RiscvTrapException(ExceptionCause.IllegalInstruction, basePc + pcOffset, Integer.toUnsignedLong(instruction), instret);
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("IllegalInstructionNode{");
        sb.append("instruction=").append(instruction);
        sb.append(", pc=").append(pcOffset);
        sb.append('}');
        return sb.toString();
    }
}
