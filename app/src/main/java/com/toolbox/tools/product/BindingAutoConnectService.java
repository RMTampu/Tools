package com.toolbox.tools.product;

import com.toolbox.tools.core.ProjectManager;
import com.toolbox.tools.library.ComponentDefinition;
import com.toolbox.tools.library.ComponentInstance;
import com.toolbox.tools.library.ComponentRegistry;
import com.toolbox.tools.library.PropertyContract;
import com.toolbox.tools.runtime.BindingDefinition;
import com.toolbox.tools.runtime.BindingMode;
import com.toolbox.tools.runtime.BindingValidator;
import com.toolbox.tools.runtime.DataFieldDefinition;
import com.toolbox.tools.runtime.DataSourceDefinition;
import com.toolbox.tools.runtime.RuntimeEnvironment;
import com.toolbox.tools.runtime.ScreenDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class BindingAutoConnectService {
    public enum Status {
        CONNECTED,
        NO_MATCH,
        AMBIGUOUS,
        INVALID_TARGET
    }

    public static final class Candidate {
        private final String sourceId;
        private final String sourceFieldId;
        private final String targetInstanceId;
        private final String targetPropertyId;

        Candidate(
                String sourceId,
                String sourceFieldId,
                String targetInstanceId,
                String targetPropertyId
        ) {
            this.sourceId=sourceId;
            this.sourceFieldId=sourceFieldId;
            this.targetInstanceId=targetInstanceId;
            this.targetPropertyId=targetPropertyId;
        }

        public String sourceId(){return sourceId;}
        public String sourceFieldId(){return sourceFieldId;}
        public String targetInstanceId(){return targetInstanceId;}
        public String targetPropertyId(){return targetPropertyId;}

        public String canonical() {
            return sourceId + "." + sourceFieldId
                    + " -> "
                    + targetInstanceId + "." + targetPropertyId;
        }
    }

    public static final class Result {
        private final Status status;
        private final List<Candidate> candidates;
        private final String report;

        Result(
                Status status,
                List<Candidate> candidates,
                String report
        ) {
            this.status=status;
            this.candidates=Collections.unmodifiableList(
                    new ArrayList<>(candidates)
            );
            this.report=report;
        }

        public Status status(){return status;}
        public List<Candidate> candidates(){return candidates;}
        public String report(){return report;}
        public boolean connected(){return status==Status.CONNECTED;}
    }

    private final RuntimeEnvironment runtime;
    private final ProjectManager projects;
    private final DiagnosticCenter diagnostics;
    private final BindingValidator validator =
            new BindingValidator();

    public BindingAutoConnectService(
            RuntimeEnvironment runtime,
            ProjectManager projects,
            DiagnosticCenter diagnostics
    ) {
        this.runtime=Objects.requireNonNull(runtime,"runtime");
        this.projects=Objects.requireNonNull(projects,"projects");
        this.diagnostics=Objects.requireNonNull(
                diagnostics,
                "diagnostics"
        );
    }

    public synchronized Result connect(
            String targetInstanceId,
            String targetPropertyId
    ) {
        ComponentInstance target=findInstance(targetInstanceId);
        if(target==null){
            return result(
                    Status.INVALID_TARGET,
                    Collections.emptyList(),
                    "Target instance tidak tersedia."
            );
        }
        ComponentDefinition component =
                runtime.components().resolveExact(
                        target.componentId(),
                        target.componentVersion()
                );
        if(component==null
                || !component.properties()
                    .containsKey(targetPropertyId)){
            return result(
                    Status.INVALID_TARGET,
                    Collections.emptyList(),
                    "Target property tidak tersedia."
            );
        }

        List<Candidate> matches=new ArrayList<>();
        for(DataSourceDefinition source
                : runtime.model().dataSources().values()){
            for(DataFieldDefinition field
                    : source.fields().values()){
                BindingDefinition probe =
                        new BindingDefinition(
                                "binding.probe."
                                        + matches.size(),
                                source.sourceId(),
                                field.fieldId(),
                                target.instanceId(),
                                targetPropertyId,
                                field.type(),
                                BindingMode.ONE_WAY
                        );
                if(validator.isCompatible(
                        probe,
                        source,
                        target,
                        runtime.components())){
                    matches.add(new Candidate(
                            source.sourceId(),
                            field.fieldId(),
                            target.instanceId(),
                            targetPropertyId
                    ));
                }
            }
        }
        matches.sort(
                Comparator.comparing(Candidate::canonical)
        );

        if(matches.isEmpty()){
            return result(
                    Status.NO_MATCH,
                    matches,
                    "AUTO CONNECT: tidak ada sumber kompatibel."
            );
        }
        if(matches.size()>1){
            String report=report(
                    Status.AMBIGUOUS,
                    matches
            );
            diagnostics.add(
                    "diagnostic.binding.autoconnect.ambiguous",
                    "ERROR",
                    "BINDING_AMBIGUOUS",
                    target.instanceId(),
                    "binding-auto-connect",
                    target.instanceId(),
                    "editor.binding",
                    "Auto Connect menemukan lebih dari satu sumber kompatibel.",
                    "Pilih sumber secara eksplisit; ToolBox tidak menebak.",
                    Collections.emptyList()
            );
            return new Result(
                    Status.AMBIGUOUS,
                    matches,
                    report
            );
        }

        Candidate selected=matches.get(0);
        LinkedHashMap<String,String> update =
                new LinkedHashMap<>();
        String prefix="binding.autoconnect."
                + normalize(target.instanceId())
                + "."
                + normalize(targetPropertyId);
        update.put(prefix+".mode","one_way");
        update.put(
                prefix+".source",
                selected.sourceId()
        );
        update.put(
                prefix+".field",
                selected.sourceFieldId()
        );
        update.put(
                prefix+".target",
                selected.targetInstanceId()
        );
        update.put(
                prefix+".property",
                selected.targetPropertyId()
        );
        projects.applyResourceTransaction(
                update,
                Collections.emptySet()
        );
        return new Result(
                Status.CONNECTED,
                matches,
                report(Status.CONNECTED,matches)
        );
    }

    public synchronized void disconnect(
            String targetInstanceId,
            String targetPropertyId
    ) {
        String prefix="binding.autoconnect."
                + normalize(targetInstanceId)
                + "."
                + normalize(targetPropertyId);
        java.util.LinkedHashSet<String> deletes =
                new java.util.LinkedHashSet<>();
        for(String key:projects.current().resources().keySet()){
            if(key.startsWith(prefix+".")){
                deletes.add(key);
            }
        }
        if(!deletes.isEmpty()){
            projects.applyResourceTransaction(
                    Collections.emptyMap(),
                    deletes
            );
        }
    }

    public synchronized Result inspect(
            String targetInstanceId,
            String targetPropertyId
    ) {
        String prefix="binding.autoconnect."
                + normalize(targetInstanceId)
                + "."
                + normalize(targetPropertyId);
        Map<String,String> resources =
                projects.current().resources();
        if(!resources.containsKey(prefix+".source")){
            return new Result(
                    Status.NO_MATCH,
                    Collections.emptyList(),
                    "AUTO CONNECT: belum ada binding tersimpan."
            );
        }
        Candidate candidate=new Candidate(
                resources.get(prefix+".source"),
                resources.get(prefix+".field"),
                resources.get(prefix+".target"),
                resources.get(prefix+".property")
        );
        return new Result(
                Status.CONNECTED,
                Collections.singletonList(candidate),
                report(
                        Status.CONNECTED,
                        Collections.singletonList(candidate)
                )
        );
    }

    private Result result(
            Status status,
            List<Candidate> candidates,
            String message
    ) {
        return new Result(
                status,
                candidates,
                message + "\n" + report(status,candidates)
        );
    }

    private static String report(
            Status status,
            List<Candidate> candidates
    ) {
        StringBuilder out=new StringBuilder();
        out.append("TOOLBOX_BINDING_AUTOCONNECT_V1\n");
        out.append("STATUS=").append(status.name()).append('\n');
        out.append("CANDIDATES=")
                .append(candidates.size())
                .append('\n');
        for(Candidate item:candidates){
            out.append("CANDIDATE=")
                    .append(item.canonical())
                    .append('\n');
        }
        out.append("POLICY=")
                .append(candidates.size()==1
                        ? "DETERMINISTIC"
                        : "NO_GUESS")
                .append('\n');
        return out.toString();
    }

    private ComponentInstance findInstance(String id) {
        if(id==null)return null;
        for(ScreenDefinition screen
                : runtime.model().screens().values()){
            for(ComponentInstance item:screen.components()){
                if(id.equals(item.instanceId()))return item;
            }
        }
        return null;
    }

    private static String normalize(String value) {
        if(value==null)throw new IllegalArgumentException(
                "binding target kosong"
        );
        String normalized=value
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]","_");
        if(normalized.isEmpty()){
            throw new IllegalArgumentException(
                    "binding target tidak valid"
            );
        }
        return normalized;
    }
}
