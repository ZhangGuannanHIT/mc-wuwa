package cn.zgnhit.mcwuwa.client;

import cn.zgnhit.mcwuwa.core.ActionLightRules;
import java.util.*;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;

/** Silhouettes observed in archived official skill demonstrations.
 * Scale/timing remain MC adaptations; no film camera or damage callback is copied. */
final class SupplementalActionVfx {
    private static final int BLUE=0xff8cdcff,WHITE=0xffeffbff,PURPLE=0xffd99aff,RED=0xffff526f;
    private SupplementalActionVfx(){}
    static Sample sample(String action,double progress,double u){
        progress=ReviewedReleaseVfxClock.presentationPhase(action,progress);
        if(!ActionLightRules.supplemental(action))return null;
        if(HiyukiPilotVfx.supports(action))return HiyukiPilotVfx.sample(action,progress,u);
        // Pilot attacks use timed sword/aperture geometry instead of an unrelated ambient ring.
        if(CartethyiaPilotVfx.supports(action))return CartethyiaPilotVfx.sample(action,progress,u);
        var phrolova=PhrolovaPilotVfx.sample(action,progress,u);
        if(phrolova!=null)return phrolova;
        var jinhsi=JinhsiPilotVfx.sample(action,progress,u);
        if(jinhsi!=null)return jinhsi;
        var mornye=MornyePilotVfx.sample(action,progress,u);
        if(mornye!=null)return mornye;
        var aemeath=AemeathPilotVfx.sample(action,progress,u);
        if(aemeath!=null)return aemeath;
        var lynae=LynaePilotVfx.sample(action,progress,u);
        if(lynae!=null)return lynae;
        double p=Math.clamp(progress,0,1),angle=u*Math.TAU;
        if(action.equals("hsin/liberation")){
            double radius=.22+.10*Math.sin(p*Math.PI);
            return new Sample(.25+Math.cos(angle)*radius,.45+Math.sin(angle)*radius,.7+Math.sin(angle*2)*radius*.45,u<.7?0xffffd48c:RED,.20f,Kind.DUST);
        }
        if(action.equals("phrolova/conducting_stasis_field"))return new Sample(Math.cos(angle)*6,-1,Math.sin(angle)*6,RED,.45f,Kind.DUST);
        if(action.equals("jinhsi/liberation"))return new Sample(Math.sin(u*27)*.12,3.4-u*4.2,1.5,u<.55?0xffffdf9c:WHITE,.8f,Kind.DUST);
        if(action.equals("yinlin/liberation")&&p<.25)return new Sample((u-.5)*1.8,.55+Math.sin(u*Math.PI)*.25,.9,PURPLE,.2f,Kind.DUST);
        if(action.equals("yinlin/liberation"))return new Sample(Math.sin(u*39)*.13,3.1-u*4,1.4,PURPLE,.8f,Kind.DUST);
        if(action.startsWith("jinhsi/")){
            int color=u<.7?0xffffdf9c:WHITE;
            if(action.contains("epiphany")||action.contains("radiance")||action.equals("jinhsi/skill_overflowing"))
                return new Sample(Math.sin(u*29)*.12,Math.cos(u*29)*.12,1+u*4.5,color,.6f,Kind.DUST);
            if(action.equals("jinhsi/air"))return new Sample(Math.cos(angle)*.5,1.8-u*3,1+Math.sin(angle)*.5,color,.6f,Kind.DUST);
            return new Sample(Math.cos(-2+u*4)*1.6,Math.sin(-2+u*4)*(action.contains("incarnation")?1.1:.65),1.3,color,.55f,Kind.DUST);
        }
        if(action.startsWith("yinlin/")){
            if(action.contains("judgment")||action.contains("blast")||action.contains("roar"))
                return new Sample(Math.sin(u*37)*.12,2.8-u*3.4,1.5,PURPLE,.65f,Kind.DUST);
            return new Sample(Math.sin(u*25+p*4)*.16,Math.cos(u*25+p*4)*.16,.6+u*2.7,PURPLE,.5f,Kind.DUST);
        }
        if(action.startsWith("lynae/")&&(action.equals("lynae/cruise_air_heavy")||action.equals("lynae/visual_impact")||action.equals("lynae/rainbow_splash")||action.equals("lynae/liberation")))
            return new Sample(Math.cos(angle)*(.4+p*2),1.5-u*3,Math.sin(angle)*(.4+p*2),rainbow(u),.8f,Kind.STAR);
        if(action.startsWith("lynae/cruise_basic_"))return new Sample(Math.cos(angle)*1.3,-.75,Math.sin(angle)*1.3,rainbow(u),.55f,Kind.DUST);
        if(action.startsWith("lynae/"))return new Sample((u-.5)*1.2,-.7+Math.sin(u*Math.PI)*.3,-u*1.8,rainbow(u),.65f,Kind.DUST);
        if(action.equals("cartethyia/skill"))return new Sample(Math.cos(angle)*1.3,-.7+u*2,Math.sin(angle)*1.3,PURPLE,.65f,Kind.DUST);
        if(action.startsWith("cartethyia/fleurdelys_")||action.equals("cartethyia/liberation_finale"))
            return new Sample(Math.cos(angle)*(1.7+p*.4),-.3+Math.sin(angle)*.9,1.2+Math.sin(angle)*.4,u<.6?BLUE:WHITE,.65f,Kind.DUST);
        if(action.startsWith("cartethyia/"))return action.startsWith("cartethyia/air")?new Sample((u-.5)*.6,1.8-u*3,1.1,PURPLE,.6f,Kind.DUST):new Sample(Math.cos(angle)*1.3,Math.sin(angle)*.7,1.2,BLUE,.6f,Kind.DUST);
        if(action.equals("hiyuki/skill"))return new Sample(Math.sin(angle)*.3,1.9-u*2.6,1.2,0xffffc4cb,.65f,Kind.DUST);
        if(action.equals("hiyuki/air")||action.equals("hiyuki/future_air")||action.equals("phrolova/air")||action.equals("camellya/air"))
            return new Sample(Math.cos(angle)*.35,1.7-u*3.2,1+Math.sin(angle)*.35,action.startsWith("hiyuki/")?WHITE:RED,.65f,Kind.DUST);
        if(action.startsWith("hiyuki/basic_"))return new Sample(Math.cos(angle)*1.2,Math.sin(angle)*.65,1.1,u<.5?WHITE:0xffffc4cb,.55f,Kind.DUST);
        if(action.startsWith("hiyuki/future_")||action.equals("hiyuki/heavy"))return new Sample(Math.cos(angle)*1.5,-.55+Math.sin(u*Math.PI)*.9,1.2+u*.5,u<.6?WHITE:BLUE,.6f,Kind.DUST);
        if(action.equals("hiyuki/skill_jade"))return new Sample(Math.cos(angle)*(1+p),-.7+u*.7,Math.sin(angle)*(1+p),WHITE,.6f,Kind.DUST);
        if(action.equals("hiyuki/skill_falling"))return new Sample((u-.5)*2.4,-.4+u*2,1.25,u<.6?WHITE:0xffffc4cb,.6f,Kind.DUST);
        if(action.equals("yangyang_xuanling/feather_descent"))return new Sample((u-.5)*1.1,2-u*3,1.2,u<.7?BLUE:WHITE,.7f,Kind.DUST);
        if(action.startsWith("yangyang_xuanling/"))return new Sample(Math.cos(angle)*1.4,Math.sin(angle)*.9,1.2,u<.7?BLUE:0xffffcf81,.7f,Kind.DUST);
        if(action.equals("aero_rover/intro")){
            double a=-1.3+u*2.8;
            return p<.5?new Sample(Math.sin(a)*.35,-.7+u*2.5,1+Math.cos(a)*.6,u<.7?BLUE:WHITE,.55f,Kind.DUST)
                :new Sample(Math.cos(a)*1.8,-.65+Math.sin(a)*.3,1.1+Math.sin(a)*.8,u<.7?BLUE:WHITE,.55f,Kind.DUST);
        }
        if(action.startsWith("aero_rover/"))return new Sample(Math.cos(angle)*1.5,action.equals("aero_rover/air")?1.8-u*3:Math.sin(angle)*.8,1.2,u<.7?BLUE:WHITE,.6f,Kind.DUST);
        if(action.equals("hsin/flying_palace"))return new Sample((u-.5)*2.3,1.4-u*1.5,(1-p)*.35,0xff9fffe3,.5f,Kind.DUST);
        if(action.equals("hsin/illuminated_word_lock"))return new Sample(Math.cos(angle)*p,.2*Math.sin(angle),Math.sin(angle)*p,0xffffd48c,.5f,Kind.DUST);
        if(hsinDescending(action))return new Sample(Math.sin(u*25)*.12,3-u*3.6,1.8,0xffffd48c,.65f,Kind.DUST);
        if(hsinWorldlyHeavy(action))return new Sample(Math.cos(angle)*1.4,-.5+Math.sin(angle)*.4,1.5+Math.sin(angle)*.6,RED,.6f,Kind.DUST);
        if(palaces(action))return new Sample(Math.cos(angle)*(1+p*.3),-.65+Math.sin(u*Math.PI)*.3,1.6+Math.sin(angle)*(1+p*.3),0xffffd48c,.6f,Kind.DUST);
        if(action.startsWith("hsin/illuminated_basic_"))return new Sample(Math.cos(angle)*(1+p*.3),-.6+Math.sin(u*Math.PI)*1.5,1.5+Math.sin(angle)*.7,u<.7?0xffffd48c:WHITE,.6f,Kind.DUST);
        if(action.startsWith("hsin/basic_"))return new Sample((u-.5)*1.6,-.6+u*.9,1.3,RED,.55f,Kind.DUST);
        if(action.equals("hsin/skill"))return new Sample(Math.cos(angle)*1.5,-.3,Math.sin(angle)*1.5,RED,.7f,Kind.DUST);
        if(action.startsWith("hsin/"))return new Sample(Math.cos(angle)*(1+p*.3),action.contains("air")?1.7-u*3:-.3+Math.sin(angle)*.65,1.3,
            action.contains("illuminated")||action.contains("palaces")?0xffffd48c:RED,.65f,Kind.DUST);
        if(chisaSaw(action))return new Sample(Math.cos(angle)*1.5,Math.sin(angle)*1.2,1.2,u<.65?RED:WHITE,.7f,Kind.DUST);
        if(action.startsWith("chisa/")&&!action.equals("chisa/liberation"))return new Sample((u-.5)*2.8,(u-.5)*(u<.5?1.4:-1.4),1.2,u<.65?RED:WHITE,.6f,Kind.DUST);
        if(action.startsWith("aemeath/mech_"))return new Sample((u-.5)*2.8,-.5+u*1.5,1.3,u<.7?BLUE:WHITE,.7f,Kind.DUST);
        if(action.startsWith("aemeath/"))return new Sample(Math.cos(angle)*(1+p),Math.sin(angle)*.7,1.2,u<.5?BLUE:0xffffb6f2,.65f,Kind.DUST);
        if(action.equals("chisa/liberation"))return new Sample((u-.5)*4,(u-.5)*1.5,1.2,RED,.85f,Kind.DUST);
        if(action.equals("hsin/illuminated_entry"))return new Sample(Math.cos(angle)*(1+p*1.5),-.8+u*.6,Math.sin(angle)*(1+p*1.5),0xffffd48c,.65f,Kind.DUST);
        if(action.startsWith("mornye/"))return new Sample(Math.cos(angle)*(1+p*.7),Math.sin(angle)*(1+p*.7),1.2,u<.5?BLUE:PURPLE,.65f,Kind.DUST);
        if(action.startsWith("phrolova/"))return new Sample((u-.5)*3.8,Math.sin(u*Math.TAU+p*3)*.28,.8+p*2,RED,.8f,Kind.DUST);
        if(action.startsWith("camellya/")){
            if(action.contains("/basic_"))return new Sample(Math.cos(angle)*1.3,-.15+Math.sin(angle)*.5,1.1,RED,.6f,Kind.DUST);
            if(action.equals("camellya/liberation"))return new Sample(Math.cos(angle)*(1+p*2),-.85+Math.sin(u*Math.PI)*.2,Math.sin(angle)*(1+p*2),RED,.9f,Kind.DUST);
            if(action.contains("bloom_")&&!action.endsWith("dark_heart"))return new Sample(Math.cos(angle)*(1.1+p*.4),-.25,Math.sin(angle)*(1.1+p*.4),RED,.65f,Kind.DUST);
            double radius=(.45+1.5*Math.sin(u*Math.PI*6)*Math.sin(u*Math.PI*6))*(.6+p*.6);
            return new Sample(Math.cos(angle)*radius,Math.sin(angle)*radius,1.1,RED,.7f,Kind.DUST);
        }
        if(action.endsWith("02"))return new Sample(Math.sin(u*19)*.1,2.3-u*3,1.3,BLUE,.8f,Kind.DUST);
        return new Sample((u-.5)*3.5,(u-.5)*(action.endsWith("intro")?2.6:-1.6),1.1,BLUE,.65f,Kind.DUST);
    }
    static List<LightPath> ribbons(String action,double progress){
        progress=ReviewedReleaseVfxClock.presentationPhase(action,progress);
        // Persistent client stance is not a server attack/light identity.
        if(action.equals("phrolova/conducting_throne")||action.equals("phrolova/conduction_enter"))return PhrolovaPilotVfx.ribbons(action,progress);
        if(!ActionLightRules.supplemental(action))return List.of();
        if(HiyukiPilotVfx.supports(action))return HiyukiPilotVfx.ribbons(action,progress);
        var cartethyia=CartethyiaPilotVfx.ribbons(action,progress);
        if(cartethyia!=null)return cartethyia;
        var phrolova=PhrolovaPilotVfx.ribbons(action,progress);
        if(phrolova!=null)return phrolova;
        var jinhsi=JinhsiPilotVfx.ribbons(action,progress);
        if(jinhsi!=null)return jinhsi;
        var mornye=MornyePilotVfx.ribbons(action,progress);
        if(mornye!=null)return mornye;
        var aemeath=AemeathPilotVfx.ribbons(action,progress);
        if(aemeath!=null)return aemeath;
        var lynae=LynaePilotVfx.ribbons(action,progress);
        if(lynae!=null)return lynae;
        double p=Math.clamp(progress,0,1);var paths=new ArrayList<LightPath>();
        if(action.startsWith("chisa/")&&!action.equals("chisa/liberation")){
            if(chisaSaw(action)){
                boolean finale=action.endsWith("finale");
                for(int layer=0;layer<3;layer++){
                    double radius=(finale?1.8:1.25)+layer*.12;
                    curve(paths,layer==1?WHITE:RED,layer==1?.025f:.05f,t->{double a=t*Math.TAU+p*9;return new Vertex(Math.cos(a)*radius,Math.sin(a)*radius*.7,1.2);});
                }
                for(int tooth=0;tooth<6;tooth++){
                    double a=tooth*Math.TAU/6+p*9;
                    curve(paths,RED,.035f,t->new Vertex(Math.cos(a)*(.95+t*.55),Math.sin(a)*(.95+t*.55)*.7,1.2));
                }
                if(finale)for(int side:new int[]{-1,1})curve(paths,WHITE,.055f,t->new Vertex((t-.5)*3.8,side*(t-.5)*2.2,1.3));
            }else{
                for(int side:new int[]{-1,1})for(int layer=0;layer<2;layer++){
                    double offset=layer*.08;
                    curve(paths,layer==0?RED:WHITE,layer==0?.055f:.02f,t->new Vertex((t-.5)*3.2,side*(t-.5)*1.8+offset,1.2));
                }
            }
            return List.copyOf(paths);
        }
        if(action.startsWith("jinhsi/")&&!action.equals("jinhsi/liberation")){
            if(action.contains("epiphany")||action.contains("radiance")||action.equals("jinhsi/skill_overflowing")){
                for(int side:new int[]{-1,1})curve(paths,side<0?0xffffdf9c:WHITE,.035f,t->new Vertex(Math.sin(t*Math.PI*4+p*3)*.14*side,Math.cos(t*Math.PI*4+p*3)*.14,1+t*4.5));
            }else if(action.equals("jinhsi/air")){
                curve(paths,0xffffdf9c,.04f,t->new Vertex(0,1.8-t*3,1));
            }else{
                for(int layer=0;layer<2;layer++){
                    final double radius=1.5+layer*.12;
                    curve(paths,layer==0?0xffffdf9c:WHITE,layer==0?.045f:.02f,t->{double a=-2+t*4;return new Vertex(Math.cos(a)*radius,Math.sin(a)*(action.contains("incarnation")?1.1:.65),1.3);});
                }
            }
            return List.copyOf(paths);
        }
        if(action.startsWith("yinlin/")&&!action.equals("yinlin/liberation")){
            boolean bolt=action.contains("judgment")||action.contains("blast")||action.contains("roar");
            for(int side:new int[]{-1,1})curve(paths,side<0?PURPLE:WHITE,side<0?.035f:.015f,t->bolt
                ?new Vertex(Math.sin(t*37)*.12*side,2.8-t*3.4,1.5)
                :new Vertex(Math.sin(t*25+p*4)*.16*side,Math.cos(t*25+p*4)*.16*side,.6+t*2.7));
            return List.copyOf(paths);
        }
        if(action.equals("hsin/illuminated_word_lock")){
            double radius=.52*(1-p)+.04;
            for(int axis=0;axis<3;axis++)for(int a:new int[]{-1,1})for(int b:new int[]{-1,1}){
                int lineAxis=axis;
                curve(paths,lineAxis==1?0xffffd48c:0xff9fffe3,.025f,t->{
                    double v=(t-.5)*2*radius;
                    return switch(lineAxis){case 0->new Vertex(v,a*radius,b*radius);case 1->new Vertex(a*radius,v,b*radius);default->new Vertex(a*radius,b*radius,v);};
                });
            }
            return List.copyOf(paths);
        }
        if(action.equals("phrolova/conducting_stasis_field")){
            curve(paths,RED,.025f,t->new Vertex(Math.cos(t*Math.TAU)*6,-1,Math.sin(t*Math.TAU)*6));
            curve(paths,WHITE,.012f,t->new Vertex(Math.cos(t*Math.TAU)*5.8,-1,Math.sin(t*Math.TAU)*5.8));
            for(int spoke=0;spoke<6;spoke++){
                double angle=spoke*Math.TAU/6;
                curve(paths,RED,.018f,t->new Vertex(Math.cos(angle)*(5.5+t*.5),-1+t*.7,Math.sin(angle)*(5.5+t*.5)));
            }
            return List.copyOf(paths);
        }
        if(action.equals("cartethyia/gravity_field")||action.equals("cartethyia/gravity_followup_field")){
            curve(paths,BLUE,.025f,t->new Vertex(Math.cos(t*Math.TAU)*4,.07,Math.sin(t*Math.TAU)*4));
            curve(paths,WHITE,.025f,t->new Vertex(Math.cos(t*Math.TAU)*.85,1+Math.sin(t*Math.TAU)*.85,0));
            curve(paths,BLUE,.025f,t->new Vertex(0,1+Math.cos(t*Math.TAU)*.85,Math.sin(t*Math.TAU)*.85));
            curve(paths,0xffffe69a,.025f,t->new Vertex(Math.cos(t*Math.TAU)*.85,1,Math.sin(t*Math.TAU)*.85));
            if(action.equals("cartethyia/gravity_followup_field")&&p<.35){
                double drop=(1-p/.35)*3;
                CartethyiaPilotVfx.sword(paths,0,0,3.2+drop,3.1);
            }
        }else if(action.equals("jinhsi/liberation")){
            for(int i=0;i<3;i++){
                double side=(i-1)*.22;
                curve(paths,i==1?WHITE:0xffffdf9c,i==1?.06f:.025f,t->new Vertex(side+Math.sin(t*27)*.09,3.5-t*4.3,1.5));
            }
            curve(paths,BLUE,.03f,t->{double a=t*Math.TAU;return new Vertex(Math.cos(a)*1.5,-.8,1.5+Math.sin(a)*1.5);});
            curve(paths,WHITE,.025f,t->{double a=t*Math.TAU*1.4;return new Vertex(Math.cos(a)*(1-p*.3),-.5+t*2.8,1.5+Math.sin(a)*(1-p*.3));});
        }else if(action.equals("yinlin/liberation")){
            if(p<.25){
                for(int i=0;i<4;i++){
                    double side=(i-1.5)*.45;
                    curve(paths,PURPLE,.014f,t->new Vertex(side*(.4+t*.6),.6-t*.7+Math.sin(t*Math.PI)*.1,.7+t*1.3));
                }
                return List.copyOf(paths);
            }
            for(int i=0;i<3;i++){
                double x=(i-1)*.32;
                curve(paths,i==1?WHITE:PURPLE,i==1?.065f:.025f,t->new Vertex(x+Math.sin(t*35)*.10,3.5-t*4.3,1.4));
            }
            curve(paths,PURPLE,.035f,t->new Vertex(Math.cos(t*Math.TAU)*(1+p),-.9,1.4+Math.sin(t*Math.TAU)*(1+p)));
        }else if(action.equals("aero_rover/intro")){
            // Entry rises through the target; the second contact sweeps low.
            // Keep these two silhouettes separate instead of one ambient ring.
            for(int layer=0;layer<3;layer++){
                double offset=(layer-1)*.08;
                int color=layer==1?WHITE:BLUE;
                float width=layer==1?.025f:.045f;
                if(p<.5)curve(paths,color,width,t->{double a=-1.3+t*2.8;
                    return new Vertex(Math.sin(a)*.35+offset,-.7+t*2.5,1+Math.cos(a)*.6);});
                else curve(paths,color,width,t->{double a=-1.3+t*2.8;
                    return new Vertex(Math.cos(a)*1.8,-.65+Math.sin(a)*.3+offset,1.1+Math.sin(a)*.8);});
            }
        }else if(action.startsWith("aero_rover/")&&!action.contains("basic_")){
            boolean falling=action.endsWith("/air")||action.endsWith("wind_air_02"),rising=action.endsWith("wind_air_01");
            boolean second=action.endsWith("unbound_wind_02"),large=action.endsWith("liberation")||second;
            for(int layer=0;layer<3;layer++){
                double radius=(large?1.9:1.35)+layer*.12;
                curve(paths,layer==1?WHITE:BLUE,layer==1?.025f:.045f,t->{double a=-2+t*4;
                    return new Vertex(Math.cos(a)*radius,falling?1.8-t*3.1:rising?-.7+t*2.6:Math.sin(a)*.75,1.2);});
            }
            if(large||action.endsWith("heavy_strangle"))for(int layer=0;layer<2;layer++){
                double radius=1+p*.8+layer*.18;
                curve(paths,layer==0?BLUE:WHITE,.035f,t->new Vertex(Math.cos(t*Math.TAU)*radius,-.75,1.2+Math.sin(t*Math.TAU)*radius));
            }
        }else if(action.startsWith("aero_rover/basic_")){
            boolean rising=action.endsWith("04");
            curve(paths,WHITE,.035f,t->{double theta=Math.PI*(.05+t*.9);return new Vertex(Math.cos(theta)*1.3,rising?-.5+Math.sin(theta)*1.3:-.2+Math.sin(theta)*.25,1.2);});
            curve(paths,BLUE,.018f,t->new Vertex((t-.5)*2.2,(t-.5)*.9,1.25));
        }else if(action.startsWith("chisa/basic_")){
            for(int sign:new int[]{-1,1})curve(paths,sign<0?RED:0xffffc4cb,.035f,t->new Vertex((t-.5)*2.8,sign*(t-.5)*.9,1.2));
        }else if(action.equals("hsin/flying_palace")){
            // Target-local cyan portal/lintel and descending flying eaves,
            // observed in official hsin-5. Never attached to the player's body.
            int mint=0xff91f5da,gold=0xffffd48c;
            for(int sign:new int[]{-1,1}){
                curve(paths,mint,.025f,t->new Vertex(sign*.9,-.6+t*1.8,0));
                curve(paths,gold,.025f,t->new Vertex(sign*(.9+t*.3),1.2+t*.18,0));
            }
            curve(paths,mint,.035f,t->new Vertex((t-.5)*1.8,1.2,0));
            curve(paths,gold,.018f,t->new Vertex((t-.5)*1.5,1.36,.03));
            double drop=Math.clamp(p/.35,0,1)*1.8;
            for(int sign:new int[]{-1,1})curve(paths,mint,.045f,t->new Vertex(sign*(.15+t*.65),1.25-drop+t*.48,.08));
            curve(paths,WHITE,.03f,t->new Vertex((t-.5)*1.2,-.55,.08));
        }else if(hsinDescending(action)){
            double drop=2.4*(1-Math.clamp(p/.6,0,1));
            for(int sign:new int[]{-1,1})curve(paths,0xffffd48c,.035f,t->new Vertex(sign*.17,-.65+t*3+drop,1.8));
            curve(paths,WHITE,.045f,t->new Vertex(0,-.65+t*3+drop,1.8));
            curve(paths,0xffffd48c,.035f,t->new Vertex(Math.cos(t*Math.TAU)*1.5,-.7,1.8+Math.sin(t*Math.TAU)*1.5));
        }else if(hsinWorldlyHeavy(action)){
            for(int sign:new int[]{-1,1})curve(paths,sign<0?RED:WHITE,.035f,t->new Vertex(Math.cos(t*Math.TAU)*1.4,sign*Math.sin(t*Math.TAU)*.4,1.5+Math.sin(t*Math.TAU)*.6));
        }else if(action.equals("hsin/liberation")){
            // Small cupped golden sphere, followed by the red/gold turning wake.
            for(int ring=0;ring<3;ring++){
                double tilt=ring*Math.PI/3,r=.25+.08*Math.sin(p*Math.PI);
                curve(paths,ring==1?WHITE:0xffffd48c,.018f,t->new Vertex(.25+Math.cos(t*Math.TAU)*r,.45+Math.sin(t*Math.TAU)*r*Math.cos(tilt),.7+Math.sin(t*Math.TAU)*r*Math.sin(tilt)));
            }
            if(p>.45)for(int layer=0;layer<3;layer++){
                double r=1.1+layer*.3;
                curve(paths,layer==1?0xffffd48c:RED,.025f,t->new Vertex(Math.cos(t*Math.TAU+p*4)*r,-.3+t*.65,Math.sin(t*Math.TAU+p*4)*r));
            }
        }else if(palaces(action)){
            int gold=0xffffd48c;
            for(int ring=0;ring<2;ring++){
                double radius=1.1+ring*.18+p*.15;
                curve(paths,ring==0?gold:WHITE,.03f,t->new Vertex(Math.cos(t*Math.TAU)*radius,-.75,1.6+Math.sin(t*Math.TAU)*radius));
            }
            for(int pillar=0;pillar<4;pillar++){
                double a=pillar*Math.TAU/4+Math.PI/4,x=Math.cos(a)*.8,z=1.6+Math.sin(a)*.8;
                curve(paths,gold,.025f,t->new Vertex(x,-.7+t*1.6,z));
            }
            for(int sign:new int[]{-1,1})curve(paths,WHITE,.025f,t->new Vertex(sign*(.9-t*.9),.8+Math.sin(t*Math.PI/2)*.35,1.6));
        }else if(action.startsWith("hsin/illuminated_basic_")){
            int stage=action.endsWith("03")?3:action.endsWith("02")?2:1;
            double radius=.65+stage*.2+p*.25;
            curve(paths,0xffffd48c,.025f,t->new Vertex(Math.cos(t*Math.TAU)*radius,-.7,1.5+Math.sin(t*Math.TAU)*radius));
            for(int petal=0;petal<stage*2;petal++){
                double angle=petal*Math.TAU/(stage*2),x=Math.cos(angle),z=Math.sin(angle);
                curve(paths,petal%2==0?0xffffd48c:WHITE,.025f,t->{double spread=Math.sin(t*Math.PI)*radius;
                    return new Vertex(x*spread,-.65+t*(1.1+stage*.22),1.5+z*spread);});
            }
        }else if(action.startsWith("hsin/basic_")){
            // Complete official film28--37s: red-white sweeps surround the
            // open-hand casting gesture as well as the individual seals.
            double direction=action.endsWith("02")||action.endsWith("04")?-1:1;
            curve(paths,0xffffb4c1,.032f,t->{double a=-2.2+t*4.4;return new Vertex(Math.sin(a)*1.65,direction*Math.cos(a)*.35,1.2+Math.cos(a)*.6);});
            int symbols=action.endsWith("04")?3:2;
            for(int i=0;i<symbols;i++){
                double x=(i-(symbols-1)*.5)*.65;
                curve(paths,RED,.025f,t->{double a=t*Math.TAU;return new Vertex(x+Math.copySign(.23,Math.cos(a))*Math.pow(Math.abs(Math.cos(a)),.25),-.2+Math.copySign(.3,Math.sin(a))*Math.pow(Math.abs(Math.sin(a)),.25),1.3);});
                curve(paths,0xffffb4c1,.015f,t->new Vertex(x+(t-.5)*.3,-.45+t*.5,1.28));
            }
        }else if(action.startsWith("yangyang_xuanling/basic_")){
            curve(paths,BLUE,.04f,t->{double a=Math.PI*(.08+t*.84);return new Vertex(Math.cos(a)*1.4,-.2+Math.sin(a)*.3,1.2);});
            curve(paths,0xffffcf81,.02f,t->new Vertex((t-.5)*2.2,(t-.5)*.9,1.25));
        }else if(action.startsWith("yangyang_xuanling/")){
            if(action.startsWith("yangyang_xuanling/feather_basic_")){
                int stage=action.endsWith("04")?4:action.endsWith("03")?3:action.endsWith("02")?2:1;
                // Lower panel of the complete normal-attack card: distinguish
                // low lateral sweeps from raised crossings and the long finish.
                for(int layer=0;layer<3;layer++){
                    double offset=layer*.09;
                    curve(paths,layer==2?0xffffcf81:BLUE,layer==2?.02f:.04f,t->{
                        double a=-2.1+t*4.2;
                        double x=Math.sin(a)*(stage==4?2:1.5);
                        double y=stage==1?-.45+Math.cos(a)*.2:
                            stage==2?(t-.5)*1.8:stage==3?Math.sin(t*Math.PI)*1.2-.5:-.25+Math.sin(t*Math.PI)*.3;
                        return new Vertex(x,y+offset,.8+Math.cos(a)*.5+(stage==4?t*1.4:0));
                    });
                }
            }else if(action.startsWith("yangyang_xuanling/listening_basic_")||action.startsWith("yangyang_xuanling/listening_counter_")){
                // Complete Hark the Wind card: crossed sweeps, an ascending
                // turn, then the broad forward release. Keep the open centre
                // and existing real-contact envelope; these are not hit boxes.
                int stage=action.endsWith("03")?3:action.endsWith("02")?2:1;
                for(int layer=0;layer<2;layer++)for(int sign:new int[]{-1,1}){
                    double offset=layer*.09;
                    int colour=layer==0?BLUE:0xffffcf81;
                    if(stage==1)curve(paths,colour,layer==0?.045f:.02f,t->{
                        double x=(t-.5)*3;
                        return new Vertex(x,sign*x*.48+offset,.8+Math.sin(t*Math.PI)*.7);
                    });
                    else if(stage==2)curve(paths,colour,layer==0?.045f:.02f,t->{
                        double a=t*Math.PI*1.65+sign*.35;
                        return new Vertex(Math.cos(a)*(1.2+offset),-.65+t*2.3,1.1+Math.sin(a)*.65);
                    });
                    else curve(paths,colour,layer==0?.055f:.025f,t->{
                        double spread=Math.sin(t*Math.PI/2)*(2.2+offset);
                        return new Vertex(sign*spread,-.15+offset+Math.sin(t*Math.PI)*.35,.8+t*1.9);
                    });
                }
            }else if(action.endsWith("feather_descent")){
                for(int blade=0;blade<3;blade++){
                    double side=(blade-1)*.5;
                    curve(paths,blade==1?WHITE:BLUE,.045f,t->new Vertex(side,2-t*3.1,1.2));
                }
                curve(paths,0xffffcf81,.03f,t->new Vertex(Math.cos(t*Math.TAU)*1.6,-.8,1.2+Math.sin(t*Math.TAU)*1.6));
            }else if(action.endsWith("feather_heavy")||action.endsWith("liberation")){
                for(int feather=0;feather<3;feather++){
                    double side=(feather-1)*.8;
                    curve(paths,WHITE,.025f,t->new Vertex(side,-.6+t*2,1.3));
                    for(int sign:new int[]{-1,1})curve(paths,feather==1?0xffffcf81:BLUE,.04f,t->new Vertex(side+sign*Math.sin(t*Math.PI)*.32,-.6+t*2,1.3));
                }
                curve(paths,BLUE,.035f,t->new Vertex(Math.cos(t*Math.TAU)*1.6,-.75,Math.sin(t*Math.TAU)*1.6));
            }else if(action.endsWith("flow_azure")){
                curve(paths,0xffffcf81,.055f,t->new Vertex((t-.5)*2.6,(t-.5)*1.4,.7+t*.8));
                curve(paths,BLUE,.035f,t->new Vertex((t-.5)*2.6+.12,(t-.5)*1.4-.12,.8+t*.8));
            }else for(int arc=0;arc<3;arc++){
                double radius=1.05+arc*.23,depth=1.2+arc*.07;
                curve(paths,arc==2?0xffffcf81:BLUE,.04f,t->{double theta=Math.PI*(.08+t*.88);return new Vertex(Math.cos(theta)*radius,-.65+Math.sin(theta)*radius,depth);});
            }
        }else if(action.equals("hsin/skill")){
            for(int arc=0;arc<3;arc++){
                double radius=1.2+arc*.22;
                curve(paths,arc==1?WHITE:RED,.04f,t->{double theta=Math.PI*(.10+t*.8);return new Vertex(Math.cos(theta)*radius,-.35+Math.sin(theta)*.18,Math.sin(theta)*radius);});
            }
        }else if(action.equals("hsin/illuminated_uppercut")){
            // Film57--59s: ascending jade column and the caster lifting with it.
            for(int edge=0;edge<4;edge++){
                double angle=edge*Math.TAU/4,x=Math.cos(angle)*.45,z=1.2+Math.sin(angle)*.45;
                curve(paths,edge%2==0?0xffffd48c:0xff91efdc,.025f,t->new Vertex(x,-.8+t*3.0,z));
            }
            curve(paths,WHITE,.035f,t->new Vertex(Math.sin(t*Math.TAU)*.5,-.7+t*2.8,1.2+Math.cos(t*Math.TAU)*.5));
        }else if(action.startsWith("hsin/")){
            int color=action.contains("illuminated")||action.contains("palaces")?0xffffd48c:RED;
            if(action.contains("air")){
                for(int blade=0;blade<3;blade++){
                    double side=(blade-1)*.4;
                    curve(paths,blade==1?WHITE:color,.035f,t->new Vertex(side,1.7-t*3,1.3));
                }
            }else{
                for(int arc=0;arc<3;arc++){
                    double radius=1.1+arc*.15;
                    curve(paths,arc==1?WHITE:color,.04f,t->{double a=-2+t*4;return new Vertex(Math.cos(a)*radius,-.3+Math.sin(a)*.65,1.3);});
                }
                if(action.contains("intro")||action.contains("heavy"))for(int edge=0;edge<4;edge++){
                    double a=edge*Math.PI/2,b=(edge+1)*Math.PI/2;
                    curve(paths,color,.025f,t->new Vertex(Math.cos(a)*(1-t)+Math.cos(b)*t,-.2+Math.sin(a)*(1-t)+Math.sin(b)*t,1.4));
                }
            }
        }else if(action.equals("lynae/cruise_air_heavy")||action.equals("lynae/visual_impact")||action.equals("lynae/rainbow_splash")||action.equals("lynae/liberation")){
            for(int band=0;band<5;band++){
                double radius=.5+p*2+band*.10;
                curve(paths,rainbow(band/4d),.045f,t->new Vertex(Math.cos(t*Math.TAU)*radius,-.75,Math.sin(t*Math.TAU)*radius));
            }
            for(int shard=0;shard<6;shard++){
                double angle=shard*Math.TAU/6;
                curve(paths,rainbow(shard/6d),.04f,t->new Vertex(Math.cos(angle)*t*1.6,-.7+t*2,Math.sin(angle)*t*1.6));
            }
        }else if(action.startsWith("lynae/cruise_basic_")){
            for(int band=0;band<3;band++){
                double radius=1.05+band*.13;
                curve(paths,band==0?0xff86fff0:band==1?BLUE:0xffff95d9,.025f,t->{double a=Math.PI*(.1+t*.8);return new Vertex(Math.cos(a)*radius,-.75+Math.sin(a)*.15,Math.sin(a)*radius);});
            }
        }else if(action.startsWith("lynae/")){
            for(int band=0;band<5;band++){
                double offset=(band-2)*.10;
                curve(paths,rainbow(band/4d),.035f,t->new Vertex(Math.sin(t*Math.PI)*.45+offset,-.75+Math.sin(t*Math.PI)*.16,-t*2));
            }
        }else if(action.startsWith("cartethyia/fleurdelys_")&&!action.equals("cartethyia/fleurdelys_intro")||action.equals("cartethyia/liberation_finale")){
            boolean rising=action.endsWith("uppercut"),falling=action.endsWith("air_03"),finale=action.endsWith("liberation_finale");
            boolean enhanced=action.contains("heavy_enhanced")||action.endsWith("basic_05")||action.endsWith("skill_followup");
            int bands=enhanced||finale?4:3;
            for(int band=0;band<bands;band++){
                double radius=1.65+band*.12;
                curve(paths,band==1?WHITE:band==2?PURPLE:BLUE,band==1?.025f:.05f,t->{double a=-2+t*4;
                    return new Vertex(Math.cos(a)*radius,rising?-.6+t*2.5:falling?1.6-t*2.8:-.3+Math.sin(a)*.9,1.2+Math.sin(a)*.35);});
            }
            if(enhanced||finale){
                for(int gust=0;gust<3;gust++){
                    double offset=gust*.15;
                    curve(paths,BLUE,.035f,t->{double a=t*Math.TAU+p*4;return new Vertex(Math.cos(a)*(1+p*.6+offset),-.75,Math.sin(a)*(1+p*.6+offset));});
                }
            }
        }else if(action.equals("cartethyia/intro")||action.equals("cartethyia/fleurdelys_intro")){
            boolean large=action.contains("fleurdelys");
            curve(paths,BLUE,.045f,t->new Vertex((t-.5)*(large?3.8:2),-.1,1.2));
            curve(paths,WHITE,.025f,t->new Vertex(0,(t-.5)*1.4,1.2));
            curve(paths,PURPLE,.02f,t->new Vertex((t-.5)*1.4,(t-.5)*1.1,1.25));
        }else if(action.startsWith("cartethyia/")&&!action.equals("cartethyia/skill")){
            if(action.startsWith("cartethyia/air")){
                int blades=action.endsWith("three_swords")?3:action.endsWith("two_swords")?2:1;
                for(int i=0;i<blades;i++){
                    double side=(i-(blades-1)*.5)*.45;
                    curve(paths,PURPLE,.04f,t->new Vertex(side,2-t*2.8,1.1));
                }
                curve(paths,BLUE,.03f,t->new Vertex(Math.cos(t*Math.TAU)*1.25,-.8,1.1+Math.sin(t*Math.TAU)*1.25));
            }else{
                boolean cross=action.endsWith("02")||action.endsWith("04"),heavy=action.endsWith("heavy");
                for(int sign:new int[]{-1,1})curve(paths,sign<0?BLUE:PURPLE,heavy?.05f:.03f,t->{double angle=Math.PI*(.10+t*.8),radius=heavy?1.6:1.2;
                    return new Vertex(Math.cos(angle)*radius,(cross?sign:1)*Math.sin(angle)*.7-.35,1.0+sign*.08);});
            }
        }else if(action.equals("cartethyia/skill")){
            curve(paths,BLUE,.03f,t->new Vertex(Math.cos(t*Math.TAU)*1.4,-.75,Math.sin(t*Math.TAU)*1.4));
            for(int blade=0;blade<3;blade++){
                double angle=blade*Math.TAU/3;
                curve(paths,PURPLE,.045f,t->new Vertex(Math.cos(angle)*1.2,-.7+t*2.2,Math.sin(angle)*1.2));
                curve(paths,WHITE,.025f,t->new Vertex(Math.cos(angle)*1.2+(t-.5)*.4,-.2,Math.sin(angle)*1.2));
            }
        }else if(action.equals("hiyuki/air")||action.equals("hiyuki/future_air")||action.equals("phrolova/air")||action.equals("camellya/air")){
            int color=action.startsWith("hiyuki/")?WHITE:RED;
            for(int layer=0;layer<3;layer++){
                double side=(layer-1)*.18;
                curve(paths,layer==1?color:action.startsWith("hiyuki/")?BLUE:0xffffb4c1,layer==1?.05f:.025f,t->new Vertex(side,1.7-t*3.2,1));
            }
            curve(paths,color,.035f,t->new Vertex(Math.cos(t*Math.TAU)*(1+p*.4),-.8,1+Math.sin(t*Math.TAU)*(1+p*.4)));
        }else if(action.equals("hiyuki/liberation")){
            for(int ring=0;ring<3;ring++){
                double radius=.8+p*1.8+ring*.14;
                curve(paths,ring==1?BLUE:WHITE,.04f,t->new Vertex(Math.cos(t*Math.TAU)*radius,-.75,Math.sin(t*Math.TAU)*radius));
            }
            for(int spike=0;spike<5;spike++){
                double side=(spike-2)*.5;
                curve(paths,spike%2==0?WHITE:0xffffc4cb,.04f,t->new Vertex(side,-.7+t*2,1.3+t*.4));
            }
        }else if(action.equals("hiyuki/skill_falling")){
            for(int sign:new int[]{-1,1})curve(paths,sign<0?WHITE:0xffffc4cb,.04f,t->new Vertex(sign*(t-.5)*2.3,-.5+t*1.9,1.3));
            curve(paths,BLUE,.025f,t->new Vertex(Math.cos(t*Math.TAU)*(1.6-p*.5),-.5,1.2+Math.sin(t*Math.TAU)*(1.6-p*.5)));
            for(int petal=0;petal<3;petal++){
                double angle=petal*Math.TAU/3;
                curve(paths,WHITE,.018f,t->{double spread=Math.sin(t*Math.PI)*.6;return new Vertex(Math.cos(angle)*spread,-.3+t*1.4,1.3+Math.sin(angle)*spread);});
            }
        }else if(action.equals("hiyuki/skill_jade")){
            for(int ring=0;ring<2;ring++){
                double radius=(1+p*.6)*(1-ring*.15);
                curve(paths,ring==0?WHITE:BLUE,.03f,t->new Vertex(Math.cos(t*Math.TAU)*radius,-.75,Math.sin(t*Math.TAU)*radius));
            }
            for(int i=-1;i<=1;i++){
                double side=i*.7;
                curve(paths,WHITE,.02f,t->new Vertex(side,-.7+t*.65,1+t*.35));
            }
        }else if(action.startsWith("hiyuki/future_")||action.equals("hiyuki/heavy")){
            boolean frost=action.endsWith("heavy_frost"),finish=action.endsWith("05");
            curve(paths,WHITE,.045f,t->{double a=Math.PI*(.08+t*.84);return new Vertex(Math.cos(a)*1.6,-.3+Math.sin(a)*.3,1.25);});
            curve(paths,0xffffc4cb,.02f,t->new Vertex((t-.5)*2.8,-.3+(t-.5)*.7,1.3));
            if(frost||finish||action.endsWith("02"))for(int spike=0;spike<(frost?5:3);spike++){
                double x=(spike-(frost?2:1))*.45;
                curve(paths,BLUE,.03f,t->new Vertex(x,-.8+t*(frost?1.6:1.1),1.25+t*.45));
            }
        }else if(action.startsWith("hiyuki/basic_")){
            boolean rising=action.endsWith("03");
            curve(paths,WHITE,.045f,t->{double a=Math.PI*(.08+t*.84);return new Vertex(Math.cos(a)*1.4,rising?-.5+Math.sin(a)*1.4:-.15+Math.sin(a)*.3,1.1);});
            curve(paths,0xffffc4cb,.025f,t->new Vertex((t-.5)*2.2,(t-.5)*1.5,1.2));
        }else if(action.equals("hiyuki/skill")){
            curve(paths,WHITE,.06f,t->new Vertex(.08,2.1-t*2.9,1.25));
            curve(paths,0xffff9eb2,.035f,t->new Vertex(Math.cos(t*Math.TAU)*(1+p),-.8,1.25+Math.sin(t*Math.TAU)*(1+p)));
        }else if(action.equals("aemeath/intro")){
            curve(paths,0xffffb6f2,.04f,t->{double a=t*Math.TAU,r=(1+p*.3)/(Math.abs(Math.cos(a))+Math.abs(Math.sin(a)));return new Vertex(Math.cos(a)*r,-.7,Math.sin(a)*r);});
            for(int sign:new int[]{-1,1})curve(paths,WHITE,.02f,t->new Vertex((t-.5)*2.6,-.65,sign*(t-.5)*1.6));
        }else if(action.startsWith("aemeath/mech_")){
            boolean heavy=action.contains("heavy");
            curve(paths,WHITE,heavy?.07f:.045f,t->new Vertex((t-.5)*(heavy?3.8:2.8),-.6+t*1.8,1.3));
            curve(paths,BLUE,.03f,t->{double a=Math.PI*(.08+t*.84);return new Vertex(Math.cos(a)*(heavy?1.9:1.4),-.25+Math.sin(a)*.4,1.35);});
            if(heavy)curve(paths,0xffffb6f2,.025f,t->new Vertex((t-.5)*3.2,1-t*2,1.4));
        }else if(action.startsWith("aemeath/basic_")){
            boolean finalHit=action.endsWith("04");
            curve(paths,0xffffb6f2,.04f,t->{double a=Math.PI*(.05+t*.9);return new Vertex(Math.cos(a)*1.4,-.3+Math.sin(a)*.6,1.2);});
            curve(paths,WHITE,.025f,t->new Vertex((t-.5)*2,finalHit?(t-.5)*2:(.5-t),1.3));
        }else if(action.startsWith("aemeath/")){
            boolean mech=action.endsWith("skill_dawn")||action.endsWith("ensemble_stage")||action.endsWith("intro_starlight");
            for(int sign:new int[]{-1,1})for(int wing=0;wing<3;wing++){
                double rise=wing*.28;
                curve(paths,wing==1?WHITE:mech?BLUE:0xffffb6f2,.035f,t->new Vertex(sign*(.15+t*1.9),.1+rise+Math.sin(t*Math.PI)*.55-t*.3,1.1-t*.5));
            }
            for(int sign:new int[]{-1,1})curve(paths,WHITE,.04f,t->new Vertex((t-.5)*2.5,sign*(t-.5)*1.5,1.5));
        }else if(action.equals("chisa/liberation")){
            for(int sign:new int[]{-1,1})curve(paths,sign<0?RED:0xffffc4cb,.065f,t->new Vertex((t-.5)*4,sign*(t-.5)*1.8,1.3));
            curve(paths,RED,.035f,t->new Vertex(Math.cos(t*Math.TAU)*1.8,-.8,Math.sin(t*Math.TAU)*1.8));
        }else if(action.equals("hsin/illuminated_entry")){
            for(int i=0;i<3;i++){
                double radius=(.7+p*1.5)*(1-i*.18),height=-.8+i*.35;
                curve(paths,i==0?0xffffd48c:WHITE,.03f,t->new Vertex(Math.cos(t*Math.TAU)*radius,height+Math.sin(t*Math.TAU*3)*.10,Math.sin(t*Math.TAU)*radius));
            }
        }else if(action.equals("mornye/block_guard")){
            for(int ring=0;ring<2;ring++){
                double radius=.55+ring*.18;
                curve(paths,ring==0?WHITE:BLUE,.025f,t->new Vertex(Math.cos(t*Math.TAU)*radius,Math.sin(t*Math.TAU)*radius,1.15));
            }
            for(int sign:new int[]{-1,1})curve(paths,BLUE,.018f,t->new Vertex((t-.5)*.8,sign*(t-.5)*.8,1.15));
        }else if(action.startsWith("mornye/basic_")){
            boolean finalHit=action.endsWith("04");
            curve(paths,BLUE,.025f,t->new Vertex(Math.cos(t*Math.TAU)*.65,Math.sin(t*Math.TAU)*.65,1.2));
            for(int sign:new int[]{-1,1})curve(paths,sign<0?WHITE:PURPLE,finalHit?.05f:.025f,t->new Vertex((t-.5)*(finalHit?3:1.8),sign*(t-.5)*1.2,1.2));
        }else if(action.startsWith("mornye/")){
            boolean focus=action.endsWith("heavy_inversion")||action.endsWith("liberation")||action.endsWith("observation_basic_03");
            for(int panel=0;panel<3;panel++){
                double x=(panel-1)*1.25,z=panel==1?1.5:.9;
                curve(paths,BLUE,.018f,t->{double perimeter=t*4,side=Math.floor(perimeter),v=perimeter-side;
                    if(t==1)return new Vertex(x-.5,-.1,z);
                    return switch((int)side){case 0->new Vertex(x-.5+v,-.1,z);case 1->new Vertex(x+.5,-.1+v*.7,z);
                        case 2->new Vertex(x+.5-v,.6,z);default->new Vertex(x-.5,.6-v*.7,z);};});
                curve(paths,WHITE,.015f,t->{double theta=t*Math.TAU;return new Vertex(x+Math.cos(theta)*.2,.25+Math.sin(theta)*.2,z-.015);});
            }
            curve(paths,PURPLE,.025f,t->new Vertex(Math.cos(t*Math.TAU)*1.4,-.8,Math.sin(t*Math.TAU)*1.4));
            if(focus)for(int sign:new int[]{-1,1})curve(paths,sign<0?BLUE:WHITE,.04f,t->new Vertex((t-.5)*3.6,sign*(t-.5)*1.8,1.6));
            if(focus)for(int sign:new int[]{-1,1})curve(paths,sign<0?PURPLE:WHITE,.035f,t->new Vertex(sign*.6,2.3-t*3.1,1.6));
        }else if(action.startsWith("phrolova/hecate_")){
            boolean finale=action.endsWith("finale")||action.endsWith("fortissimo");
            boolean dash=action.contains("basic");
            for(int layer=0;layer<3;layer++){
                double offset=(layer-1)*.16;
                curve(paths,layer==1?0xffffe5ed:RED,layer==1?.025f:.065f,t->dash
                    ?new Vertex((t-.5)*4,.4+offset,.8+t*2.7)
                    :new Vertex(Math.cos(-2+t*4)*(finale?2.7:2.1),.4+Math.sin(-2+t*4)*1.2+offset,1.2));
            }
            if(finale)for(int layer=0;layer<3;layer++){
                double radius=1.8+layer*.2;
                curve(paths,layer==1?PURPLE:RED,.045f,t->new Vertex(Math.cos(t*Math.TAU)*radius,-.8,1.2+Math.sin(t*Math.TAU)*radius));
            }
        }else if(action.startsWith("phrolova/basic_")){
            boolean wide=action.endsWith("03");
            curve(paths,RED,wide?.05f:.035f,t->{double theta=Math.PI*(.08+t*.84),radius=wide?1.9:1.3;return new Vertex(Math.cos(theta)*radius,-.3+Math.sin(theta)*.16,Math.sin(theta)*radius);});
            if(wide)curve(paths,0xffffb4c1,.025f,t->new Vertex(Math.cos(t*Math.TAU)*1.25,-.7,1.2+Math.sin(t*Math.TAU)*1.25));
        }else if(action.startsWith("phrolova/")){
            if(action.endsWith("liberation_curtain")||action.endsWith("intro_eternal")){
                curve(paths,RED,.05f,t->new Vertex(Math.cos(t*Math.TAU)*(1.2+p),-.8,Math.sin(t*Math.TAU)*(1.2+p)));
                for(int sign:new int[]{-1,1})curve(paths,sign<0?RED:PURPLE,.055f,t->new Vertex(sign*Math.sin(t*Math.PI)*1.6,-.7+t*2.8,1.2));
            }else{
                for(int sign:new int[]{-1,1})curve(paths,sign<0?RED:0xffffb4c1,.045f,t->new Vertex((t-.5)*3.8,sign*Math.sin(t*Math.TAU+p*2)*.22,.9+p*1.2));
                if(action.endsWith("composition_finale")||action.endsWith("undying_reverie")||action.endsWith("heavy"))curve(paths,RED,.035f,t->new Vertex(Math.cos(t*Math.TAU)*(1+p),-.75,1.3+Math.sin(t*Math.TAU)*(1+p)));
            }
        }else if(action.startsWith("camellya/basic_")){
            boolean spin=action.endsWith("04"),fall=action.endsWith("05");
            curve(paths,RED,.04f,t->{double a=t*Math.PI*(spin?2:1)+.1;return new Vertex(Math.cos(a)*1.4,fall?1.3-t*2:-.2+Math.sin(a)*.15,spin?Math.sin(a)*1.4:1.2);});
            curve(paths,0xffffc0bc,.02f,t->new Vertex((t-.5)*2,fall?.9-t*1.8:(t-.5)*.8,1.25));
        }else if(action.equals("camellya/liberation")){
            return CamellyaLiberationBloomVfx.ribbons(p);
        }else if(action.startsWith("camellya/bloom_")&&!action.endsWith("dark_heart")){
            for(int i=0;i<2;i++){
                double offset=i*.18;
                curve(paths,i==0?RED:0xffffc0bc,.045f,t->{double a=t*Math.TAU+p*3;return new Vertex(Math.cos(a)*(1.2+offset),-.2+Math.sin(a*2)*.12,Math.sin(a)*(1.2+offset));});
            }
        }else if(action.startsWith("camellya/")){
            double radius=action.endsWith("intro")?1.2:1.8;
            for(int i=0;i<6;i++){
                double theta=i*Math.TAU/6;
                curve(paths,i%2==0?RED:0xffffc0bc,.045f,t->{
                    double a=theta+Math.sin(t*Math.TAU)*.22,r=Math.sin(t*Math.PI)*radius;
                    return new Vertex(Math.cos(a)*r,Math.sin(a)*r,1.2+Math.sin(t*Math.TAU)*.18);
                });
            }
            curve(paths,RED,.025f,t->new Vertex(Math.cos(t*Math.TAU)*radius,Math.sin(t*Math.TAU)*radius,1.2));
        }else{
            if(action.endsWith("02")){
                curve(paths,WHITE,.07f,t->new Vertex(.05,2.5-t*3.4,1.3));
                curve(paths,BLUE,.035f,t->new Vertex(Math.cos(t*Math.TAU)*(1+p),-.85,1.3+Math.sin(t*Math.TAU)*(1+p)));
            }else for(int sign:new int[]{-1,1})curve(paths,sign<0?BLUE:WHITE,.055f,t->new Vertex((t-.5)*3.6,sign*(t-.5)*1.8,1.15));
        }
        return List.copyOf(paths);
    }
    private static boolean hsinDescending(String action){return action.equals("hsin/illuminated_descend")||action.equals("hsin/illuminated_suppress");}
    private static boolean chisaSaw(String action){return action.startsWith("chisa/saw_")||action.startsWith("chisa/skill_saw_");}
    private static boolean hsinWorldlyHeavy(String action){return action.equals("hsin/worldly_step")||action.equals("hsin/worldly_suppress");}
    private static boolean palaces(String action){return action.equals("hsin/illuminated_skill")|| action.startsWith("hsin/thousand_palaces_")||action.equals("hsin/palaces_formation")||action.equals("hsin/liberation_palaces");}
    private static void curve(List<LightPath> out,int color,float width,DoubleFunction<Vertex> shape){
        var points=new ArrayList<Vertex>();for(int i=0;i<=24;i++)points.add(shape.apply(i/24d));
        out.add(new LightPath(points,color,width));
    }
    private static int rainbow(double u){
        int[] colors={0xff86fff0,0xff83cfff,0xffd6a0ff,0xffff95d9,0xffffe5a0};
        return colors[Math.clamp((int)(u*colors.length),0,colors.length-1)];
    }
}
