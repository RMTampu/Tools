package com.toolbox.tools.product;

import com.toolbox.tools.core.StableId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RenderDiagnostics {
    public static final class Sample {
        private final int visibleNodes;
        private final int translucentLayers;
        private final int activeAnimations;
        private final long frameTimeMs;

        Sample(
                int visibleNodes,
                int translucentLayers,
                int activeAnimations,
                long frameTimeMs
        ) {
            this.visibleNodes=visibleNodes;
            this.translucentLayers=translucentLayers;
            this.activeAnimations=activeAnimations;
            this.frameTimeMs=frameTimeMs;
        }

        public int visibleNodes(){return visibleNodes;}
        public int translucentLayers(){return translucentLayers;}
        public int activeAnimations(){return activeAnimations;}
        public long frameTimeMs(){return frameTimeMs;}
        public int complexityScore(){
            return visibleNodes
                    + translucentLayers*8
                    + activeAnimations*12
                    + (int)Math.min(100,frameTimeMs);
        }
        public boolean withinBudget(){
            return visibleNodes<=240
                    && translucentLayers<=8
                    && activeAnimations<=16
                    && frameTimeMs<=50;
        }
    }

    private final Map<String,Sample> samples=new LinkedHashMap<>();

    public synchronized void record(
            String screenId,
            int visibleNodes,
            int translucentLayers,
            int activeAnimations,
            long frameTimeMs
    ){
        String id=StableId.require(screenId,"screenId");
        if(visibleNodes<0||translucentLayers<0
                ||activeAnimations<0||frameTimeMs<0){
            throw new IllegalArgumentException("render sample invalid");
        }
        samples.put(
                id,
                new Sample(
                        visibleNodes,
                        translucentLayers,
                        activeAnimations,
                        frameTimeMs
                )
        );
    }

    public synchronized Sample sample(String screenId){
        return samples.get(
                StableId.require(screenId,"screenId")
        );
    }

    public synchronized boolean allWithinBudget(){
        if(samples.isEmpty())return false;
        for(Sample sample:samples.values()){
            if(!sample.withinBudget())return false;
        }
        return true;
    }

    public synchronized Map<String,Sample> snapshot(){
        return Collections.unmodifiableMap(
                new LinkedHashMap<>(samples)
        );
    }
}
