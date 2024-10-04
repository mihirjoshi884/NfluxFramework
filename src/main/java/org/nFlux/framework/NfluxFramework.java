package org.nFlux.framework;

import org.nFlux.config.NfluxConfig;
import org.nFlux.pojo.API;

import java.util.ArrayList;
import java.util.List;

public class NfluxFramework {

    List<API> executableApi = new ArrayList();
    private static NfluxConfig config;

    public NfluxFramework(NfluxConfig config){
        this.config = config;
    }



    public void executeGetAPIs(){

    }

    public void executePostAPIs(){


    }

    public void executePutAPIs(){


    }

    public void executeDeleteAPIs(){


    }
}
