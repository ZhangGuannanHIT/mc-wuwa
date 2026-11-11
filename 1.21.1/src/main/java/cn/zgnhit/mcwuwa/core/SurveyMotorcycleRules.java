package cn.zgnhit.mcwuwa.core;

/** Lynae inherent4: +600 capacity. Other vehicle numbers are explicit Minecraft adaptations. */
public final class SurveyMotorcycleRules {
    private SurveyMotorcycleRules() { }
    public static double capacity(boolean lynaeInTeam){return 1000+(lynaeInTeam?600:0);}
    public static double advance(double stored,double capacity,boolean boosting,boolean moving,int ticks){
        Checks.nonnegative("vehicle kinetic",stored);Checks.positive("vehicle capacity",capacity);
        if(ticks<0||ticks>20)throw new IllegalArgumentException("vehicle step");
        return Math.clamp(stored+(boosting?(moving?-2d/3:0):.3)*ticks,0,capacity);
    }
    public static final int HOLD_TICKS=8, LEAP_CAPACITY=9, LEAP_RECOVERY_TICKS=40, GLIDE_CAPACITY=600;
    public enum Lift { NONE, DOLPHIN, CHARGED }
    /** Server input clock. Short airborne input toggles wings; long input never also toggles them. */
    public static final class Aerial {
        private final long[] pressedAt={-1,-1};
        private final boolean[] holdConsumed=new boolean[2],pressOnGround=new boolean[2];
        private long lastLift=-100;
        private boolean gliding;
        private int charges=LEAP_CAPACITY,recovery,glideEnergy=GLIDE_CAPACITY,airDolphins;
        public int charges(){return charges;}
        public int recovery(){return recovery;}
        public int energy(){return glideEnergy;}
        public boolean gliding(){return gliding;}
        public void press(long now,boolean grounded,int button){
            if(button<0||button>1||pressedAt[button]>=0)return;
            pressedAt[button]=now;pressOnGround[button]=grounded;holdConsumed[button]=false;
        }
        public Lift release(long now,boolean grounded,boolean usable,int button){
            if(button<0||button>1||pressedAt[button]<0)return Lift.NONE;
            boolean shortPress=!holdConsumed[button]&&now-pressedAt[button]<HOLD_TICKS;
            boolean completedHold=!holdConsumed[button]&&now-pressedAt[button]>=HOLD_TICKS;
            pressedAt[button]=-1;holdConsumed[button]=false;
            // Input packets can be handled before this entity's tick at the threshold.
            // A release on exactly tick8 is still a completed hold, never a lost gesture.
            if(usable&&completedHold)return charged(now);
            if(!usable||!shortPress)return Lift.NONE;
            if(button==1||grounded&&pressOnGround[button]){
                if(grounded&&now-lastLift>4)airDolphins=0;
                if(airDolphins>=2)return Lift.NONE;
                airDolphins++;lastLift=now;return Lift.DOLPHIN;
            }
            gliding=!gliding&&glideEnergy>0;
            return Lift.NONE;
        }
        public Lift tick(long now,boolean grounded,boolean usable){
            if(!usable)cancel();
            if(grounded&&now-lastLift>4){
                gliding=false;glideEnergy=GLIDE_CAPACITY;
                airDolphins=0;
                if(charges<LEAP_CAPACITY&&++recovery>=LEAP_RECOVERY_TICKS){charges++;recovery=0;}
                if(charges==LEAP_CAPACITY)recovery=0;
            } else {
                recovery=0;
                if(gliding&&glideEnergy>0&&--glideEnergy==0)gliding=false;
            }
            for(int button=0;usable&&button<2;button++)if(pressedAt[button]>=0&&!holdConsumed[button]&&now-pressedAt[button]>=HOLD_TICKS){
                holdConsumed[button]=true;
                return charged(now);
            }
            return Lift.NONE;
        }
        private Lift charged(long now){
            if(charges<=0)return Lift.NONE;
            for(int other=0;other<2;other++)if(pressedAt[other]>=0&&now-pressedAt[other]>=HOLD_TICKS)holdConsumed[other]=true;
            charges--;lastLift=now;recovery=0;gliding=glideEnergy>0;return Lift.CHARGED;
        }
        public void cancelInput(){java.util.Arrays.fill(pressedAt,-1);java.util.Arrays.fill(holdConsumed,false);}
        public void cancel(){cancelInput();gliding=false;}
        public void restore(int storedCharges,int energy){
            charges=Math.clamp(storedCharges,0,LEAP_CAPACITY);glideEnergy=Math.clamp(energy,0,GLIDE_CAPACITY);cancel();recovery=0;
        }
    }
}
