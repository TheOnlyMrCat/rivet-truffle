package au.mrcat.rivet.nodes.arith;

import au.mrcat.rivet.nodes.RivetOpNode;
import au.mrcat.rivet.riscv.PrivilegedContext;
import com.oracle.truffle.api.frame.VirtualFrame;

import java.math.BigInteger;

public class MultiplyHighSignedUnsignedNode extends RivetOpNode {
    @Child RivetOpNode multiplicand;
    @Child RivetOpNode multiplier;

    public MultiplyHighSignedUnsignedNode(RivetOpNode multiplicand, RivetOpNode multiplier) {
        this.multiplicand = multiplicand;
        this.multiplier = multiplier;
    }

    @Override
    public long executeLong(VirtualFrame frame, long basePc, PrivilegedContext priv) {
        // If there's a way to do this with multiplyHigh and unsignedMultiplyHigh I don't know it.
        // Signed multiplicand
        BigInteger multiplicand = BigInteger.valueOf(this.multiplicand.executeLong(frame, basePc, priv));

        // Unsigned multiplier
        BigInteger multiplier;
        long multiplierSigned = this.multiplier.executeLong(frame, basePc, priv);
        // From OpenJDK: https://github.com/AdoptOpenJDK/openjdk-jdk11/blob/master/src/java.base/share/classes/java/lang/Long.java#L241-L252
        if (multiplierSigned >= 0) {
            multiplier = BigInteger.valueOf(multiplierSigned);
        } else {
            int upper = (int) (multiplierSigned >>> 32);
            int lower = (int) multiplierSigned;

            // return (upper << 32) + lower
            multiplier = (BigInteger.valueOf(Integer.toUnsignedLong(upper))).shiftLeft(32).
                    add(BigInteger.valueOf(Integer.toUnsignedLong(lower)));
        }

        return multiplicand.multiply(multiplier).shiftRight(64).longValue();
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("MultiplyHighSignedUnsignedNode{");
        sb.append("multiplicand=").append(multiplicand);
        sb.append(", multiplier=").append(multiplier);
        sb.append('}');
        return sb.toString();
    }
}
