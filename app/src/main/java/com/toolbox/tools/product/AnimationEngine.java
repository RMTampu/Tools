package com.toolbox.tools.product;

import com.toolbox.tools.core.StableId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AnimationEngine {
    public enum Kind { FADE, SLIDE, SCALE, ROTATE, PROPERTY }
    public enum Easing { LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT }
    public enum GroupMode { SEQUENCE, PARALLEL }

    public static final class Keyframe {
        private final float fraction;
        private final String value;
        public Keyframe(float fraction,String value){
            if(fraction<0f||fraction>1f)throw new IllegalArgumentException("fraction invalid");
            this.fraction=fraction;this.value=Objects.requireNonNull(value,"value");
        }
        public float fraction(){return fraction;} public String value(){return value;}
    }

    public static final class Track {
        private final String propertyId;
        private final List<Keyframe> keyframes;
        public Track(String propertyId,List<Keyframe> keyframes){
            this.propertyId=StableId.require(propertyId,"propertyId");
            if(keyframes==null||keyframes.size()<2)throw new IllegalArgumentException("keyframe insufficient");
            this.keyframes=Collections.unmodifiableList(new ArrayList<>(keyframes));
        }
        public String propertyId(){return propertyId;} public List<Keyframe> keyframes(){return keyframes;}
    }

    public static final class Animation {
        private final String id,triggerId;
        private final Kind kind;
        private final long durationMs,delayMs;
        private final Easing easing;
        private final List<Track> tracks;

        public Animation(String id,Kind kind,String triggerId,long durationMs,long delayMs,Easing easing){
            this(id,kind,triggerId,durationMs,delayMs,easing,Collections.emptyList());
        }

        public Animation(String id,Kind kind,String triggerId,long durationMs,long delayMs,Easing easing,List<Track> tracks){
            this.id=StableId.require(id,"animationId");this.kind=Objects.requireNonNull(kind,"kind");
            this.triggerId=StableId.require(triggerId,"triggerId");
            if(durationMs<1||durationMs>60000||delayMs<0||delayMs>60000)throw new IllegalArgumentException("waktu animasi di luar batas");
            this.durationMs=durationMs;this.delayMs=delayMs;this.easing=Objects.requireNonNull(easing,"easing");
            this.tracks=Collections.unmodifiableList(new ArrayList<>(tracks));
        }
        public String id(){return id;} public Kind kind(){return kind;} public String triggerId(){return triggerId;}
        public long durationMs(){return durationMs;} public long delayMs(){return delayMs;} public Easing easing(){return easing;}
        public List<Track> tracks(){return tracks;}
    }

    public static final class Group {
        private final String id;private final GroupMode mode;private final List<String> animationIds;
        public Group(String id,GroupMode mode,List<String> animationIds){
            this.id=StableId.require(id,"groupId");this.mode=Objects.requireNonNull(mode,"mode");
            if(animationIds==null||animationIds.isEmpty())throw new IllegalArgumentException("animation group empty");
            List<String> ids=new ArrayList<>();for(String x:animationIds)ids.add(StableId.require(x,"animationId"));
            this.animationIds=Collections.unmodifiableList(ids);
        }
        public String id(){return id;} public GroupMode mode(){return mode;} public List<String> animationIds(){return animationIds;}
    }

    private final Map<String,Animation> animations=new LinkedHashMap<>();
    private final Map<String,Group> groups=new LinkedHashMap<>();

    public synchronized void register(Animation animation){
        Objects.requireNonNull(animation,"animation");if(animations.containsKey(animation.id()))throw new IllegalArgumentException("animasi duplikat");animations.put(animation.id(),animation);
    }
    public synchronized void registerGroup(Group group){
        Objects.requireNonNull(group,"group");
        for(String id:group.animationIds())if(!animations.containsKey(id))throw new IllegalArgumentException("group animation unavailable");
        groups.put(group.id(),group);
    }
    public synchronized List<Animation> all(){return Collections.unmodifiableList(new ArrayList<>(animations.values()));}
    public synchronized List<Group> groups(){return Collections.unmodifiableList(new ArrayList<>(groups.values()));}
    public synchronized long groupDuration(String groupId){
        Group g=groups.get(StableId.require(groupId,"groupId"));if(g==null)throw new IllegalArgumentException("group unavailable");
        long value=0;for(String id:g.animationIds()){Animation a=animations.get(id);long d=a.delayMs()+a.durationMs();value=g.mode()==GroupMode.SEQUENCE?value+d:Math.max(value,d);}return value;
    }
}
