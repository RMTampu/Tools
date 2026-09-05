package com.toolbox.tools.product;

import com.toolbox.tools.core.StableId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class StateVariantEngine {
    public enum Layer { STATE, ORIENTATION, THEME, DATA }

    private final Map<String,Map<String,String>> normal=new LinkedHashMap<>();
    private final Map<String,Map<Layer,Map<String,Map<String,String>>>> layers=new LinkedHashMap<>();

    public synchronized void setNormal(String objectId,String propertyId,String value){
        String o=StableId.require(objectId,"objectId"),p=StableId.require(propertyId,"propertyId");
        normal.computeIfAbsent(o,k->new LinkedHashMap<>()).put(p,Objects.requireNonNull(value,"value"));
    }

    public synchronized void setStateOverride(String objectId,String stateId,String propertyId,String value){
        setLayerOverride(objectId,Layer.STATE,stateId,propertyId,value);
    }

    public synchronized void setLayerOverride(String objectId,Layer layer,String variantId,String propertyId,String value){
        String o=StableId.require(objectId,"objectId"),v=StableId.require(variantId,"variantId"),p=StableId.require(propertyId,"propertyId");
        layers.computeIfAbsent(o,k->new LinkedHashMap<>())
                .computeIfAbsent(Objects.requireNonNull(layer,"layer"),k->new LinkedHashMap<>())
                .computeIfAbsent(v,k->new LinkedHashMap<>()).put(p,Objects.requireNonNull(value,"value"));
    }

    public synchronized void resetState(String objectId,String stateId){
        resetLayer(objectId,Layer.STATE,stateId);
    }

    public synchronized void resetLayer(String objectId,Layer layer,String variantId){
        Map<Layer,Map<String,Map<String,String>>> by=layers.get(StableId.require(objectId,"objectId"));
        if(by!=null&&by.get(layer)!=null)by.get(layer).remove(StableId.require(variantId,"variantId"));
    }

    public synchronized Map<String,String> resolve(String objectId,String stateId){
        return resolve(objectId,stateId,null,null,null);
    }

    public synchronized Map<String,String> resolve(String objectId,String stateId,String orientationId,String themeId,String dataStateId){
        String object=StableId.require(objectId,"objectId");
        LinkedHashMap<String,String> out=new LinkedHashMap<>(normal.getOrDefault(object,Collections.emptyMap()));
        apply(out,object,Layer.ORIENTATION,orientationId);
        apply(out,object,Layer.THEME,themeId);
        apply(out,object,Layer.DATA,dataStateId);
        apply(out,object,Layer.STATE,stateId);
        return Collections.unmodifiableMap(out);
    }

    private void apply(Map<String,String> out,String object,Layer layer,String variant){
        if(variant==null||variant.trim().isEmpty())return;
        Map<Layer,Map<String,Map<String,String>>> byLayer=layers.get(object);
        if(byLayer==null)return;
        Map<String,Map<String,String>> byVariant=byLayer.get(layer);
        if(byVariant==null)return;
        out.putAll(byVariant.getOrDefault(StableId.require(variant,"variantId"),Collections.emptyMap()));
    }
}
