package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetNode;
import com.oracle.truffle.api.frame.VirtualFrame;

import java.util.List;

public class WithLockedDoubleNode extends RivetNode {
    @Children RivetNode[] instructions;
    final LoadDoubleReservedNode loadReserved;
    final StoreDoubleConditionalNode storeConditional;

    public WithLockedDoubleNode(LoadDoubleReservedNode loadReserved, RivetNode[] instructions, StoreDoubleConditionalNode storeConditional) {
        this.instructions = instructions;
        this.loadReserved = loadReserved;
        this.storeConditional = storeConditional;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        var ctx = currentLanguageContext();

        long address = loadReserved.address.executeLong(frame);
        long doubleWord = ctx.readLong(address);
        // This includes the temp zero register: AMO instructions need the value to be
        // loaded even when the result is discarded
        ctx.setRegister(loadReserved.rd, doubleWord);

        for (var instr : instructions) {
            instr.executeVoid(frame);
        }

        long storeAddress = storeConditional.address.executeLong(frame);
        long value = storeConditional.src.executeLong(frame);
        if (storeAddress == address) {
            ctx.writeLong(address, value);
            if (storeConditional.rd != 0) {
                ctx.setRegister(storeConditional.rd, 0);
            }
        } else {
            if (storeConditional.rd != 0) {
                ctx.setRegister(storeConditional.rd, 1);
            }
        }
    }
}
