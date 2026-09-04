package com.toolbox.tools.delivery;

public final class PatchApplyResult {
    public enum State {
        APPLIED,
        RESTORED,
        REJECTED,
        FAILED_SAFE
    }

    private final State state;
    private final String message;
    private final long revision;

    public PatchApplyResult(State state,String message,long revision){
        this.state=java.util.Objects.requireNonNull(state,"state");
        this.message=java.util.Objects.requireNonNull(message,"message");
        this.revision=revision;
    }

    public State state(){return state;}
    public String message(){return message;}
    public long revision(){return revision;}
    public boolean isPass(){
        return state==State.APPLIED||state==State.RESTORED;
    }
}
