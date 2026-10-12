package tlm.dev.lectern.pipeline.extract;

public sealed interface JarEra {

    record Bundler() implements JarEra {
    }

    record Simple() implements JarEra {
    }
}
