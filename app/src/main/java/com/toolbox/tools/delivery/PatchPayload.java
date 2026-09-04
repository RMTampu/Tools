package com.toolbox.tools.delivery;

import com.toolbox.tools.core.StableId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class PatchPayload {
    public static final int MAX_RAW_BYTES = 1024 * 1024;
    private final Map<String,String> upserts;
    private final Set<String> deletes;
    private final String canonical;
    private final String sha256;

    public PatchPayload(Map<String,String> upserts, Set<String> deletes) {
        if (upserts == null || deletes == null) throw new NullPointerException("patch payload");
        LinkedHashMap<String,String> u=new LinkedHashMap<>();
        LinkedHashSet<String> d=new LinkedHashSet<>();
        int raw=0;
        for (Map.Entry<String,String> e:new TreeMap<>(upserts).entrySet()) {
            String id=StableId.require(e.getKey(),"resourceId");
            rejectProtected(id);
            String value=java.util.Objects.requireNonNull(e.getValue(),"payload");
            raw+=value.getBytes(StandardCharsets.UTF_8).length;
            if(raw>MAX_RAW_BYTES) throw new IllegalArgumentException("patch payload exceeds budget");
            u.put(id,value);
        }
        for(String item:new TreeSet<>(deletes)) {
            String id=StableId.require(item,"resourceId");
            rejectProtected(id);
            if(u.containsKey(id)) throw new IllegalArgumentException("resource cannot be upserted and deleted");
            d.add(id);
        }
        this.upserts=Collections.unmodifiableMap(u);
        this.deletes=Collections.unmodifiableSet(d);
        this.canonical=canonicalize(u,d);
        this.sha256=sha256(canonical);
    }

    public Map<String,String> upserts(){return upserts;}
    public Set<String> deletes(){return deletes;}
    public String canonical(){return canonical;}
    public String sha256(){return sha256;}

    private static String canonicalize(Map<String,String> upserts,Set<String> deletes){
        StringBuilder out=new StringBuilder("TBX_PATCH_PAYLOAD_V1\n");
        for(Map.Entry<String,String> e:new TreeMap<>(upserts).entrySet()){
            byte[] bytes=e.getValue().getBytes(StandardCharsets.UTF_8);
            out.append("upsert|").append(e.getKey()).append('|').append(bytes.length)
                    .append('|').append(sha256(e.getValue())).append('\n');
        }
        for(String id:new TreeSet<>(deletes)) out.append("delete|").append(id).append('\n');
        return out.toString();
    }

    private static void rejectProtected(String id){
        if(id.startsWith("kernel.")||id.startsWith("recovery.")
                ||id.startsWith("safety.")||id.startsWith("security.")) {
            throw new IllegalArgumentException("protected resource");
        }
    }

    static String sha256(String value){
        try{
            byte[] bytes=MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out=new StringBuilder();
            for(byte item:bytes) {
                out.append(String.format(java.util.Locale.ROOT,"%02x",item));
            }
            return out.toString();
        }catch(Exception error){throw new IllegalStateException(error);}
    }
}
