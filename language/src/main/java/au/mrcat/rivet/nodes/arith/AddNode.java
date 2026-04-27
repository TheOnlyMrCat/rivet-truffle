package au.mrcat.rivet.nodes.arith;

import au.mrcat.rivet.nodes.RivetOpNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class AddNode extends RivetOpNode {
    @Child RivetOpNode addend1;
    @Child RivetOpNode addend2;

    public AddNode(RivetOpNode addend1, RivetOpNode addend2) {
        this.addend1 = addend1;
        this.addend2 = addend2;
    }

    @Override
    public long executeLong(VirtualFrame frame) {
        return addend1.executeLong(frame) + addend2.executeLong(frame);
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("AddNode{");
        sb.append("addend1=").append(addend1);
        sb.append(", addend2=").append(addend2);
        sb.append('}');
        return sb.toString();
    }
}
