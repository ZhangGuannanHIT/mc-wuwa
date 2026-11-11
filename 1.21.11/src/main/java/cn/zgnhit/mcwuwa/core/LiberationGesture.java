package cn.zgnhit.mcwuwa.core;

/** One physical gesture belongs to one original weapon. No Minecraft or network side effects. */
public final class LiberationGesture {
    public enum Mode { IMMEDIATE, CHARGE_RELEASE, HOLD_ALTERNATIVE }
    public static final int HOLD_TICKS=4; // Existing MC key discrimination threshold, not an original animation frame claim.
    private String owner="";
    private Mode mode=Mode.IMMEDIATE;
    private boolean down,held,suppressed;
    private int ticks;
    public String poll(String currentOwner,Mode currentMode,boolean pressed,boolean clicked){
        if(!owner.equals(currentOwner)){
            boolean block=(down||suppressed)&&pressed;reset();owner=currentOwner;suppressed=block;down=block;
        }
        if(suppressed){if(!pressed)reset();return "";}
        if(pressed&&!down){mode=currentMode;ticks=0;held=false;down=true;
            if(mode==Mode.IMMEDIATE)return "LIBERATION";
        }
        if(pressed){
            if(mode!=Mode.IMMEDIATE&&!held&&++ticks>=HOLD_TICKS){held=true;return "LIBERATION_HOLD";}
            return "";
        }
        if(down){
            String result=mode==Mode.IMMEDIATE?"":held?mode==Mode.CHARGE_RELEASE?"LIBERATION_RELEASE":"":"LIBERATION";
            reset();return result;
        }
        return clicked?"LIBERATION":"";
    }
    /** A curtain already cast cannot be undone. Only an active charge needs a server cancel. */
    public boolean cancel(){
        boolean notify=down&&held&&mode==Mode.CHARGE_RELEASE;
        boolean block=down;String previousOwner=owner;reset();owner=previousOwner;down=block;suppressed=block;return notify;
    }
    public void reset(){owner="";mode=Mode.IMMEDIATE;down=false;held=false;suppressed=false;ticks=0;}
}
