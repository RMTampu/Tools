package io.toolbox.stagea.android;

import java.util.LinkedHashMap;
import java.util.Map;

public final class HostPureJvmSelfTest {
    private static int cases;
    public static void main(String[] args) {
        roundTrip();
        deterministic();
        corruptionRejected();
        bounds();
        resourceMath();
        System.out.println("STAGE_A_ANDROID_HOST_JVM_TESTS = PASS");
        System.out.println("HOST_JVM_TEST_CASES=" + cases);
    }
    private static void roundTrip() {
        Map<String,String> map=new LinkedHashMap<>();
        map.put("kernel.state","STOPPED"); map.put("stage.a.recovery.state","SAFE_MODE");
        check(StateFileCodec.decode(StateFileCodec.encode(map)).equals(map)); cases++;
    }
    private static void deterministic() {
        Map<String,String> a=new LinkedHashMap<>(); a.put("b","2"); a.put("a","1");
        Map<String,String> b=new LinkedHashMap<>(); b.put("a","1"); b.put("b","2");
        check(java.util.Arrays.equals(StateFileCodec.encode(a),StateFileCodec.encode(b))); cases++;
    }
    private static void corruptionRejected() {
        Map<String,String> map=new LinkedHashMap<>(); map.put("kernel.state","STOPPED");
        byte[] data=StateFileCodec.encode(map);
        byte[] needle="STOPPED".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int offset=-1;
        outer: for(int i=0;i<=data.length-needle.length;i++){
            for(int j=0;j<needle.length;j++) if(data[i+j]!=needle[j]) continue outer;
            offset=i; break;
        }
        check(offset>=0);
        data[offset]=(byte)'R';
        boolean failed=false; try { StateFileCodec.decode(data); } catch(RuntimeException expected){ failed=true; }
        check(failed); cases++;
    }
    private static void bounds() {
        boolean failed=false; try { StateFileCodec.requireKey("../bad"); } catch(RuntimeException expected){ failed=true; }
        check(failed); cases++;
    }
    private static void resourceMath() {
        check(NormalizedResourceMath.normalizedUsage(0,100,false)==0);
        check(NormalizedResourceMath.normalizedUsage(80,100,false)==8000);
        check(NormalizedResourceMath.normalizedUsage(1,100,true)>NormalizedResourceMath.NORMALIZED_BUDGET);
        cases++;
    }
    private static void check(boolean value){ if(!value) throw new AssertionError("case "+cases); }
}
