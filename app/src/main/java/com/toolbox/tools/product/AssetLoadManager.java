package com.toolbox.tools.product;

import com.toolbox.tools.core.StableId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AssetLoadManager {
    public enum Kind {
        IMAGE, ICON, FONT, AUDIO, VIDEO, JSON, RAW
    }

    public static final class Descriptor {
        private final String id;
        private final Kind kind;
        private final long sizeBytes;
        private final String sha256;
        private boolean referenced;

        Descriptor(
                String id,
                Kind kind,
                long sizeBytes,
                String sha256
        ) {
            this.id=id;
            this.kind=kind;
            this.sizeBytes=sizeBytes;
            this.sha256=sha256;
        }

        public String id(){return id;}
        public Kind kind(){return kind;}
        public long sizeBytes(){return sizeBytes;}
        public String sha256(){return sha256;}
        public boolean referenced(){return referenced;}
    }

    public static final class LoadPlan {
        private final boolean viewportRequired;
        private final boolean thumbnailFirst;
        private final boolean streaming;
        private final int targetWidth;
        private final int targetHeight;
        private final int chunkBytes;
        private final long memoryBudgetBytes;

        LoadPlan(
                boolean viewportRequired,
                boolean thumbnailFirst,
                boolean streaming,
                int targetWidth,
                int targetHeight,
                int chunkBytes,
                long memoryBudgetBytes
        ){
            this.viewportRequired=viewportRequired;
            this.thumbnailFirst=thumbnailFirst;
            this.streaming=streaming;
            this.targetWidth=targetWidth;
            this.targetHeight=targetHeight;
            this.chunkBytes=chunkBytes;
            this.memoryBudgetBytes=memoryBudgetBytes;
        }

        public boolean viewportRequired(){return viewportRequired;}
        public boolean thumbnailFirst(){return thumbnailFirst;}
        public boolean streaming(){return streaming;}
        public int targetWidth(){return targetWidth;}
        public int targetHeight(){return targetHeight;}
        public int chunkBytes(){return chunkBytes;}
        public long memoryBudgetBytes(){return memoryBudgetBytes;}
    }

    public static final class Audit {
        private final Set<String> unused;
        private final Set<String> duplicateDigests;

        Audit(Set<String> unused,Set<String> duplicateDigests){
            this.unused=Collections.unmodifiableSet(unused);
            this.duplicateDigests=Collections.unmodifiableSet(duplicateDigests);
        }

        public Set<String> unused(){return unused;}
        public Set<String> duplicateDigests(){return duplicateDigests;}
        public boolean isPass(){return duplicateDigests.isEmpty();}
    }

    private final Map<String,Descriptor> assets=new LinkedHashMap<>();

    public synchronized void register(
            String id,
            Kind kind,
            long sizeBytes,
            String sha256
    ){
        String stable=StableId.require(id,"assetId");
        if(kind==null||sizeBytes<0
                ||sha256==null
                ||!sha256.matches("[0-9a-f]{64}")){
            throw new IllegalArgumentException("asset descriptor invalid");
        }
        assets.put(stable,new Descriptor(
                stable,kind,sizeBytes,sha256
        ));
    }

    public synchronized void reference(String id){
        Descriptor asset=require(id);
        asset.referenced=true;
    }

    public synchronized LoadPlan plan(
            String id,
            int viewportWidth,
            int viewportHeight,
            boolean inViewport
    ){
        Descriptor asset=require(id);
        int width=Math.max(16,Math.min(2048,viewportWidth));
        int height=Math.max(16,Math.min(2048,viewportHeight));
        boolean image=asset.kind==Kind.IMAGE
                ||asset.kind==Kind.ICON;
        boolean stream=asset.kind==Kind.AUDIO
                ||asset.kind==Kind.VIDEO;
        long pixelBudget=Math.min(
                24L*1024L*1024L,
                (long)width*height*4L
        );
        return new LoadPlan(
                true,
                image,
                stream,
                inViewport?width:Math.min(256,width),
                inViewport?height:Math.min(256,height),
                stream?512*1024:0,
                pixelBudget
        );
    }

    public synchronized Descriptor require(String id){
        Descriptor asset=assets.get(
                StableId.require(id,"assetId")
        );
        if(asset==null)throw new IllegalArgumentException(
                "asset unavailable"
        );
        return asset;
    }

    public synchronized Audit audit(){
        LinkedHashSet<String> unused=new LinkedHashSet<>();
        LinkedHashSet<String> duplicates=new LinkedHashSet<>();
        Map<String,String> owner=new LinkedHashMap<>();
        for(Descriptor asset:assets.values()){
            if(!asset.referenced)unused.add(asset.id);
            String previous=owner.put(asset.sha256,asset.id);
            if(previous!=null){
                duplicates.add(previous);
                duplicates.add(asset.id);
            }
        }
        return new Audit(unused,duplicates);
    }

    public synchronized List<Descriptor> all(){
        return Collections.unmodifiableList(
                new ArrayList<>(assets.values())
        );
    }
}
