package com.toolbox.tools.product;

import com.toolbox.tools.core.StableId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class VisualLayoutEngine {
    public enum LayoutMode { ROW, COLUMN, STACK, GRID, FREE }
    public enum AdaptiveClass { COMPACT, MEDIUM, EXPANDED }
    public enum Orientation { PORTRAIT, LANDSCAPE }
    public enum PointerBehavior { AUTO, NONE }
    public enum Layer { BACKGROUND, CONTENT, OVERLAY, MODAL, SYSTEM }
    public enum LockAspect { POSITION, SIZE, TRANSFORM, STYLE, CONTENT, BINDING, EVENT }
    public enum GuideAxis { X, Y }

    public static final class Guide {
        private final String id;
        private final GuideAxis axis;
        private final float position;
        public Guide(String id, GuideAxis axis, float position) {
            this.id=StableId.require(id,"guideId");
            this.axis=Objects.requireNonNull(axis,"axis");
            this.position=position;
        }
        public String id(){return id;} public GuideAxis axis(){return axis;} public float position(){return position;}
    }

    public static final class Insets {
        private final float left,top,right,bottom;
        public Insets(float left,float top,float right,float bottom){
            if(left<0||top<0||right<0||bottom<0) throw new IllegalArgumentException("insets invalid");
            this.left=left;this.top=top;this.right=right;this.bottom=bottom;
        }
        public float left(){return left;} public float top(){return top;} public float right(){return right;} public float bottom(){return bottom;}
    }

    public static final class Node {
        private final String id,parentId;
        private final float x,y,width,height;
        private final int z;
        private final PointerBehavior pointerBehavior;
        private final Layer layer;
        private final Set<LockAspect> locks;

        public Node(String id,String parentId,float x,float y,float width,float height,int z,boolean locked,PointerBehavior pointerBehavior) {
            this(id,parentId,x,y,width,height,z,pointerBehavior,Layer.CONTENT,
                    locked ? EnumSet.allOf(LockAspect.class) : EnumSet.noneOf(LockAspect.class));
        }

        public Node(String id,String parentId,float x,float y,float width,float height,int z,
                    PointerBehavior pointerBehavior,Layer layer,Set<LockAspect> locks) {
            this.id=StableId.require(id,"nodeId");
            this.parentId=parentId==null?null:StableId.require(parentId,"parentId");
            if(width<=0||height<=0) throw new IllegalArgumentException("ukuran node tidak valid");
            this.x=x;this.y=y;this.width=width;this.height=height;this.z=z;
            this.pointerBehavior=Objects.requireNonNull(pointerBehavior,"pointerBehavior");
            this.layer=Objects.requireNonNull(layer,"layer");
            this.locks=Collections.unmodifiableSet(locks.isEmpty()?EnumSet.noneOf(LockAspect.class):EnumSet.copyOf(locks));
        }

        public String id(){return id;} public String parentId(){return parentId;}
        public float x(){return x;} public float y(){return y;} public float width(){return width;} public float height(){return height;}
        public int z(){return z;} public boolean locked(){return !locks.isEmpty();}
        public PointerBehavior pointerBehavior(){return pointerBehavior;} public Layer layer(){return layer;}
        public Set<LockAspect> locks(){return locks;} public boolean locked(LockAspect aspect){return locks.contains(aspect);}

        Node move(float nx,float ny){return copy(parentId,nx,ny,width,height,z,pointerBehavior,layer,locks);}
        Node resize(float w,float h){return copy(parentId,x,y,w,h,z,pointerBehavior,layer,locks);}
        Node reparent(String parent,float nx,float ny){return copy(parent,nx,ny,width,height,z,pointerBehavior,layer,locks);}
        Node withZ(int next){return copy(parentId,x,y,width,height,next,pointerBehavior,layer,locks);}
        Node withLayer(Layer next){return copy(parentId,x,y,width,height,z,pointerBehavior,next,locks);}
        Node withPointer(PointerBehavior next){return copy(parentId,x,y,width,height,z,next,layer,locks);}
        Node withLocks(Set<LockAspect> next){return copy(parentId,x,y,width,height,z,pointerBehavior,layer,next);}
        private Node copy(String p,float nx,float ny,float w,float h,int nz,PointerBehavior pb,Layer l,Set<LockAspect> ls){
            return new Node(id,p,nx,ny,w,h,nz,pb,l,ls);
        }
    }

    private final Map<String,Node> nodes=new LinkedHashMap<>();
    private final Map<String,Guide> guides=new LinkedHashMap<>();
    private final Map<String,Map<Orientation,Map<String,Float>>> responsiveOverrides=new LinkedHashMap<>();
    private float zoom=1f,panX,panY;
    private Insets safeInsets=new Insets(0,0,0,0);

    public synchronized void add(Node node){
        Objects.requireNonNull(node,"node");
        if(nodes.containsKey(node.id())) throw new IllegalArgumentException("node duplikat");
        if(node.parentId()!=null&&!nodes.containsKey(node.parentId())) throw new IllegalArgumentException("parent tidak tersedia");
        nodes.put(node.id(),node);
    }

    public synchronized void move(String id,float x,float y,float gridSize){
        Node n=require(id); requireUnlocked(n,LockAspect.POSITION);
        float nx=snapWithGuides(x,gridSize,GuideAxis.X), ny=snapWithGuides(y,gridSize,GuideAxis.Y);
        nodes.put(n.id(),n.move(nx,ny));
    }

    public synchronized void resize(String id,float width,float height,float gridSize){
        Node n=require(id); requireUnlocked(n,LockAspect.SIZE);
        nodes.put(n.id(),n.resize(Math.max(Math.max(1,gridSize),snap(width,gridSize)),Math.max(Math.max(1,gridSize),snap(height,gridSize))));
    }

    public synchronized void reparent(String id,String parentId,float x,float y){
        Node n=require(id); requireUnlocked(n,LockAspect.POSITION);
        String parent=StableId.require(parentId,"parentId");
        if(!nodes.containsKey(parent)) throw new IllegalArgumentException("parent tidak tersedia");
        if(parent.equals(n.id())||isDescendant(parent,n.id())) throw new IllegalArgumentException("reparent membentuk siklus");
        nodes.put(n.id(),n.reparent(parent,x,y));
    }

    public synchronized void groupMove(List<String> ids,float dx,float dy){
        if(ids==null||ids.isEmpty()) return;
        LinkedHashMap<String,Node> next=new LinkedHashMap<>(nodes);
        for(String id:ids){Node n=require(id);requireUnlocked(n,LockAspect.POSITION);next.put(n.id(),n.move(n.x()+dx,n.y()+dy));}
        nodes.clear();nodes.putAll(next);
    }

    public synchronized void alignLeft(List<String> ids){
        if(ids==null||ids.size()<2) return;
        float min=Float.MAX_VALUE;for(String id:ids)min=Math.min(min,require(id).x());
        for(String id:ids){Node n=require(id);requireUnlocked(n,LockAspect.POSITION);nodes.put(id,n.move(min,n.y()));}
    }

    public synchronized void equalSize(List<String> ids){
        if(ids==null||ids.size()<2)return;
        Node first=require(ids.get(0));
        for(String id:ids){Node n=require(id);requireUnlocked(n,LockAspect.SIZE);nodes.put(id,n.resize(first.width(),first.height()));}
    }

    public synchronized void distributeHorizontal(List<String> ids){
        if(ids==null||ids.size()<3)return;
        List<Node> list=new ArrayList<>();for(String id:ids)list.add(require(id));
        list.sort(Comparator.comparingDouble(Node::x));
        float left=list.get(0).x(),right=list.get(list.size()-1).x()+list.get(list.size()-1).width();
        float widths=0;for(Node n:list)widths+=n.width();
        float gap=(right-left-widths)/(list.size()-1);
        float x=left;
        for(Node n:list){requireUnlocked(n,LockAspect.POSITION);nodes.put(n.id(),n.move(x,n.y()));x+=n.width()+gap;}
    }

    public synchronized void setLocks(String id,Set<LockAspect> locks){Node n=require(id);nodes.put(id,n.withLocks(locks));}
    public synchronized void setLayer(String id,Layer layer){Node n=require(id);nodes.put(id,n.withLayer(layer));}
    public synchronized void setPointerBehavior(String id,PointerBehavior p){Node n=require(id);nodes.put(id,n.withPointer(p));}

    public synchronized Node hitTest(float x,float y){
        List<Node> ordered=new ArrayList<>(nodes.values());
        ordered.sort((a,b)->{
            int layer=Integer.compare(b.layer().ordinal(),a.layer().ordinal());
            return layer!=0?layer:Integer.compare(b.z(),a.z());
        });
        for(Node n:ordered){
            if(n.pointerBehavior()==PointerBehavior.NONE)continue;
            if(x>=n.x()&&x<=n.x()+n.width()&&y>=n.y()&&y<=n.y()+n.height())return n;
        }
        return null;
    }

    public synchronized List<String> pathToRoot(String id){
        Node n=require(id);List<String> out=new ArrayList<>();out.add(n.id());
        int guard=0;while(n.parentId()!=null){if(++guard>128)throw new IllegalStateException("layout cycle");n=require(n.parentId());out.add(n.id());}
        Collections.reverse(out);return Collections.unmodifiableList(out);
    }

    public synchronized void bringToFront(String id){Node n=require(id);int max=0;for(Node x:nodes.values())max=Math.max(max,x.z());nodes.put(id,n.withZ(max+1));}

    public synchronized void addGuide(Guide guide){guides.put(guide.id(),guide);}
    public synchronized List<Guide> guides(){return Collections.unmodifiableList(new ArrayList<>(guides.values()));}

    public synchronized void setSafeInsets(Insets insets){safeInsets=Objects.requireNonNull(insets,"insets");}
    public synchronized Insets safeInsets(){return safeInsets;}

    public synchronized Node clampToSafeArea(String id,float left,float top,float right,float bottom){
        Node n=require(id);requireUnlocked(n,LockAspect.POSITION);
        float l=left+safeInsets.left(),t=top+safeInsets.top(),r=right-safeInsets.right(),b=bottom-safeInsets.bottom();
        Node c=n.move(Math.max(l,Math.min(r-n.width(),n.x())),Math.max(t,Math.min(b-n.height(),n.y())));
        nodes.put(id,c);return c;
    }

    public synchronized void setResponsiveOverride(String screenId,Orientation orientation,String property,float value){
        String screen=StableId.require(screenId,"screenId");StableId.require(property,"propertyId");
        responsiveOverrides.computeIfAbsent(screen,k->new LinkedHashMap<>())
                .computeIfAbsent(orientation,k->new LinkedHashMap<>()).put(property,value);
    }

    public synchronized Map<String,Float> responsiveOverride(String screenId,Orientation orientation){
        Map<Orientation,Map<String,Float>> by=responsiveOverrides.get(screenId);
        if(by==null)return Collections.emptyMap();
        return Collections.unmodifiableMap(new LinkedHashMap<>(by.getOrDefault(orientation,Collections.emptyMap())));
    }

    public synchronized void setViewport(float zoom,float panX,float panY){
        if(zoom<0.25f||zoom>4f)throw new IllegalArgumentException("zoom di luar batas");
        this.zoom=zoom;this.panX=panX;this.panY=panY;
    }
    public synchronized float zoom(){return zoom;} public synchronized float panX(){return panX;} public synchronized float panY(){return panY;}
    public synchronized float designX(float editorX){return (editorX-panX)/zoom;}
    public synchronized float designY(float editorY){return (editorY-panY)/zoom;}

    public synchronized AdaptiveClass adaptiveClass(float widthDp){if(widthDp<600)return AdaptiveClass.COMPACT;if(widthDp<840)return AdaptiveClass.MEDIUM;return AdaptiveClass.EXPANDED;}
    public synchronized Map<String,Node> snapshot(){return Collections.unmodifiableMap(new LinkedHashMap<>(nodes));}

    private Node require(String id){Node n=nodes.get(StableId.require(id,"nodeId"));if(n==null)throw new IllegalArgumentException("node tidak tersedia");return n;}
    private static void requireUnlocked(Node n,LockAspect aspect){if(n.locked(aspect))throw new IllegalStateException("node terkunci:"+aspect);}
    private boolean isDescendant(String candidate,String ancestor){Node c=nodes.get(candidate);while(c!=null&&c.parentId()!=null){if(ancestor.equals(c.parentId()))return true;c=nodes.get(c.parentId());}return false;}
    private float snapWithGuides(float value,float grid,GuideAxis axis){float s=snap(value,grid);float best=s;float distance=6f;for(Guide g:guides.values())if(g.axis()==axis&&Math.abs(g.position()-value)<=distance){best=g.position();distance=Math.abs(g.position()-value);}return best;}
    private static float snap(float value,float grid){if(grid<=0)return value;return Math.round(value/grid)*grid;}
}
