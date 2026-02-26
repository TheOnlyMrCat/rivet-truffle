package au.mrcat.launcher;

import au.mrcat.rivet.RivetLanguage;
import au.mrcat.rivet.nodes.RiscvDispatchNode;
import au.mrcat.rivet.nodes.RivetRootNode;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.impl.FrameWithoutBoxing;

public class Main {
    static void main() {
        byte[] testProgram = new byte[] {
                0x13, 0x05, (byte) 0xa5, 0x00, // addi a0, a0, 10
                0x13, 0x05, (byte) 0xa5, 0x00, // addi a0, a0, 10
                0x6f, 0x00, 0x00, 0x00, // j 0
        };

        int[] testInstructions = new int[] {
                0x00a50513, // addi a0, a0, 10
                0x00a50013, // addi zero, a0, 10
                0x0000006f, // j 0
        };

        RivetLanguage language = new RivetLanguage();
        RiscvDispatchNode dispatchNode = new RiscvDispatchNode(testInstructions);
        RivetRootNode rootNode = new RivetRootNode(language, new FrameDescriptor(), dispatchNode);

        rootNode.execute(new FrameWithoutBoxing(FrameDescriptor.newBuilder().build(), new Object[] {}));
    }
}
