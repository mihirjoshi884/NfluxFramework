package org.nFlux.framework;

public class NfluxFrameworkHolder {

    private static NfluxFramework nfluxFramework;

    public static void setNfluxFramework(NfluxFramework nfluxFramework){
        NfluxFrameworkHolder.nfluxFramework = nfluxFramework;

    }

    public static NfluxFramework getFramework() {
        return nfluxFramework;
    }
}
