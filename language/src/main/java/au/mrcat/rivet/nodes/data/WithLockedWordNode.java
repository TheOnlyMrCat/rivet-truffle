package au.mrcat.rivet.nodes.data;

import au.mrcat.rivet.nodes.RivetNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class WithLockedWordNode extends RivetNode {
    @Children RivetNode[] instructions;
    final LoadWordReservedNode loadReserved;
    final StoreWordConditionalNode storeConditional;

    public WithLockedWordNode(LoadWordReservedNode loadReserved, RivetNode[] instructions, StoreWordConditionalNode storeConditional) {
        this.instructions = instructions;
        this.loadReserved = loadReserved;
        this.storeConditional = storeConditional;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        var ctx = currentLanguageContext();

        long address = loadReserved.address.executeLong(frame);
        int word = ctx.readInt(address);
        // This includes the temp zero register: AMO instructions need the value to be
        // loaded even when the result is discarded
        ctx.setRegister(loadReserved.rd, word);

        for (var instr : instructions) {
            instr.executeVoid(frame);
        }

        long storeAddress = storeConditional.address.executeLong(frame);
        int value = (int) storeConditional.src.executeLong(frame);
        if (storeAddress == address) {
            ctx.writeInt(address, value);
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
