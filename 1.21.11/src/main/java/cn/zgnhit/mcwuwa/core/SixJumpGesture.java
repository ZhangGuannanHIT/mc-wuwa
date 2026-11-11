package cn.zgnhit.mcwuwa.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Client gesture classification only; server independently verifies owner and elapsed ticks. */
public final class SixJumpGesture {
    private String owner="";
    private boolean active,held,blocked;
    private int ticks;
    public List<String> poll(String currentOwner,boolean special,boolean down) {
        Objects.requireNonNull(currentOwner);var result=new ArrayList<String>();
        if(active&&(!special||!owner.equals(currentOwner))){result.add("SIX_JUMP_CANCEL");active=false;blocked=down;}
        if(!down){
            if(active){result.add("SIX_JUMP_RELEASE");if(!held)result.add("JUMP");}
            active=held=blocked=false;ticks=0;owner="";return List.copyOf(result);
        }
        if(blocked||!special)return List.copyOf(result);
        if(!active){active=true;held=false;ticks=0;owner=currentOwner;result.add("SIX_JUMP_PRESS");return List.copyOf(result);}
        if(++ticks>=8&&!held){held=true;result.add("SIX_JUMP_HOLD");}
        return List.copyOf(result);
    }
    public boolean cancel(boolean currentlyDown){boolean was=active;active=held=false;ticks=0;owner="";blocked=currentlyDown;return was;}
    public void reset(){active=held=blocked=false;ticks=0;owner="";}
}
