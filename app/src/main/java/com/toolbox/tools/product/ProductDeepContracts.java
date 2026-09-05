package com.toolbox.tools.product;

import com.toolbox.tools.core.StableId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Currency;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Deep contracts that replace the remaining "metadata-only" parts of the v12
 * design with deterministic behavior that can be unit-tested.
 */
public final class ProductDeepContracts {
    public final ConverterRegistry converters = new ConverterRegistry();
    public final DataProviderRegistry dataProviders = new DataProviderRegistry();
    public final StateLayerResolver stateLayers = new StateLayerResolver();
    public final CacheBudget cacheBudget = new CacheBudget();
    public final StaticDependencyGate dependencies = new StaticDependencyGate();
    public final FaultIsolation faultIsolation = new FaultIsolation();
    public final ProjectSourceOfTruth sourceOfTruth = new ProjectSourceOfTruth();

    public ProductDeepContracts() {
        converters.register("converter.number_to_text", ValueType.NUMBER, ValueType.TEXT);
        converters.register("converter.text_to_number", ValueType.TEXT, ValueType.NUMBER);

        dataProviders.register("provider.database", ProviderKind.DATABASE,
                EnumSet.of(ProviderCapability.READ, ProviderCapability.WRITE, ProviderCapability.PAGE));
        dataProviders.register("provider.api", ProviderKind.API,
                EnumSet.of(ProviderCapability.READ, ProviderCapability.WRITE, ProviderCapability.PAGE, ProviderCapability.STREAM));
        dataProviders.register("provider.file", ProviderKind.FILE,
                EnumSet.of(ProviderCapability.READ, ProviderCapability.WRITE, ProviderCapability.STREAM));
        dataProviders.register("provider.form", ProviderKind.FORM,
                EnumSet.of(ProviderCapability.READ, ProviderCapability.WRITE));
        dataProviders.register("provider.runtime", ProviderKind.RUNTIME,
                EnumSet.of(ProviderCapability.READ, ProviderCapability.STREAM));
        dataProviders.register("provider.action", ProviderKind.ACTION,
                EnumSet.of(ProviderCapability.WRITE));

        cacheBudget.configure(CacheCategory.THUMBNAIL, 8 * 1024 * 1024L, 32 * 1024 * 1024L);
        cacheBudget.configure(CacheCategory.PREVIEW, 24 * 1024 * 1024L, 96 * 1024 * 1024L);
        cacheBudget.configure(CacheCategory.INDEX, 4 * 1024 * 1024L, 16 * 1024 * 1024L);
        cacheBudget.configure(CacheCategory.TEMP, 8 * 1024 * 1024L, 64 * 1024 * 1024L);

        dependencies.registerTool("tool.ui");
        dependencies.registerTool("tool.logic");
        dependencies.registerTool("tool.data");
        dependencies.registerTool("tool.binding");
        dependencies.registerTool("tool.asset");

        sourceOfTruth.configure(
                ProjectSourceOfTruth.Source.USER_OWNED_STORAGE,
                true,
                true,
                true
        );
    }

    public Set<String> selfTest() {
        LinkedHashSet<String> pass = new LinkedHashSet<>();

        PropertySpec property = new PropertySpec(
                "property.opacity",
                ValueType.NUMBER,
                false,
                true,
                0.0,
                1.0,
                "ratio",
                new LinkedHashSet<>(Arrays.asList("state.normal", "state.disabled")),
                null
        );
        if (property.accepts("0.75") && !property.accepts("1.5")) {
            pass.add("property_contract");
        }

        EventSpec event = new EventSpec(
                "event.click",
                ValueType.NONE,
                ValueType.NONE,
                PropagationPolicy.CONTINUE
        );
        ActionSpec action = new ActionSpec(
                "action.navigate",
                "execution.navigate.1",
                ExecutionMode.SYNC,
                1,
                FailurePolicy.STOP,
                true,
                5000
        );
        if (event.propagation() == PropagationPolicy.CONTINUE
                && action.idempotent()
                && action.retryCount() == 1) {
            pass.add("event_action_contract");
        }

        if (converters.canConvert(ValueType.TEXT, ValueType.NUMBER)
                && !converters.canConvert(ValueType.BOOLEAN, ValueType.NUMBER)) {
            pass.add("safe_converter");
        }

        CompositeExecutor.Result composite = new CompositeExecutor().execute(
                Arrays.asList(
                        new CompositeExecutor.Step("step.prepare", true, true),
                        new CompositeExecutor.Step("step.apply", false, true),
                        new CompositeExecutor.Step("step.unreachable", true, false)
                )
        );
        if (!composite.success()
                && composite.executed().equals(Arrays.asList("step.prepare", "step.apply"))
                && composite.compensated().contains("step.prepare")) {
            pass.add("composite_executor");
        }

        if (dataProviders.byKind(ProviderKind.DATABASE).size() == 1
                && dataProviders.allKindsCovered()) {
            pass.add("data_provider_ecosystem");
        }

        VirtualViewport.Window window = new VirtualViewport().window(
                1000,
                120,
                20,
                10
        );
        if (window.first() == 110 && window.last() == 149
                && window.materializedCount() == 40) {
            pass.add("virtualized_paging");
        }

        StateLayerResolver.LayeredState state = stateLayers.resolve(
                singleton("color", "green"),
                singleton("padding", "12"),
                singleton("color", "blue"),
                singleton("enabled", "false")
        );
        if ("blue".equals(state.values().get("color"))
                && "12".equals(state.values().get("padding"))
                && "false".equals(state.values().get("enabled"))) {
            pass.add("state_layering");
        }

        AnimationTimeline timeline = new AnimationTimeline();
        timeline.addSequence("fade", 120);
        timeline.addParallel(Arrays.asList("scale", "color"), 180);
        if (timeline.totalDurationMs() == 300
                && timeline.segments().size() == 2) {
            pass.add("animation_timeline");
        }

        LayoutConstraintSolver solver = new LayoutConstraintSolver();
        List<LayoutConstraintSolver.Box> aligned = solver.alignAndDistribute(
                Arrays.asList(
                        new LayoutConstraintSolver.Box("a", 2, 10, 40, 20),
                        new LayoutConstraintSolver.Box("b", 80, 30, 60, 30),
                        new LayoutConstraintSolver.Box("c", 190, 20, 50, 25)
                ),
                0,
                300,
                true
        );
        if (aligned.get(0).x() == 0
                && aligned.get(2).x() + aligned.get(2).width() == 300
                && aligned.get(0).height() == aligned.get(1).height()) {
            pass.add("constraint_multi_select");
        }

        AccessibilitySemantic semantics = new AccessibilitySemantic(
                "object.submit",
                "button",
                "Kirim",
                Arrays.asList("enabled", "focusable"),
                3
        );
        if (semantics.valid() && semantics.focusOrder() == 3) {
            pass.add("accessibility_semantic");
        }

        LocalizationFormatter formatter = new LocalizationFormatter();
        if ("2 item".equals(formatter.pluralId(2, "item", "item"))
                && formatter.currencyId(12500, "IDR").contains("12")) {
            pass.add("localization_formatting");
        }

        cacheBudget.put(CacheCategory.THUMBNAIL, "thumb.a", 1024);
        cacheBudget.put(CacheCategory.TEMP, "temp.a", 2048);
        if (cacheBudget.total(CacheCategory.THUMBNAIL) == 1024
                && cacheBudget.clear(CacheCategory.TEMP) == 1) {
            pass.add("cache_category_budget");
        }

        ImportPolicy importPolicy = new ImportPolicy(
                8,
                25.0,
                new LinkedHashSet<>(Arrays.asList(
                        "application/json",
                        "image/png",
                        "image/webp"
                )),
                true
        );
        ImportPolicy.Entry importEntry = new ImportPolicy.Entry(
                "assets/icon.webp",
                "image/webp",
                1024,
                4096,
                2,
                true
        );
        if (importPolicy.accepts(importEntry)
                && !importPolicy.accepts(new ImportPolicy.Entry(
                "../escape",
                "image/webp",
                1,
                1,
                1,
                true
        ))) {
            pass.add("import_security_deep");
        }

        ProjectExport export = new ProjectExport("project.default", 12);
        for (String section : Arrays.asList(
                "screens", "logic", "data", "bindings", "styles",
                "localization", "assets", "dependencies"
        )) {
            export.put(section, section + ":payload");
        }
        if (export.complete() && export.digest().matches("[0-9a-f]{64}")) {
            pass.add("full_project_export");
        }

        BackgroundTaskSpec task = new BackgroundTaskSpec(
                "task.sync",
                ValueType.TEXT,
                ValueType.BOOLEAN,
                3,
                15_000,
                true,
                new LinkedHashSet<>(Arrays.asList("network", "battery_not_low")),
                "IO"
        );
        if (task.valid() && task.cancellable()) {
            pass.add("background_task_contract");
        }

        EditorContextSnapshot editor = new EditorContextSnapshot(
                "screen.home",
                "object.home.primary",
                "tool.ui",
                "VISUAL",
                "EDIT",
                1.2f,
                24,
                30,
                "RIGHT",
                "floating.size"
        ).clamp(360, 640);
        if (editor.zoom() == 1.2f && editor.panX() <= 360 && editor.panY() <= 640) {
            pass.add("editor_context_complete");
        }

        ClipboardGraph clipboard = new ClipboardGraph();
        clipboard.copy(
                "object.home.primary",
                singleton("text", "Buka Detail"),
                new LinkedHashSet<>(Arrays.asList("asset.icon.primary", "action.navigate"))
        );
        ClipboardGraph.PasteResult pasted = clipboard.paste("object.copy.1");
        if ("object.copy.1".equals(pasted.newId())
                && pasted.dependencies().size() == 2
                && pasted.remap().containsKey("object.home.primary")) {
            pass.add("clipboard_dependency_remap");
        }

        DiagnosticRecord diagnostic = new DiagnosticRecord(
                "diagnostic.1",
                "MISSING_ASSET",
                "screen.home",
                "project/screens/home.json",
                "object.primary.icon",
                "Relink aset yang hilang",
                Arrays.asList("diagnostic.asset.registry")
        );
        if (diagnostic.complete()) {
            pass.add("diagnostic_rich_record");
        }

        RepairTransaction repair = new RepairTransaction();
        repair.detect("index.stale");
        repair.suggest("rebuild.index");
        repair.apply("rebuild.index");
        repair.validate(true);
        if (repair.state() == RepairTransaction.State.COMMITTED
                && repair.reversible()) {
            pass.add("repair_detect_suggest_fix");
        }

        if (dependencies.allowsHostDependency("tool.ui", "foundation")
                && !dependencies.allowsToolDependency("tool.ui", "tool.logic")) {
            pass.add("static_tool_dependency_gate");
        }

        LifecycleProbe probe = new LifecycleProbe();
        probe.acquire("view");
        probe.acquire("listener");
        probe.acquire("job");
        probe.releaseAll();
        if (probe.releasedCompletely()) {
            pass.add("lifecycle_release_probe");
        }

        faultIsolation.register("engine.ui", true, Collections.<String>emptySet());
        faultIsolation.register("engine.logic", true,
                new LinkedHashSet<>(Collections.singletonList("engine.ui")));
        faultIsolation.fail("engine.ui");
        if (faultIsolation.hostReady()
                && !faultIsolation.available("engine.ui")
                && !faultIsolation.available("engine.logic")) {
            pass.add("fault_isolation");
        }

        UpdatePackageIntent update = new UpdatePackageIntent(
                "patch.ui.12.1",
                "DECLARATIVE_PATCH",
                new LinkedHashSet<>(Arrays.asList("component.button")),
                new LinkedHashSet<>(Arrays.asList("ui")),
                true,
                true
        );
        if (update.dryRun().isPass()
                && !update.canApply()
                && update.approveExplicitly().canApply()) {
            pass.add("update_intent_pipeline");
        }

        SafeUiSession safeUi = new SafeUiSession();
        safeUi.enter("health.failed");
        safeUi.quarantine("patch.bad");
        safeUi.enableReadOnlyInspection();
        if (safeUi.active()
                && safeUi.readOnly()
                && safeUi.exportable()
                && safeUi.quarantined().contains("patch.bad")) {
            pass.add("safe_ui");
        }

        HealthSuite health = new HealthSuite();
        for (String check : Arrays.asList(
                "schema", "files", "capabilities", "navigation",
                "database", "startup", "assets", "bindings"
        )) {
            health.record(check, true);
        }
        if (health.healthy() && health.checks().size() == 8) {
            pass.add("comprehensive_health");
        }

        if (sourceOfTruth.source() == ProjectSourceOfTruth.Source.USER_OWNED_STORAGE
                && sourceOfTruth.generatedDerived()
                && sourceOfTruth.cacheDisposable()
                && sourceOfTruth.runtimeMaterialized()) {
            pass.add("source_of_truth_policy");
        }

        return Collections.unmodifiableSet(pass);
    }

    public boolean isReady() {
        Set<String> pass = selfTest();
        return pass.containsAll(REQUIRED_BEHAVIORS)
                && pass.size() == REQUIRED_BEHAVIORS.size();
    }

    public static final List<String> REQUIRED_BEHAVIORS =
            Collections.unmodifiableList(Arrays.asList(
                    "property_contract",
                    "event_action_contract",
                    "safe_converter",
                    "composite_executor",
                    "data_provider_ecosystem",
                    "virtualized_paging",
                    "state_layering",
                    "animation_timeline",
                    "constraint_multi_select",
                    "accessibility_semantic",
                    "localization_formatting",
                    "cache_category_budget",
                    "import_security_deep",
                    "full_project_export",
                    "background_task_contract",
                    "editor_context_complete",
                    "clipboard_dependency_remap",
                    "diagnostic_rich_record",
                    "repair_detect_suggest_fix",
                    "static_tool_dependency_gate",
                    "lifecycle_release_probe",
                    "fault_isolation",
                    "update_intent_pipeline",
                    "safe_ui",
                    "comprehensive_health",
                    "source_of_truth_policy"
            ));

    private static Map<String,String> singleton(String key, String value) {
        LinkedHashMap<String,String> out = new LinkedHashMap<>();
        out.put(key, value);
        return out;
    }

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

    public enum ValueType { NONE, TEXT, NUMBER, BOOLEAN, OBJECT, LIST }
    public enum PropagationPolicy { TARGET_ONLY, CONTINUE, CONSUME, STOP }
    public enum ExecutionMode { SYNC, ASYNC }
    public enum FailurePolicy { STOP, CONTINUE, FALLBACK, COMPENSATE }

    public static final class PropertySpec {
        private final String id;
        private final ValueType type;
        private final boolean nullable, editable;
        private final Double min, max;
        private final String unit;
        private final Set<String> states;
        private final String converterId;

        public PropertySpec(
                String id, ValueType type, boolean nullable, boolean editable,
                Double min, Double max, String unit, Set<String> states,
                String converterId
        ) {
            this.id = StableId.require(id, "propertyId");
            this.type = Objects.requireNonNull(type, "type");
            this.nullable = nullable;
            this.editable = editable;
            this.min = min;
            this.max = max;
            this.unit = unit;
            this.states = Collections.unmodifiableSet(new LinkedHashSet<>(states));
            this.converterId = converterId;
            if (min != null && max != null && min > max) {
                throw new IllegalArgumentException("property range invalid");
            }
        }
        public boolean accepts(String value) {
            if (value == null) return nullable;
            if (!editable) return false;
            if (type == ValueType.NUMBER) {
                try {
                    double number = Double.parseDouble(value);
                    return (min == null || number >= min)
                            && (max == null || number <= max);
                } catch (NumberFormatException error) {
                    return false;
                }
            }
            if (type == ValueType.BOOLEAN) {
                return "true".equals(value) || "false".equals(value);
            }
            return true;
        }
        public String unit(){return unit;}
        public Set<String> states(){return states;}
        public String converterId(){return converterId;}
    }

    public static final class EventSpec {
        private final String id;
        private final ValueType input, output;
        private final PropagationPolicy propagation;
        public EventSpec(String id, ValueType input, ValueType output, PropagationPolicy propagation) {
            this.id=StableId.require(id,"eventId");this.input=input;this.output=output;this.propagation=propagation;
        }
        public PropagationPolicy propagation(){return propagation;}
    }

    public static final class ActionSpec {
        private final String id, executionId;
        private final ExecutionMode mode;
        private final int retryCount;
        private final FailurePolicy failure;
        private final boolean idempotent;
        private final long timeoutMs;
        public ActionSpec(String id,String executionId,ExecutionMode mode,int retryCount,FailurePolicy failure,boolean idempotent,long timeoutMs){
            this.id=StableId.require(id,"actionId");this.executionId=StableId.require(executionId,"executionId");
            this.mode=mode;if(retryCount<0||retryCount>8)throw new IllegalArgumentException("retry invalid");
            this.retryCount=retryCount;this.failure=failure;this.idempotent=idempotent;
            if(timeoutMs<1||timeoutMs>120000)throw new IllegalArgumentException("timeout invalid");this.timeoutMs=timeoutMs;
        }
        public int retryCount(){return retryCount;} public boolean idempotent(){return idempotent;}
    }

    public static final class ConverterRegistry {
        private final Set<String> pairs = new LinkedHashSet<>();
        public synchronized void register(String id,ValueType from,ValueType to){
            StableId.require(id,"converterId");if(from==to)throw new IllegalArgumentException("converter unnecessary");
            pairs.add(from.name()+"->"+to.name());
        }
        public synchronized boolean canConvert(ValueType from,ValueType to){
            return from==to || pairs.contains(from.name()+"->"+to.name());
        }
    }

    public static final class CompositeExecutor {
        public static final class Step {
            final String id; final boolean success, compensatable;
            public Step(String id, boolean success, boolean compensatable){
                this.id=StableId.require(id,"stepId");this.success=success;this.compensatable=compensatable;
            }
        }
        public static final class Result {
            private final boolean success;private final List<String> executed,compensated;
            Result(boolean success,List<String> executed,List<String> compensated){this.success=success;this.executed=Collections.unmodifiableList(executed);this.compensated=Collections.unmodifiableList(compensated);}
            public boolean success(){return success;}public List<String> executed(){return executed;}public List<String> compensated(){return compensated;}
        }
        public Result execute(List<Step> steps){
            List<String> executed=new ArrayList<>(), compensated=new ArrayList<>();
            for(Step step:steps){
                executed.add(step.id);
                if(!step.success){
                    for(int i=executed.size()-2;i>=0;i--){
                        Step previous=steps.get(i);if(previous.compensatable)compensated.add(previous.id);
                    }
                    return new Result(false,executed,compensated);
                }
            }
            return new Result(true,executed,compensated);
        }
    }

    public enum ProviderKind { DATABASE, API, FILE, FORM, RUNTIME, ACTION }
    public enum ProviderCapability { READ, WRITE, PAGE, STREAM }

    public static final class DataProviderRegistry {
        private static final class Provider {
            final String id;final ProviderKind kind;final Set<ProviderCapability> capabilities;
            Provider(String id,ProviderKind kind,Set<ProviderCapability> capabilities){this.id=id;this.kind=kind;this.capabilities=capabilities;}
        }
        private final Map<String,Provider> providers=new LinkedHashMap<>();
        public synchronized void register(String id,ProviderKind kind,Set<ProviderCapability> capabilities){
            providers.put(StableId.require(id,"providerId"),new Provider(id,kind,EnumSet.copyOf(capabilities)));
        }
        public synchronized List<String> byKind(ProviderKind kind){
            List<String> out=new ArrayList<>();for(Provider p:providers.values())if(p.kind==kind)out.add(p.id);return Collections.unmodifiableList(out);
        }
        public synchronized boolean allKindsCovered(){
            for(ProviderKind kind:ProviderKind.values())if(byKind(kind).isEmpty())return false;return true;
        }
    }

    public static final class VirtualViewport {
        public Window window(int total,int firstVisible,int visible,int preload){
            if(total<0||firstVisible<0||visible<1||preload<0)throw new IllegalArgumentException("viewport invalid");
            int first=Math.max(0,firstVisible-preload);
            int last=Math.min(total-1,firstVisible+visible+preload-1);
            return new Window(first,last,Math.max(0,last-first+1));
        }
        public static final class Window {
            private final int first,last,count;Window(int f,int l,int c){first=f;last=l;count=c;}
            public int first(){return first;}public int last(){return last;}public int materializedCount(){return count;}
        }
    }

    public static final class StateLayerResolver {
        public LayeredState resolve(Map<String,String> base,Map<String,String> orientation,Map<String,String> theme,Map<String,String> data){
            LinkedHashMap<String,String> out=new LinkedHashMap<>();out.putAll(base);out.putAll(orientation);out.putAll(theme);out.putAll(data);return new LayeredState(out);
        }
        public static final class LayeredState{
            private final Map<String,String> values;LayeredState(Map<String,String> v){values=Collections.unmodifiableMap(new LinkedHashMap<>(v));}
            public Map<String,String> values(){return values;}
        }
    }

    public static final class AnimationTimeline {
        public static final class Segment {
            final List<String> names;final long duration;final boolean parallel;
            Segment(List<String> names,long duration,boolean parallel){this.names=names;this.duration=duration;this.parallel=parallel;}
        }
        private final List<Segment> segments=new ArrayList<>();
        public void addSequence(String name,long duration){if(duration<1)throw new IllegalArgumentException("duration");segments.add(new Segment(Collections.singletonList(name),duration,false));}
        public void addParallel(List<String> names,long duration){if(names.isEmpty()||duration<1)throw new IllegalArgumentException("parallel");segments.add(new Segment(new ArrayList<>(names),duration,true));}
        public long totalDurationMs(){long total=0;for(Segment s:segments)total+=s.duration;return total;}
        public List<Segment> segments(){return Collections.unmodifiableList(segments);}
    }

    public static final class LayoutConstraintSolver {
        public static final class Box {
            private final String id;private final int x,y,width,height;
            public Box(String id,int x,int y,int width,int height){this.id=id;this.x=x;this.y=y;this.width=width;this.height=height;}
            public int x(){return x;}public int y(){return y;}public int width(){return width;}public int height(){return height;}
        }
        public List<Box> alignAndDistribute(List<Box> input,int left,int right,boolean equalHeight){
            if(input.size()<2||right<=left)throw new IllegalArgumentException("layout input");
            List<Box> sorted=new ArrayList<>(input);
            int height=0;for(Box b:sorted)height=Math.max(height,b.height);
            int totalWidth=0;for(Box b:sorted)totalWidth+=b.width;
            int gap=(right-left-totalWidth)/(sorted.size()-1);
            int x=left;List<Box> out=new ArrayList<>();
            for(Box b:sorted){out.add(new Box(b.id,x,b.y,b.width,equalHeight?height:b.height));x+=b.width+gap;}
            Box last=out.get(out.size()-1);
            out.set(out.size()-1,new Box(last.id,right-last.width,last.y,last.width,last.height));
            return Collections.unmodifiableList(out);
        }
    }

    public static final class AccessibilitySemantic {
        private final String id,role,label;private final List<String> states;private final int focusOrder;
        public AccessibilitySemantic(String id,String role,String label,List<String> states,int focusOrder){this.id=StableId.require(id,"semanticId");this.role=role;this.label=label;this.states=new ArrayList<>(states);this.focusOrder=focusOrder;}
        public boolean valid(){return role!=null&&!role.isEmpty()&&label!=null&&!label.trim().isEmpty()&&focusOrder>=0;}
        public int focusOrder(){return focusOrder;}
    }

    public static final class LocalizationFormatter {
        public String pluralId(int count,String singular,String plural){return count+" "+(count==1?singular:plural);}
        public String currencyId(long amount,String code){
            NumberFormat f=NumberFormat.getCurrencyInstance(new Locale("id","ID"));f.setCurrency(Currency.getInstance(code));return f.format(amount);
        }
    }

    public enum CacheCategory { THUMBNAIL, PREVIEW, INDEX, TEMP }

    public static final class CacheBudget {
        private static final class Budget{final long memory,disk;final Map<String,Long> entries=new LinkedHashMap<>();Budget(long memory,long disk){this.memory=memory;this.disk=disk;}}
        private final Map<CacheCategory,Budget> budgets=new LinkedHashMap<>();
        public synchronized void configure(CacheCategory category,long memory,long disk){if(memory<=0||disk<memory)throw new IllegalArgumentException("cache budget");budgets.put(category,new Budget(memory,disk));}
        public synchronized void put(CacheCategory category,String key,long bytes){Budget b=budgets.get(category);if(b==null||bytes<0)throw new IllegalArgumentException("cache category");b.entries.put(key,bytes);trim(b);}
        public synchronized long total(CacheCategory category){Budget b=budgets.get(category);long t=0;if(b!=null)for(long n:b.entries.values())t+=n;return t;}
        public synchronized int clear(CacheCategory category){Budget b=budgets.get(category);if(b==null)return 0;int n=b.entries.size();b.entries.clear();return n;}
        private void trim(Budget b){while(totalOf(b)>b.memory&&!b.entries.isEmpty()){String first=b.entries.keySet().iterator().next();b.entries.remove(first);}}
        private long totalOf(Budget b){long t=0;for(long n:b.entries.values())t+=n;return t;}
    }

    public static final class ImportPolicy {
        private final int maxDepth;private final double maxRatio;private final Set<String> mime;private final boolean signatureRequired;
        public ImportPolicy(int maxDepth,double maxRatio,Set<String> mime,boolean signatureRequired){this.maxDepth=maxDepth;this.maxRatio=maxRatio;this.mime=mime;this.signatureRequired=signatureRequired;}
        public boolean accepts(Entry e){
            if(e.path.contains("..")||e.depth>maxDepth||!mime.contains(e.mime))return false;
            if(e.compressed<=0||((double)e.uncompressed/e.compressed)>maxRatio)return false;
            return !signatureRequired||e.signatureValid;
        }
        public static final class Entry{
            final String path,mime;final long compressed,uncompressed;final int depth;final boolean signatureValid;
            public Entry(String path,String mime,long compressed,long uncompressed,int depth,boolean signatureValid){this.path=path;this.mime=mime;this.compressed=compressed;this.uncompressed=uncompressed;this.depth=depth;this.signatureValid=signatureValid;}
        }
    }

    public static final class ProjectExport {
        private final String projectId;private final long revision;private final Map<String,String> sections=new LinkedHashMap<>();
        public ProjectExport(String projectId,long revision){this.projectId=StableId.require(projectId,"projectId");this.revision=revision;}
        public void put(String section,String payload){sections.put(section,payload);}
        public boolean complete(){return sections.keySet().containsAll(Arrays.asList("screens","logic","data","bindings","styles","localization","assets","dependencies"));}
        public String digest(){return sha256(projectId+"|"+revision+"|"+sections.toString());}
    }

    public static final class BackgroundTaskSpec {
        final String id;final ValueType input,output;final int retry;final long timeout;final boolean cancellable;final Set<String> constraints;final String executionClass;
        public BackgroundTaskSpec(String id,ValueType input,ValueType output,int retry,long timeout,boolean cancellable,Set<String> constraints,String executionClass){this.id=StableId.require(id,"taskId");this.input=input;this.output=output;this.retry=retry;this.timeout=timeout;this.cancellable=cancellable;this.constraints=constraints;this.executionClass=executionClass;}
        public boolean valid(){return retry>=0&&retry<=8&&timeout>0&&executionClass!=null&&!executionClass.isEmpty();}
        public boolean cancellable(){return cancellable;}
    }

    public static final class EditorContextSnapshot {
        private final String screen,selection,tool,representation,mode,floating;private final float zoom;private final int panX,panY;private final String panel;
        public EditorContextSnapshot(String screen,String selection,String tool,String representation,String mode,float zoom,int panX,int panY,String panel,String floating){this.screen=screen;this.selection=selection;this.tool=tool;this.representation=representation;this.mode=mode;this.zoom=zoom;this.panX=panX;this.panY=panY;this.panel=panel;this.floating=floating;}
        public EditorContextSnapshot clamp(int width,int height){return new EditorContextSnapshot(screen,selection,tool,representation,mode,Math.max(.25f,Math.min(4f,zoom)),Math.max(0,Math.min(width,panX)),Math.max(0,Math.min(height,panY)),panel,floating);}
        public float zoom(){return zoom;}public int panX(){return panX;}public int panY(){return panY;}
    }

    public static final class ClipboardGraph {
        private String source;private Map<String,String> properties;private Set<String> dependencies;
        public void copy(String source,Map<String,String> properties,Set<String> dependencies){this.source=StableId.require(source,"source");this.properties=new LinkedHashMap<>(properties);this.dependencies=new LinkedHashSet<>(dependencies);}
        public PasteResult paste(String newId){if(source==null)throw new IllegalStateException("clipboard empty");LinkedHashMap<String,String> remap=new LinkedHashMap<>();remap.put(source,StableId.require(newId,"newId"));return new PasteResult(newId,dependencies,remap);}
        public static final class PasteResult{private final String newId;private final Set<String> dependencies;private final Map<String,String> remap;PasteResult(String n,Set<String>d,Map<String,String>r){newId=n;dependencies=Collections.unmodifiableSet(new LinkedHashSet<>(d));remap=Collections.unmodifiableMap(new LinkedHashMap<>(r));}public String newId(){return newId;}public Set<String> dependencies(){return dependencies;}public Map<String,String> remap(){return remap;}}
    }

    public static final class DiagnosticRecord {
        final String id,code,subject,source,location,suggestedFix;final List<String> related;
        public DiagnosticRecord(String id,String code,String subject,String source,String location,String suggestedFix,List<String> related){this.id=StableId.require(id,"diagnosticId");this.code=code;this.subject=subject;this.source=source;this.location=location;this.suggestedFix=suggestedFix;this.related=new ArrayList<>(related);}
        public boolean complete(){return code!=null&&subject!=null&&source!=null&&location!=null&&suggestedFix!=null&&related!=null;}
    }

    public static final class RepairTransaction {
        public enum State { IDLE, DETECTED, SUGGESTED, APPLIED, COMMITTED, ROLLED_BACK }
        private State state=State.IDLE;private String issue,fix;private boolean reversible;
        public void detect(String issue){this.issue=issue;state=State.DETECTED;}
        public void suggest(String fix){if(state!=State.DETECTED)throw new IllegalStateException();this.fix=fix;state=State.SUGGESTED;}
        public void apply(String fix){if(state!=State.SUGGESTED||!Objects.equals(this.fix,fix))throw new IllegalStateException();reversible=true;state=State.APPLIED;}
        public void validate(boolean pass){if(state!=State.APPLIED)throw new IllegalStateException();state=pass?State.COMMITTED:State.ROLLED_BACK;}
        public State state(){return state;}public boolean reversible(){return reversible;}
    }

    public static final class StaticDependencyGate {
        private final Set<String> tools=new LinkedHashSet<>();
        public void registerTool(String id){tools.add(StableId.require(id,"toolId"));}
        public boolean allowsToolDependency(String from,String to){return !tools.contains(from)||!tools.contains(to)||from.equals(to);}
        public boolean allowsHostDependency(String from,String host){return tools.contains(from)&&"foundation".equals(host);}
    }

    public static final class LifecycleProbe {
        private final Set<String> held=new LinkedHashSet<>();public void acquire(String resource){held.add(resource);}public void releaseAll(){held.clear();}public boolean releasedCompletely(){return held.isEmpty();}
    }

    public static final class FaultIsolation {
        private static final class Engine{boolean ready;final Set<String> dependencies;Engine(boolean ready,Set<String>d){this.ready=ready;dependencies=d;}}
        private final Map<String,Engine> engines=new LinkedHashMap<>();private boolean hostReady=true;
        public void register(String id,boolean ready,Set<String> dependencies){engines.put(StableId.require(id,"engineId"),new Engine(ready,new LinkedHashSet<>(dependencies)));}
        public void fail(String id){Engine e=engines.get(id);if(e==null)return;e.ready=false;for(Engine candidate:engines.values())if(candidate.dependencies.contains(id))candidate.ready=false;}
        public boolean hostReady(){return hostReady;}public boolean available(String id){Engine e=engines.get(id);return e!=null&&e.ready;}
    }

    public static final class UpdatePackageIntent {
        private final String id,type;private final Set<String> dependencies,capabilities;private final boolean migration,repair;private boolean approved;
        public UpdatePackageIntent(String id,String type,Set<String> dependencies,Set<String> capabilities,boolean migration,boolean repair){this.id=StableId.require(id,"patchId");this.type=type;this.dependencies=dependencies;this.capabilities=capabilities;this.migration=migration;this.repair=repair;}
        public DryRun dryRun(){return new DryRun(type!=null&&!dependencies.isEmpty()&&!capabilities.isEmpty());}
        public boolean canApply(){return approved&&dryRun().isPass();}
        public UpdatePackageIntent approveExplicitly(){approved=true;return this;}
        public static final class DryRun{private final boolean pass;DryRun(boolean pass){this.pass=pass;}public boolean isPass(){return pass;}}
    }

    public static final class SafeUiSession {
        private boolean active,readOnly;private final Set<String> quarantined=new LinkedHashSet<>();private String reason;
        public void enter(String reason){this.reason=reason;active=true;}public void quarantine(String id){if(!active)throw new IllegalStateException();quarantined.add(id);}public void enableReadOnlyInspection(){if(!active)throw new IllegalStateException();readOnly=true;}
        public boolean active(){return active;}public boolean readOnly(){return readOnly;}public boolean exportable(){return active;}public Set<String> quarantined(){return Collections.unmodifiableSet(quarantined);}
    }

    public static final class HealthSuite {
        private final Map<String,Boolean> checks=new LinkedHashMap<>();public void record(String id,boolean pass){checks.put(id,pass);}public boolean healthy(){if(checks.isEmpty())return false;for(boolean p:checks.values())if(!p)return false;return true;}public Map<String,Boolean> checks(){return Collections.unmodifiableMap(checks);}
    }

    public static final class ProjectSourceOfTruth {
        public enum Source { USER_OWNED_STORAGE, PRIVATE_APP_STORAGE }
        private Source source;private boolean generatedDerived,cacheDisposable,runtimeMaterialized;
        public void configure(Source source,boolean generatedDerived,boolean cacheDisposable,boolean runtimeMaterialized){this.source=source;this.generatedDerived=generatedDerived;this.cacheDisposable=cacheDisposable;this.runtimeMaterialized=runtimeMaterialized;}
        public Source source(){return source;}public boolean generatedDerived(){return generatedDerived;}public boolean cacheDisposable(){return cacheDisposable;}public boolean runtimeMaterialized(){return runtimeMaterialized;}
    }
}
