package au.mrcat.rivet.nodes;

import au.mrcat.rivet.riscv.Csr;

public abstract class RivetCsrwNode extends RivetInstructionNode {
    protected final int csr;

    protected RivetCsrwNode(int csr) {
        this.csr = csr;
    }

    public boolean doesChangePrivilegedContext() {
        return csr == Csr.MSTATUS
                || csr == Csr.MENVCFG
                || csr == Csr.SSTATUS
                || csr == Csr.SATP;
    }
}
