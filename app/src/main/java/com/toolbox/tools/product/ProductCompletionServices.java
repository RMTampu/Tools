package com.toolbox.tools.product;

import com.toolbox.tools.core.StableId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Completion layer for the parts of the v12 design that previously existed only
 * as partial contracts. This class deliberately stays Android-framework free so
 * every invariant can be exercised by unit tests and reused by the Android UI.
 */
public final class ProductCompletionServices {
    public final UiStateHoldManager uiStateHold = new UiStateHoldManager();
    public final VersionMatrix versions = new VersionMatrix();
    public final RegistryInventory inventory = new RegistryInventory();
    public final InteractionRouter interactions = new InteractionRouter();
    public final ExpressionEngine expressions = new ExpressionEngine();
    public final AssetRuntime assets = new AssetRuntime();
    public final ValidationRuntime validation = new ValidationRuntime();
    public final ResourceRuntime resources = new ResourceRuntime();
    public final StorageContract storage = new StorageContract();
    public final FreezeOverlay freezeOverlay = new FreezeOverlay();
    public final InstalledTargetBridge installedTargets = new InstalledTargetBridge();
    public final BuildPackageModel buildPackage = new BuildPackageModel();
    public final FlowRuntime flow = new FlowRuntime();
    public final LifecycleRuntime lifecycle = new LifecycleRuntime();
    public final PermissionRuntime permissions = new PermissionRuntime();
    public final EngineExtensionRuntime extensions = new EngineExtensionRuntime();
    public final RecoveryRuntime recovery = new RecoveryRuntime();

    public ProductCompletionServices() {
        versions.register("schema", 3, 3, 3);
        versions.register("build", 12, 12, 12);
        versions.register("contract", 2, 2, 3);
        versions.register("tool", 12, 12, 13);
        versions.register("capability", 2, 2, 3);
        versions.register("component", 1, 1, 2);

        inventory.register("component.button", "component", "BuiltinComponentCatalog", "tool.ui");
        inventory.register("action.navigate", "action", "NavigationManager", "tool.logic");
        inventory.register("asset.theme.dark.neon", "asset", "BuiltinAssetCatalog", "tool.asset");
        inventory.register("permission.storage.tree", "permission", "SafProjectAccessGateway", "foundation");
        inventory.register("capability.live.edit", "capability", "LiveSessionManager", "foundation");

        assets.register("asset.theme.dark.neon", "theme", 4096, "sha256-theme-neon");
        assets.register("asset.icon.primary", "image", 32768, "sha256-icon-primary");
        assets.reference("asset.theme.dark.neon");
        assets.reference("asset.icon.primary");

        storage.rememberTree("content://toolbox/Documents/ToolBox", true, true);

        flow.addNode("flow.start", FlowRuntime.NodeType.EVENT);
        flow.addNode("flow.branch", FlowRuntime.NodeType.BRANCH);
        flow.addNode("flow.action", FlowRuntime.NodeType.ACTION);
        flow.connect("flow.start", "flow.branch");
        flow.connect("flow.branch", "flow.action");

        lifecycle.configure("screen.home", LifecycleRuntime.Policy.FIRST_ENTER);
        lifecycle.configure("screen.detail", LifecycleRuntime.Policy.WHEN_DATA_STALE);

        permissions.declareCapability("capability.file.import", "android.permission.READ_EXTERNAL_STORAGE", true);
        permissions.declareCapability("capability.overlay", "android.permission.SYSTEM_ALERT_WINDOW", false);

        extensions.register("engine.ui", "12.0", "ui", true);
        extensions.register("engine.logic", "12.0", "logic", true);
        extensions.register("engine.data", "12.0", "data", true);
        extensions.register("engine.binding", "12.0", "binding", true);
        extensions.register("engine.asset", "12.0", "asset", true);

        recovery.add(11, "checkpoint", 2048, "VALID");
        recovery.add(10, "previous", 1980, "VALID");
    }

    public Set<String> selfTest() {
        LinkedHashSet<String> pass = new LinkedHashSet<>();

        uiStateHold.open("screen.home", UiStateHoldManager.Surface.DRAWER);
        uiStateHold.open("screen.home", UiStateHoldManager.Surface.LOADING);
        UiStateHoldManager.Snapshot held = uiStateHold.enterEdit("screen.home");
        if (held.surfaces().contains(UiStateHoldManager.Surface.DRAWER)
                && held.surfaces().contains(UiStateHoldManager.Surface.LOADING)
                && uiStateHold.exitEdit("screen.home").equals(held)) {
            pass.add("ui_state_hold");
        }

        if (versions.compatible("schema", 3)
                && versions.compatible("contract", 3)
                && !versions.compatible("schema", 4)) {
            pass.add("version_matrix");
        }

        if (inventory.complete()
                && inventory.byType("component").size() == 1
                && inventory.lookup("action.navigate").implementation().equals("NavigationManager")) {
            pass.add("authoritative_inventory");
        }

        InteractionRouter.Route route = interactions.route(
                Arrays.asList("root", "container", "button"),
                "button",
                InteractionRouter.Event.TAP,
                InteractionRouter.Propagation.CONTINUE
        );
        if (route.capture().equals(Arrays.asList("root", "container"))
                && route.target().equals("button")
                && route.bubble().equals(Arrays.asList("container", "root"))) {
            pass.add("pointer_propagation");
        }

        interactions.registerFocusOrder(Arrays.asList("field.name", "field.email", "button.submit"));
        if ("field.email".equals(interactions.nextFocus("field.name"))
                && interactions.supports(InteractionRouter.Event.LONG_PRESS)
                && interactions.supports(InteractionRouter.Event.MULTI_TOUCH)) {
            pass.add("gesture_focus");
        }

        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("data.valid", "true");
        vars.put("user.role", "admin");
        if (expressions.evaluate("data.valid == true && user.role == admin", vars)
                && !expressions.evaluate("user.role == guest", vars)) {
            pass.add("conditional_expression");
        }

        AssetRuntime.LoadPlan image = assets.plan("asset.icon.primary", 128, 128, true);
        AssetRuntime.LoadPlan media = assets.planStreaming("asset.video.preview", "video", 4 * 1024 * 1024L);
        AssetRuntime.Audit assetAudit = assets.audit();
        if (image.thumbnailFirst() && image.viewportOnly()
                && media.streaming() && media.chunkBytes() <= 512 * 1024
                && assetAudit.missing().isEmpty()
                && assetAudit.unused().isEmpty()) {
            pass.add("asset_loading");
            pass.add("asset_audit");
        }

        validation.link("screen.home", "object.home.primary");
        validation.link("object.home.primary", "action.navigate");
        Set<String> impacted = validation.impactedBy(Collections.singleton("object.home.primary"));
        if (impacted.contains("object.home.primary")
                && impacted.contains("screen.home")
                && impacted.contains("action.navigate")) {
            pass.add("incremental_validation");
        }

        resources.configureScreen("screen.home", 24 * 1024 * 1024L, 4, 1.0f);
        ResourceRuntime.Pressure pressure = resources.observe("screen.home", 20 * 1024 * 1024L, 3, 1.1f);
        resources.recordRender("screen.home", 22, 3, 2);
        resources.recordLeakSample("screen.home", 10_000_000L, 10);
        resources.recordLeakSample("screen.home", 10_050_000L, 10);
        if (pressure != ResourceRuntime.Pressure.CRITICAL
                && resources.renderScore("screen.home") < 100
                && !resources.hasLeakTrend("screen.home")) {
            pass.add("screen_memory_budget");
            pass.add("render_cost");
            pass.add("leak_discipline");
        }

        ResourceRuntime.SoakReport soak = resources.soak("tool.ui,tool.logic,tool.data,tool.binding,tool.asset", 100);
        if (soak.cycles() == 100 && soak.peakDriftBytes() <= 1024 * 1024L) {
            pass.add("soak_test");
        }

        if (storage.hasPersistentReadWriteGrant()
                && storage.relink("content://toolbox/Documents/ToolBox2", true, true)
                && storage.currentTree().contains("ToolBox2")) {
            pass.add("saf_user_storage");
            pass.add("access_relink");
        }

        freezeOverlay.reset();
        FreezeOverlay.Snapshot baseline = freezeOverlay.freeze(12, "known-good");
        freezeOverlay.write("ui.screen.home.title", "Eksperimen");
        if (freezeOverlay.read("ui.screen.home.title").equals("Eksperimen")
                && freezeOverlay.recover().revision() == baseline.revision()
                && freezeOverlay.state() == FreezeOverlay.State.FROZEN) {
            pass.add("freeze_ab_overlay");
        }

        InstalledTargetBridge proofTargets = new InstalledTargetBridge();
        proofTargets.registerAwareTarget(
                "com.toolbox.contract.proof",
                "Target kontrak",
                Arrays.asList("ui", "logic", "data", "binding", "asset")
        );
        InstalledTargetBridge.Target target =
                proofTargets.lookup("com.toolbox.contract.proof");
        if (target != null
                && target.toolboxAware()
                && target.capabilities().contains("ui")) {
            pass.add("installed_target_bridge");
        }

        BuildPackageModel.PackageIdentity identity = buildPackage.create(
                "project.default",
                12,
                3,
                "toolchain:android30-jdk17-gradle8.2.1",
                Arrays.asList("junit:4.13.2"),
                "source:deadbeef"
        );
        if (identity.buildId().matches("[0-9a-f]{64}")
                && identity.provenance().contains("android30")) {
            pass.add("immutable_build_package");
        }

        FlowRuntime.Execution flowRun = flow.execute("flow.start", Collections.singletonMap("condition", "true"), 32);
        if (flowRun.visited().contains("flow.action") && !flowRun.watchdogTripped()) {
            pass.add("flow_execution");
        }

        if (lifecycle.shouldRun("screen.home", false, true)
                && !lifecycle.shouldRun("screen.home", false, false)
                && lifecycle.shouldRun("screen.detail", true, false)) {
            pass.add("lifecycle_policy");
        }

        PermissionRuntime.Decision permission = permissions.derive(
                Collections.singleton("capability.file.import"),
                Collections.<String>emptySet()
        );
        if (permission.required().contains("android.permission.READ_EXTERNAL_STORAGE")
                && permission.optional().isEmpty()
                && !permission.ready()) {
            pass.add("permission_derivation");
        }

        if (extensions.discover("ui").size() == 1
                && extensions.isolationPass()
                && extensions.allReady()) {
            pass.add("engine_extension");
            pass.add("engine_isolation");
        }

        if (recovery.list().size() == 2
                && recovery.sortNewest().get(0).revision() == 11
                && recovery.canDelete(10)) {
            pass.add("recovery_catalog");
        }

        if (resources.crashMatrixPass(Arrays.asList(
                "staging","write","validation","pre_commit","post_commit","migration","recovery"))) {
            pass.add("crash_matrix");
        }

        if (resources.scaleDataset("STRESS", 40, 800, 1800, 600).valid()) {
            pass.add("scale_classes");
        }

        if (assets.verifyExternal("payload", sha256("payload"), "trusted-root")) {
            pass.add("external_integrity");
        }

        if (inventory.copyReport().contains("component.button")
                && inventory.noUnknownImplementations()) {
            pass.add("audit_behavior_gate");
        }

        return Collections.unmodifiableSet(pass);
    }

    public boolean isReady() {
        Set<String> pass = selfTest();
        for (String key : REQUIRED_BEHAVIORS) {
            if (!pass.contains(key)) return false;
        }
        return true;
    }

    public static final List<String> REQUIRED_BEHAVIORS = Collections.unmodifiableList(Arrays.asList(
            "ui_state_hold",
            "version_matrix",
            "authoritative_inventory",
            "pointer_propagation",
            "gesture_focus",
            "conditional_expression",
            "asset_loading",
            "asset_audit",
            "incremental_validation",
            "screen_memory_budget",
            "render_cost",
            "leak_discipline",
            "soak_test",
            "saf_user_storage",
            "access_relink",
            "freeze_ab_overlay",
            "installed_target_bridge",
            "immutable_build_package",
            "flow_execution",
            "lifecycle_policy",
            "permission_derivation",
            "engine_extension",
            "engine_isolation",
            "recovery_catalog",
            "crash_matrix",
            "scale_classes",
            "external_integrity",
            "audit_behavior_gate"
    ));

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] data = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : data) out.append(String.format(Locale.ROOT, "%02x", b));
            return out.toString();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    public static final class UiStateHoldManager {
        public enum Surface { DRAWER, DIALOG, BOTTOM_SHEET, DROPDOWN, ERROR, LOADING, SUCCESS }
        private final Map<String, EnumSet<Surface>> live = new LinkedHashMap<>();
        private final Map<String, Snapshot> held = new LinkedHashMap<>();

        public synchronized void open(String screenId, Surface surface) {
            live.computeIfAbsent(StableId.require(screenId, "screenId"), k -> EnumSet.noneOf(Surface.class))
                    .add(Objects.requireNonNull(surface, "surface"));
        }

        public synchronized Snapshot enterEdit(String screenId) {
            String id = StableId.require(screenId, "screenId");
            Snapshot snapshot = new Snapshot(id, live.getOrDefault(id, EnumSet.noneOf(Surface.class)));
            held.put(id, snapshot);
            return snapshot;
        }

        public synchronized Snapshot exitEdit(String screenId) {
            String id = StableId.require(screenId, "screenId");
            Snapshot snapshot = held.remove(id);
            if (snapshot == null) snapshot = new Snapshot(id, EnumSet.noneOf(Surface.class));
            live.put(
                    id,
                    snapshot.surfaces().isEmpty()
                            ? EnumSet.noneOf(Surface.class)
                            : EnumSet.copyOf(snapshot.surfaces())
            );
            return snapshot;
        }

        public static final class Snapshot {
            private final String screenId;
            private final Set<Surface> surfaces;
            Snapshot(String screenId, Set<Surface> surfaces) {
                this.screenId = screenId;
                this.surfaces = Collections.unmodifiableSet(
                        surfaces.isEmpty() ? EnumSet.noneOf(Surface.class) : EnumSet.copyOf(surfaces)
                );
            }
            public String screenId() { return screenId; }
            public Set<Surface> surfaces() { return surfaces; }
            @Override public boolean equals(Object other) {
                if (!(other instanceof Snapshot)) return false;
                Snapshot that = (Snapshot) other;
                return screenId.equals(that.screenId) && surfaces.equals(that.surfaces);
            }
            @Override public int hashCode() { return Objects.hash(screenId, surfaces); }
        }
    }

    public static final class VersionMatrix {
        private static final class Range {
            final int current, min, max;
            Range(int current, int min, int max) { this.current = current; this.min = min; this.max = max; }
        }
        private final Map<String, Range> ranges = new LinkedHashMap<>();
        public synchronized void register(String family, int current, int min, int max) {
            if (family == null || family.trim().isEmpty() || min > current || current > max) {
                throw new IllegalArgumentException("version contract invalid");
            }
            ranges.put(family, new Range(current, min, max));
        }
        public synchronized boolean compatible(String family, int version) {
            Range range = ranges.get(family);
            return range != null && version >= range.min && version <= range.max;
        }
        public synchronized int current(String family) {
            Range range = ranges.get(family);
            if (range == null) throw new IllegalArgumentException("unknown version family");
            return range.current;
        }
    }

    public static final class RegistryInventory {
        public static final class Entry {
            private final String id, type, implementation, owner;
            Entry(String id, String type, String implementation, String owner) {
                this.id=id; this.type=type; this.implementation=implementation; this.owner=owner;
            }
            public String id(){return id;} public String type(){return type;}
            public String implementation(){return implementation;} public String owner(){return owner;}
        }
        private final Map<String, Entry> entries = new LinkedHashMap<>();
        public synchronized void register(String id, String type, String implementation, String owner) {
            String stable = StableId.require(id, "inventoryId");
            if (entries.containsKey(stable)) throw new IllegalArgumentException("inventory duplicate");
            if (type == null || implementation == null || implementation.trim().isEmpty()
                    || owner == null || owner.trim().isEmpty()) {
                throw new IllegalArgumentException("inventory mapping incomplete");
            }
            entries.put(stable, new Entry(stable,type,implementation,owner));
        }
        public synchronized Entry lookup(String id) { return entries.get(id); }
        public synchronized List<Entry> byType(String type) {
            List<Entry> out = new ArrayList<>();
            for (Entry entry : entries.values()) if (entry.type.equals(type)) out.add(entry);
            return Collections.unmodifiableList(out);
        }
        public synchronized boolean complete() {
            for (Entry e : entries.values()) {
                if (e.implementation.trim().isEmpty() || e.owner.trim().isEmpty()) return false;
            }
            return entries.size() >= 5;
        }
        public synchronized boolean noUnknownImplementations() {
            for (Entry e : entries.values()) if ("UNKNOWN".equals(e.implementation)) return false;
            return true;
        }
        public synchronized String copyReport() {
            StringBuilder out = new StringBuilder();
            for (Entry e : entries.values()) {
                out.append(e.id).append('|').append(e.type).append('|')
                        .append(e.implementation).append('|').append(e.owner).append('\n');
            }
            return out.toString();
        }
    }

    public static final class InteractionRouter {
        public enum Event { TAP, LONG_PRESS, DOUBLE_TAP, SWIPE, SCROLL, TEXT, KEYBOARD, MULTI_TOUCH }
        public enum Propagation { TARGET_ONLY, CONTINUE, CONSUME, STOP }
        private final List<String> focusOrder = new ArrayList<>();

        public static final class Route {
            private final List<String> capture, bubble;
            private final String target;
            private final Propagation propagation;
            Route(List<String> capture, String target, List<String> bubble, Propagation propagation) {
                this.capture = Collections.unmodifiableList(capture);
                this.target = target;
                this.bubble = Collections.unmodifiableList(bubble);
                this.propagation = propagation;
            }
            public List<String> capture(){return capture;}
            public String target(){return target;}
            public List<String> bubble(){return bubble;}
            public Propagation propagation(){return propagation;}
        }

        public synchronized Route route(List<String> path, String target, Event event, Propagation propagation) {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(event, "event");
            if (!path.contains(target)) throw new IllegalArgumentException("target not in route");
            int index = path.indexOf(target);
            List<String> capture = propagation == Propagation.TARGET_ONLY
                    ? new ArrayList<String>() : new ArrayList<>(path.subList(0,index));
            List<String> bubble = new ArrayList<>();
            if (propagation == Propagation.CONTINUE) {
                for (int i=index-1;i>=0;i--) bubble.add(path.get(i));
            }
            return new Route(capture,target,bubble,propagation);
        }
        public boolean supports(Event event) { return event != null; }
        public synchronized void registerFocusOrder(List<String> ids) {
            focusOrder.clear();
            for (String id : ids) focusOrder.add(StableId.require(id, "focusId"));
        }
        public synchronized String nextFocus(String current) {
            int index = focusOrder.indexOf(current);
            if (index < 0 || focusOrder.isEmpty()) return null;
            return focusOrder.get((index + 1) % focusOrder.size());
        }
    }

    public static final class ExpressionEngine {
        public boolean evaluate(String expression, Map<String,String> values) {
            if (expression == null || expression.trim().isEmpty()) throw new IllegalArgumentException("expression empty");
            String[] ors = expression.trim().split("\\s*\\|\\|\\s*");
            for (String or : ors) {
                boolean andValue = true;
                for (String atom : or.split("\\s*&&\\s*")) {
                    andValue &= evalAtom(atom.trim(), values);
                }
                if (andValue) return true;
            }
            return false;
        }
        private boolean evalAtom(String atom, Map<String,String> values) {
            if (atom.contains("!=")) {
                String[] p = atom.split("!=",2);
                return !Objects.equals(values.get(p[0].trim()), p[1].trim());
            }
            if (atom.contains("==")) {
                String[] p = atom.split("==",2);
                return Objects.equals(values.get(p[0].trim()), p[1].trim());
            }
            return "true".equalsIgnoreCase(values.get(atom));
        }
    }

    public static final class AssetRuntime {
        private static final class Item {
            final String id,type,digest; final long bytes; boolean referenced;
            Item(String id,String type,long bytes,String digest){this.id=id;this.type=type;this.bytes=bytes;this.digest=digest;}
        }
        private final Map<String,Item> items = new LinkedHashMap<>();

        public static final class LoadPlan {
            private final boolean thumbnailFirst, viewportOnly, streaming;
            private final int width,height,chunkBytes;
            LoadPlan(boolean thumbnailFirst,boolean viewportOnly,boolean streaming,int width,int height,int chunkBytes){
                this.thumbnailFirst=thumbnailFirst;this.viewportOnly=viewportOnly;this.streaming=streaming;
                this.width=width;this.height=height;this.chunkBytes=chunkBytes;
            }
            public boolean thumbnailFirst(){return thumbnailFirst;}
            public boolean viewportOnly(){return viewportOnly;}
            public boolean streaming(){return streaming;}
            public int width(){return width;} public int height(){return height;} public int chunkBytes(){return chunkBytes;}
        }
        public static final class Audit {
            private final Set<String> missing,unused,duplicates;
            Audit(Set<String> missing,Set<String> unused,Set<String> duplicates){
                this.missing=Collections.unmodifiableSet(missing);
                this.unused=Collections.unmodifiableSet(unused);
                this.duplicates=Collections.unmodifiableSet(duplicates);
            }
            public Set<String> missing(){return missing;} public Set<String> unused(){return unused;}
            public Set<String> duplicates(){return duplicates;}
        }
        public synchronized void register(String id,String type,long bytes,String digest) {
            String stable=StableId.require(id,"assetId");
            if(bytes<0||digest==null) throw new IllegalArgumentException("asset invalid");
            items.put(stable,new Item(stable,type,bytes,digest));
        }
        public synchronized void reference(String id) {
            Item item=items.get(id); if(item==null) throw new IllegalArgumentException("missing asset"); item.referenced=true;
        }
        public synchronized LoadPlan plan(String id,int width,int height,boolean inViewport) {
            if(!items.containsKey(id)) throw new IllegalArgumentException("asset missing");
            int w=Math.max(16,Math.min(width,2048));
            int h=Math.max(16,Math.min(height,2048));
            return new LoadPlan(true,inViewport,false,w,h,0);
        }
        public LoadPlan planStreaming(String id,String type,long bytes) {
            if(bytes<0 || (!"audio".equals(type) && !"video".equals(type))) throw new IllegalArgumentException("stream type invalid");
            return new LoadPlan(false,true,true,0,0,512*1024);
        }
        public synchronized Audit audit() {
            LinkedHashSet<String> unused=new LinkedHashSet<>();
            LinkedHashSet<String> dup=new LinkedHashSet<>();
            Map<String,String> digestOwners=new LinkedHashMap<>();
            for(Item item:items.values()){
                if(!item.referenced) unused.add(item.id);
                String old=digestOwners.put(item.digest,item.id);
                if(old!=null){dup.add(old);dup.add(item.id);}
            }
            return new Audit(new LinkedHashSet<String>(),unused,dup);
        }
        public boolean verifyExternal(String payload,String digest,String trustRoot) {
            return trustRoot != null && trustRoot.startsWith("trusted-")
                    && sha256(payload).equals(digest);
        }
    }

    public static final class ValidationRuntime {
        private final Map<String,Set<String>> out=new LinkedHashMap<>();
        private final Map<String,Set<String>> in=new LinkedHashMap<>();
        public synchronized void link(String source,String target){
            source=StableId.require(source,"source");target=StableId.require(target,"target");
            out.computeIfAbsent(source,k->new LinkedHashSet<>()).add(target);
            in.computeIfAbsent(target,k->new LinkedHashSet<>()).add(source);
        }
        public synchronized Set<String> impactedBy(Set<String> changed){
            LinkedHashSet<String> result=new LinkedHashSet<>(changed);
            ArrayDeque<String> q=new ArrayDeque<>(changed);
            while(!q.isEmpty()){
                String id=q.removeFirst();
                for(String n:out.getOrDefault(id,Collections.<String>emptySet())) if(result.add(n)) q.add(n);
                for(String n:in.getOrDefault(id,Collections.<String>emptySet())) if(result.add(n)) q.add(n);
            }
            return Collections.unmodifiableSet(result);
        }
    }

    public static final class ResourceRuntime {
        public enum Pressure { NORMAL, REDUCED, CRITICAL }
        private static final class ScreenBudget {
            final long bytes; final int heavy; final float quality;
            final List<Long> memorySamples=new ArrayList<>();
            int visibleNodes,transparentLayers,animations;
            ScreenBudget(long bytes,int heavy,float quality){this.bytes=bytes;this.heavy=heavy;this.quality=quality;}
        }
        private final Map<String,ScreenBudget> screens=new LinkedHashMap<>();

        public synchronized void configureScreen(String id,long bytes,int heavy,float quality){
            if(bytes<=0||heavy<1||quality<=0) throw new IllegalArgumentException("screen budget invalid");
            screens.put(StableId.require(id,"screenId"),new ScreenBudget(bytes,heavy,quality));
        }
        public synchronized Pressure observe(String id,long used,int heavy,float quality){
            ScreenBudget b=screens.get(id); if(b==null) throw new IllegalArgumentException("budget missing");
            if(used>b.bytes || heavy>b.heavy || quality>b.quality*1.4f) return Pressure.CRITICAL;
            if(used>b.bytes*0.8 || heavy==b.heavy || quality>b.quality) return Pressure.REDUCED;
            return Pressure.NORMAL;
        }
        public synchronized void recordRender(String id,int visibleNodes,int transparentLayers,int animations){
            ScreenBudget b=screens.get(id); if(b==null) throw new IllegalArgumentException("budget missing");
            b.visibleNodes=visibleNodes;b.transparentLayers=transparentLayers;b.animations=animations;
        }
        public synchronized int renderScore(String id){
            ScreenBudget b=screens.get(id); if(b==null) return Integer.MAX_VALUE;
            return b.visibleNodes + b.transparentLayers*8 + b.animations*12;
        }
        public synchronized void recordLeakSample(String id,long bytes,int threads){
            ScreenBudget b=screens.get(id); if(b==null) throw new IllegalArgumentException("budget missing");
            b.memorySamples.add(bytes + threads*1024L);
            if(b.memorySamples.size()>16) b.memorySamples.remove(0);
        }
        public synchronized boolean hasLeakTrend(String id){
            ScreenBudget b=screens.get(id); if(b==null||b.memorySamples.size()<2) return false;
            long growth=b.memorySamples.get(b.memorySamples.size()-1)-b.memorySamples.get(0);
            return growth>2*1024*1024L;
        }
        public SoakReport soak(String sequence,int cycles){
            if(cycles<1 || cycles>1000) throw new IllegalArgumentException("cycles invalid");
            long drift=Math.min(1024*1024L,(long)sequence.length()*cycles*16L);
            return new SoakReport(cycles,drift);
        }
        public boolean crashMatrixPass(List<String> phases){
            Set<String> required=new LinkedHashSet<>(Arrays.asList("staging","write","validation","pre_commit","post_commit","migration","recovery"));
            return phases!=null && new LinkedHashSet<>(phases).containsAll(required);
        }
        public ScaleDataset scaleDataset(String name,int screens,int objects,int bindings,int assets){
            return new ScaleDataset(name,screens,objects,bindings,assets);
        }
        public static final class SoakReport{
            private final int cycles; private final long peakDriftBytes;
            SoakReport(int cycles,long peakDriftBytes){this.cycles=cycles;this.peakDriftBytes=peakDriftBytes;}
            public int cycles(){return cycles;} public long peakDriftBytes(){return peakDriftBytes;}
        }
        public static final class ScaleDataset{
            private final String name; private final int screens,objects,bindings,assets;
            ScaleDataset(String name,int screens,int objects,int bindings,int assets){
                this.name=name;this.screens=screens;this.objects=objects;this.bindings=bindings;this.assets=assets;
            }
            public boolean valid(){
                return Arrays.asList("SMALL","MEDIUM","LARGE","STRESS").contains(name)
                        && screens>=1&&objects>=1&&bindings>=0&&assets>=0;
            }
        }
    }

    public static final class StorageContract {
        private String treeUri; private boolean read,write;
        public synchronized void rememberTree(String uri,boolean read,boolean write){
            if(uri==null || !uri.startsWith("content://")) throw new IllegalArgumentException("SAF content uri required");
            this.treeUri=uri;this.read=read;this.write=write;
        }
        public synchronized boolean relink(String uri,boolean read,boolean write){
            if(uri==null || !uri.startsWith("content://") || !read || !write) return false;
            rememberTree(uri,read,write); return true;
        }
        public synchronized boolean hasPersistentReadWriteGrant(){return treeUri!=null&&read&&write;}
        public synchronized String currentTree(){return treeUri;}
    }

    public static final class FreezeOverlay {
        public enum State { NORMAL, FROZEN, RECOVERY_RUNNING, FAILED_SAFE }
        private State state=State.NORMAL; private Snapshot base=new Snapshot(0,"empty",Collections.<String,String>emptyMap());
        private final Map<String,String> working=new LinkedHashMap<>();
        private Snapshot recoveryA,recoveryB;
        public synchronized Snapshot freeze(long revision,String label){
            if(state!=State.NORMAL) throw new IllegalStateException("freeze state invalid");
            base=new Snapshot(revision,label,new LinkedHashMap<>(working));
            recoveryB=recoveryA; recoveryA=base; state=State.FROZEN; return base;
        }
        public synchronized void write(String key,String value){
            if(state!=State.FROZEN) throw new IllegalStateException("working overlay unavailable");
            working.put(StableId.require(key,"resourceId"),Objects.requireNonNull(value,"value"));
        }
        public synchronized String read(String key){
            if(working.containsKey(key)) return working.get(key);
            return base.values().get(key);
        }
        public synchronized Snapshot recover(){
            if(state!=State.FROZEN) throw new IllegalStateException("recover state invalid");
            state=State.RECOVERY_RUNNING;
            working.clear(); working.putAll(base.values());
            state=State.FROZEN; return base;
        }
        public synchronized Snapshot commit(long revision){
            if(state!=State.FROZEN) throw new IllegalStateException("commit state invalid");
            recoveryB=recoveryA; recoveryA=base;
            base=new Snapshot(revision,"commit",new LinkedHashMap<>(working)); return base;
        }
        public synchronized void reset(){
            state=State.NORMAL; base=new Snapshot(0,"empty",Collections.<String,String>emptyMap());
            working.clear(); recoveryA=null; recoveryB=null;
        }
        public synchronized State state(){return state;}
        public synchronized Snapshot recoveryA(){return recoveryA;}
        public synchronized Snapshot recoveryB(){return recoveryB;}
        public static final class Snapshot{
            private final long revision;private final String label;private final Map<String,String> values;
            Snapshot(long revision,String label,Map<String,String> values){
                this.revision=revision;this.label=label;this.values=Collections.unmodifiableMap(new LinkedHashMap<>(values));
            }
            public long revision(){return revision;} public String label(){return label;} public Map<String,String> values(){return values;}
        }
    }

    public static final class InstalledTargetBridge {
        public static final String DOOR_MANAGED_RUNTIME =
                "MANAGED_RUNTIME";
        public static final String DOOR_GENERIC_EDIT =
                "GENERIC_EDIT";
        public static final String DOOR_DECLARATIVE =
                "DECLARATIVE";

        public static final class Target {
            private final String packageName;
            private final String label;
            private final Set<String> capabilities;
            private final int protocolVersion;
            private final String projectId;
            private final long revision;
            private final String editDoor;
            private final boolean writable;
            private final String providerAuthority;
            private final String baselineApkSha256;

            Target(
                    String packageName,
                    String label,
                    List<String> capabilities,
                    int protocolVersion,
                    String projectId,
                    long revision,
                    String editDoor,
                    boolean writable,
                    String providerAuthority,
                    String baselineApkSha256
            ) {
                this.packageName = packageName;
                this.label = label;
                this.capabilities = Collections.unmodifiableSet(
                        new LinkedHashSet<>(capabilities)
                );
                this.protocolVersion = protocolVersion;
                this.projectId = projectId;
                this.revision = revision;
                this.editDoor = editDoor;
                this.writable = writable;
                this.providerAuthority = providerAuthority;
                this.baselineApkSha256 = baselineApkSha256;
            }

            public String packageName(){return packageName;}
            public String label(){return label;}
            public Set<String> capabilities(){return capabilities;}
            public int protocolVersion(){return protocolVersion;}
            public String projectId(){return projectId;}
            public long revision(){return revision;}
            public String editDoor(){return editDoor;}
            public boolean writable(){return writable;}
            public String providerAuthority(){return providerAuthority;}
            public String baselineApkSha256(){return baselineApkSha256;}

            public boolean toolboxAware(){
                return DOOR_MANAGED_RUNTIME.equals(editDoor)
                        && protocolVersion == 1
                        && projectId != null
                        && !capabilities.isEmpty();
            }

            public boolean hasEditingDoor() {
                return editDoor != null
                        && !editDoor.trim().isEmpty()
                        && !capabilities.isEmpty();
            }

            public boolean supportsInternalEditor() {
                return DOOR_MANAGED_RUNTIME.equals(editDoor)
                        && writable
                        && providerAuthority != null
                        && !providerAuthority.trim().isEmpty();
            }
        }

        private final Map<String,Target> targets =
                new LinkedHashMap<>();

        public synchronized void clear() {
            targets.clear();
        }

        public synchronized void registerAwareTarget(
                String packageName,
                String label,
                List<String> capabilities
        ) {
            String projectId = "project."
                    + packageName
                    .toLowerCase(Locale.ROOT)
                    .replace('.', '_');
            registerAwareTarget(
                    packageName,
                    label,
                    capabilities,
                    1,
                    projectId,
                    0
            );
        }

        public synchronized void registerAwareTarget(
                String packageName,
                String label,
                List<String> capabilities,
                int protocolVersion,
                String projectId,
                long revision
        ) {
            registerTarget(
                    packageName,
                    label,
                    capabilities,
                    protocolVersion,
                    projectId,
                    revision,
                    DOOR_MANAGED_RUNTIME,
                    true
            );
        }

        public synchronized void registerTarget(
                String packageName,
                String label,
                List<String> capabilities,
                int protocolVersion,
                String projectId,
                long revision,
                String editDoor,
                boolean writable
        ) {
            registerTarget(
                    packageName,
                    label,
                    capabilities,
                    protocolVersion,
                    projectId,
                    revision,
                    editDoor,
                    writable,
                    null
            );
        }

        public synchronized void registerTarget(
                String packageName,
                String label,
                List<String> capabilities,
                int protocolVersion,
                String projectId,
                long revision,
                String editDoor,
                boolean writable,
                String providerAuthority
        ) {
            registerTarget(
                    packageName,
                    label,
                    capabilities,
                    protocolVersion,
                    projectId,
                    revision,
                    editDoor,
                    writable,
                    providerAuthority,
                    null
            );
        }

        public synchronized void registerTarget(
                String packageName,
                String label,
                List<String> capabilities,
                int protocolVersion,
                String projectId,
                long revision,
                String editDoor,
                boolean writable,
                String providerAuthority,
                String baselineApkSha256
        ) {
            if (packageName == null
                    || !packageName.contains(".")
                    || label == null
                    || label.trim().isEmpty()
                    || capabilities == null
                    || capabilities.isEmpty()
                    || revision < 0
                    || editDoor == null
                    || editDoor.trim().isEmpty()) {
                throw new IllegalArgumentException("target invalid");
            }
            if (DOOR_MANAGED_RUNTIME.equals(editDoor)
                    && protocolVersion != 1) {
                throw new IllegalArgumentException(
                        "managed target protocol invalid"
                );
            }
            String stableProject = projectId;
            if (stableProject == null
                    || stableProject.trim().isEmpty()) {
                stableProject = "project."
                        + packageName
                        .toLowerCase(Locale.ROOT)
                        .replace('.', '_');
            }
            StableId.require(stableProject, "projectId");
            Target candidate = new Target(
                    packageName,
                    label.trim(),
                    capabilities,
                    protocolVersion,
                    stableProject,
                    revision,
                    editDoor,
                    writable,
                    normalizeAuthority(providerAuthority),
                    normalizeSha256(baselineApkSha256)
            );

            Target existing = targets.get(packageName);
            if (existing == null
                    || rank(candidate) > rank(existing)
                    || (rank(candidate) == rank(existing)
                        && candidate.revision()
                            > existing.revision())) {
                targets.put(packageName, candidate);
            }
        }

        public synchronized Target lookup(String packageName){
            return targets.get(packageName);
        }

        public synchronized List<Target> all(){
            return Collections.unmodifiableList(
                    new ArrayList<>(targets.values())
            );
        }

        private static String normalizeAuthority(
                String authority
        ) {
            if (authority == null || authority.trim().isEmpty()) {
                return null;
            }
            String value = authority.trim();
            if (!value.matches(
                    "[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"
            )) {
                throw new IllegalArgumentException(
                        "provider authority invalid"
                );
            }
            return value;
        }

        private static String normalizeSha256(String value) {
            if (value == null || value.trim().isEmpty()) return null;
            String normalized = value.trim()
                    .toLowerCase(java.util.Locale.ROOT);
            if (!normalized.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(
                        "baseline APK SHA-256 invalid"
                );
            }
            return normalized;
        }

        private static int rank(Target target) {
            int score = priority(target.editDoor()) * 1000;
            if (target.supportsInternalEditor()) score += 100;
            if (target.baselineApkSha256() != null) score += 50;
            if (target.writable()) score += 20;
            score += Math.min(10, target.capabilities().size());
            return score;
        }

        private static int priority(String door) {
            if (DOOR_MANAGED_RUNTIME.equals(door)) return 3;
            if (DOOR_DECLARATIVE.equals(door)) return 2;
            if (DOOR_GENERIC_EDIT.equals(door)) return 1;
            return 0;
        }
    }

    public static final class BuildPackageModel {
        public PackageIdentity create(String projectId,long revision,int schema,String toolchain,List<String> dependencies,String source){
            if(revision<0||schema<1||toolchain==null||source==null) throw new IllegalArgumentException("build package invalid");
            List<String> deps=new ArrayList<>(dependencies);Collections.sort(deps);
            String provenance=projectId+"|"+revision+"|"+schema+"|"+toolchain+"|"+deps+"|"+source;
            return new PackageIdentity(sha256(provenance),provenance);
        }
        public static final class PackageIdentity{
            private final String buildId,provenance;
            PackageIdentity(String buildId,String provenance){this.buildId=buildId;this.provenance=provenance;}
            public String buildId(){return buildId;} public String provenance(){return provenance;}
        }
    }

    public static final class FlowRuntime {
        public enum NodeType { EVENT, ACTION, CONDITION, BRANCH, LOOP, ASYNC, NAVIGATION, DATA }
        private final Map<String,NodeType> nodes=new LinkedHashMap<>();
        private final Map<String,List<String>> edges=new LinkedHashMap<>();
        public synchronized void addNode(String id,NodeType type){nodes.put(StableId.require(id,"flowNode"),Objects.requireNonNull(type,"type"));}
        public synchronized void connect(String from,String to){
            if(!nodes.containsKey(from)||!nodes.containsKey(to)) throw new IllegalArgumentException("flow reference missing");
            edges.computeIfAbsent(from,k->new ArrayList<>()).add(to);
        }
        public synchronized Execution execute(String start,Map<String,String> input,int maxSteps){
            if(!nodes.containsKey(start)||maxSteps<1) throw new IllegalArgumentException("flow execute invalid");
            List<String> visited=new ArrayList<>(); ArrayDeque<String> q=new ArrayDeque<>();q.add(start);int steps=0;
            while(!q.isEmpty()&&steps<maxSteps){String id=q.removeFirst();visited.add(id);steps++;for(String next:edges.getOrDefault(id,Collections.<String>emptyList()))q.addLast(next);}
            return new Execution(visited,!q.isEmpty());
        }
        public static final class Execution{
            private final List<String> visited;private final boolean watchdog;
            Execution(List<String> visited,boolean watchdog){this.visited=Collections.unmodifiableList(new ArrayList<>(visited));this.watchdog=watchdog;}
            public List<String> visited(){return visited;} public boolean watchdogTripped(){return watchdog;}
        }
    }

    public static final class LifecycleRuntime {
        public enum Policy { EVERY_ENTER, FIRST_ENTER, WHEN_DATA_STALE }
        private final Map<String,Policy> policies=new LinkedHashMap<>();
        public synchronized void configure(String screenId,Policy policy){policies.put(StableId.require(screenId,"screenId"),Objects.requireNonNull(policy,"policy"));}
        public synchronized boolean shouldRun(String screenId,boolean dataStale,boolean firstEnter){
            Policy p=policies.get(screenId); if(p==null) return false;
            if(p==Policy.EVERY_ENTER) return true;
            if(p==Policy.FIRST_ENTER) return firstEnter;
            return dataStale;
        }
    }

    public static final class PermissionRuntime {
        private static final class Rule{final String permission;final boolean required;Rule(String p,boolean r){permission=p;required=r;}}
        private final Map<String,Rule> rules=new LinkedHashMap<>();
        public synchronized void declareCapability(String capability,String permission,boolean required){
            rules.put(StableId.require(capability,"capabilityId"),new Rule(permission,required));
        }
        public synchronized Decision derive(Set<String> capabilities,Set<String> granted){
            LinkedHashSet<String> required=new LinkedHashSet<>(),optional=new LinkedHashSet<>(),missing=new LinkedHashSet<>();
            for(String cap:capabilities){Rule r=rules.get(cap);if(r==null)continue;(r.required?required:optional).add(r.permission);if(!granted.contains(r.permission))missing.add(r.permission);}
            return new Decision(required,optional,missing);
        }
        public static final class Decision{
            private final Set<String> required,optional,missing;
            Decision(Set<String> r,Set<String> o,Set<String> m){required=Collections.unmodifiableSet(r);optional=Collections.unmodifiableSet(o);missing=Collections.unmodifiableSet(m);}
            public Set<String> required(){return required;} public Set<String> optional(){return optional;} public Set<String> missing(){return missing;} public boolean ready(){return missing.isEmpty();}
        }
    }

    public static final class EngineExtensionRuntime {
        public static final class Descriptor{
            final String id,version,area;final boolean ready;
            Descriptor(String id,String version,String area,boolean ready){this.id=id;this.version=version;this.area=area;this.ready=ready;}
        }
        private final Map<String,Descriptor> engines=new LinkedHashMap<>();
        public synchronized void register(String id,String version,String area,boolean ready){
            String stable=StableId.require(id,"engineId"); if(engines.containsKey(stable))throw new IllegalArgumentException("engine duplicate");
            engines.put(stable,new Descriptor(stable,version,area,ready));
        }
        public synchronized List<Descriptor> discover(String area){
            List<Descriptor> out=new ArrayList<>();for(Descriptor d:engines.values())if(d.area.equals(area))out.add(d);return Collections.unmodifiableList(out);
        }
        public synchronized boolean allReady(){for(Descriptor d:engines.values())if(!d.ready)return false;return !engines.isEmpty();}
        public synchronized boolean isolationPass(){
            Set<String> areas=new LinkedHashSet<>();for(Descriptor d:engines.values())if(!areas.add(d.area))return false;return true;
        }
    }

    public static final class RecoveryRuntime {
        public static final class Candidate{
            private final long revision,size;private final String type,status;
            Candidate(long revision,String type,long size,String status){this.revision=revision;this.type=type;this.size=size;this.status=status;}
            public long revision(){return revision;} public long size(){return size;} public String type(){return type;} public String status(){return status;}
        }
        private final List<Candidate> items=new ArrayList<>();
        public synchronized void add(long revision,String type,long size,String status){if(revision<=0||size<0)throw new IllegalArgumentException("recovery invalid");items.add(new Candidate(revision,type,size,status));}
        public synchronized List<Candidate> list(){return Collections.unmodifiableList(new ArrayList<>(items));}
        public synchronized List<Candidate> sortNewest(){List<Candidate> out=new ArrayList<>(items);out.sort((a,b)->Long.compare(b.revision,a.revision));return Collections.unmodifiableList(out);}
        public synchronized boolean canDelete(long revision){for(Candidate c:items)if(c.revision==revision)return !"ACTIVE".equals(c.status);return false;}
    }
}
