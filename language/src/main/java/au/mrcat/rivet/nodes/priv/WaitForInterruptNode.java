package au.mrcat.rivet.nodes.priv;

import au.mrcat.rivet.nodes.RivetInstructionNode;
import au.mrcat.rivet.riscv.ExceptionCause;
import au.mrcat.rivet.riscv.PrivilegedContext;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class WaitForInterruptNode extends RivetInstructionNode {
    private final long pcOffset;
    private final long instruction;
    private final short instret;

    public WaitForInterruptNode(long instruction, long pcOffset, short instret) {
        this.instruction = instruction;
        this.pcOffset = pcOffset;
        this.instret = instret;
    }

    @Override
    public void executeVoid(VirtualFrame frame, long basePc, PrivilegedContext priv) {
        var ctx = currentLanguageContext();

        ctx.privilegedState.checkForInterrupts(basePc + pcOffset);
        if (priv.shouldTrapWait()) {
            throw new RiscvTrapException(ExceptionCause.IllegalInstruction, basePc + pcOffset, instruction, instret);
        }
        ctx.privilegedState.waitForInterrupts();
        ctx.privilegedState.checkForInterrupts(basePc + pcOffset);
    }
}
