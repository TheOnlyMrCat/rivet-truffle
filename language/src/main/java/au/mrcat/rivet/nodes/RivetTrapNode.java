package au.mrcat.rivet.nodes;

public abstract class RivetTrapNode extends RivetNode {
    protected final short instret;

    protected RivetTrapNode(short instret) {
        this.instret = instret;
    }
}
