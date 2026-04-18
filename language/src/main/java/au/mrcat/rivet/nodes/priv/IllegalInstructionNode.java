package au.mrcat.rivet.nodes.priv;

import au.mrcat.rivet.nodes.RivetDivergentNode;
import au.mrcat.rivet.nodes.RivetNode;
import au.mrcat.rivet.riscv.ExceptionCause;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class IllegalInstructionNode extends RivetDivergentNode {
    private final int instruction;
    private final long pc;

    public IllegalInstructionNode(int instruction, long pc) {
        this.instruction = instruction;
        this.pc = pc;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        throw new RiscvTrapException(ExceptionCause.IllegalInstruction, pc);
    }
}
