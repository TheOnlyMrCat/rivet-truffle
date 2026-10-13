package au.mrcat.rivet.nodes.priv;

import au.mrcat.rivet.nodes.RivetDivergentNode;
import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.nodes.data.GetRegisterNode;
import au.mrcat.rivet.riscv.ExceptionCause;
import au.mrcat.rivet.riscv.PrivilegedContext;
import au.mrcat.rivet.runtime.RiscvIndirectJumpException;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class VirtualMemoryFence extends RivetDivergentNode {
    @Child RivetOpNode asid;
    private final int instruction;
    private final long pcOffset;
    private final short instret;

    public VirtualMemoryFence(RivetOpNode asid, int instruction, long pcOffset, short instret) {
        this.asid = asid;
        this.instruction = instruction;
        this.pcOffset = pcOffset;
        this.instret = instret;
    }

    @Override
    public int executeDivergent(VirtualFrame frame, long basePc, PrivilegedContext priv) throws RiscvTrapException {
        var ctx = currentLanguageContext();
        if (priv.shouldTrapSatpAccess()) {
            throw new RiscvTrapException(ExceptionCause.IllegalInstruction, basePc + pcOffset, Integer.toUnsignedLong(instruction), instret);
        }
        if (asid instanceof GetRegisterNode) {
            try {
                ctx.privilegedState.fenceVirtualMemory((short) asid.executeLong(frame, basePc, priv));
            } catch (RiscvTrapException e) {
                throw new RuntimeException(e);
            }
        } else {
            ctx.privilegedState.fenceVirtualMemory();
        }
        throw new RiscvIndirectJumpException(basePc + pcOffset + 4);
    }

    @Override
    public Long[] callTargetContinuations() {
        return new Long[0];
    }
}
