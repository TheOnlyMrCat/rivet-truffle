package au.mrcat.rivet.nodes.priv;

import au.mrcat.rivet.nodes.RivetNode;
import au.mrcat.rivet.riscv.ExceptionCause;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class IllegalInstructionNode extends RivetNode {
    private final int instruction;

    public IllegalInstructionNode(int instruction) {
        this.instruction = instruction;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        throw new RiscvTrapException(ExceptionCause.IllegalInstruction);
    }
}
