package au.mrcat.rivet.riscv;

import au.mrcat.rivet.nodes.RivetRootNode;

public record InstructionParseRequest(long pc, PrivilegedContext priv, RivetRootNode root) {
}
