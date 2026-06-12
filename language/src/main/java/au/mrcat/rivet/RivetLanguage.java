package au.mrcat.rivet;

import au.mrcat.rivet.nodes.RivetRootNode;
import au.mrcat.rivet.parser.RivetParser;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import org.graalvm.options.*;

import java.util.List;

@TruffleLanguage.Registration(id = "rv64", name = "Rivet RV64I", byteMimeTypes = "application/x-elf-riscv")
public final class RivetLanguage extends TruffleLanguage<RivetContext> {
    private final OptionKey<Boolean> OPENSBI_FIRMWARE = new OptionKey<>(false);

    @Override
    protected RivetContext createContext(Env env) {
        return new RivetContext(env);
    }

    @Override
    protected OptionDescriptors getSourceOptionDescriptors() {
        return OptionDescriptors.create(List.of(
                OptionDescriptor.newBuilder(OPENSBI_FIRMWARE, "rv64.opensbi")
                        .category(OptionCategory.USER)
                        .stability(OptionStability.STABLE)
                        .help("Boot the given binary under the built-in OpenSBI firmware")
                        .build()
        ));
    }

    @Override
    protected CallTarget parse(ParsingRequest request) {
        var startupNode = RivetParser.loadProgramHeader(request.getSource().getBytes());
        if (request.getOptionValues().get(OPENSBI_FIRMWARE)) {
            startupNode.loadOpensbi();
        }
        return new RivetRootNode(this, startupNode).getCallTarget();
    }
}
