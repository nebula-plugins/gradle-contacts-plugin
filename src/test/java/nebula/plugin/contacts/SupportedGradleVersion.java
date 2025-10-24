package nebula.plugin.contacts;

enum SupportedGradleVersion {
    MIN("9.0.0"), MAX("9.1.0");
    public final String version;

    SupportedGradleVersion(String version) {
        this.version = version;
    }
}
