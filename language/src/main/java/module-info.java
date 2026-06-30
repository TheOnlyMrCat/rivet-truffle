module au.mrcat.rivet {
    requires java.base;
    requires net.fornwall.jelf;
    requires org.graalvm.polyglot;
    requires org.graalvm.truffle;
    provides com.oracle.truffle.api.provider.TruffleLanguageProvider with au.mrcat.rivet.RivetLanguageProvider;
}