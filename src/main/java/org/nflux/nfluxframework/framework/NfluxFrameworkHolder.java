package org.nflux.nfluxframework.framework;





public class NfluxFrameworkHolder {

    private static NfluxFramework framework;

    public void setFramework(NfluxFramework framework) {
        NfluxFrameworkHolder.framework = framework;
    }

    public static NfluxFramework getFramework() {
        if (framework == null) {
            throw new IllegalStateException("NfluxFramework has not been initialized. " +
                    "Ensure @EnableNfluxFramework is used and Spring context is loaded.");
        }
        return framework;
    }
}
