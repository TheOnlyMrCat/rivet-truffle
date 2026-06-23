package au.mrcat.rivet.nodes;

public abstract class RivetTrapNode extends RivetDivergentNode {
    protected final short instret;

    protected RivetTrapNode(short instret) {
        this.instret = instret;
    }

    @Override
    public Long[] callTargetContinuations() {
        return new Long[0];
    }
}
