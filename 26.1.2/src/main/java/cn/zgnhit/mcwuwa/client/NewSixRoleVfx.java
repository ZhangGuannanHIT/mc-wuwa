package cn.zgnhit.mcwuwa.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;

/** Silhouettes from the frozen publisher demonstrations. Distances, sample
 * counts and the accepted-action clock are MC presentation adaptations only.
 * Neither strokes nor particles own targeting, damage, resources or movement. */
final class NewSixRoleVfx {
    private static final Set<String> ROLES=Set.of("denia","iuno","changli","qingxiao","lupa","carlotta");
    private static final int WHITE=0xfff3fcff,BLUE=0xff87d8ff,TEAL=0xff77f2ea,
        PURPLE=0xffbe93ff,ROSE=0xffff779f,RED=0xffff553e,GOLD=0xffffca72;
    private NewSixRoleVfx(){}
    static boolean supports(String role){return ROLES.contains(role);}
    private static int color(String role){return switch(role){case "denia"->PURPLE;case "changli","lupa"->RED;case "carlotta"->ROSE;default->BLUE;};}
    static Sample sample(String role,String action,double progress,double u,long elapsed){
        if(action.startsWith("echo"))return null;
        int color=color(role),lane=(int)(u*24);double p=Math.clamp(progress,0,1),angle=u*Math.TAU,arc=-2.1+u*4.2;
        if(action.equals("charge"))return NewRoleVfxProfiles.charge(color,u,elapsed);
        double side,up,forward;
        switch(role){
            case "denia"->{
                boolean wings=action.contains("disillusion")||action.contains("exile");
                if(wings){double sign=lane%2==0?1:-1;side=sign*(.2+Math.sin(u*Math.PI)*2.2);up=.25+Math.sin(u*Math.TAU)*.8;forward=.8+p*1.4;}
                else{double r=.3+u*.4;side=Math.cos(angle)*r;up=Math.sin(angle)*r;forward=.7+u*2.8;}
                color=lane%3==0?TEAL:PURPLE;
            }
            case "iuno"->{
                boolean bow=action.contains("bow")||action.startsWith("skill_beyond");
                if(bow){side=Math.sin(angle)*.12;up=Math.cos(angle)*.12;forward=.7+u*4;}
                else{side=Math.sin(arc)*1.65;up=Math.cos(arc)*.8;forward=.9+Math.cos(arc)*.5;}
                color=lane%4==0?GOLD:BLUE;
            }
            case "changli"->{side=Math.sin(arc)*(action.contains("sacrifice")?2.8:1.6);up=Math.cos(arc)*.65;forward=1.1+Math.cos(arc)*.8;color=lane%4==0?GOLD:RED;}
            case "qingxiao"->{
                if(action.contains("liberation")||action.startsWith("vast_sword")){side=(lane%7-3)*.55;up=3.2-u*4;forward=1.5+p;}
                else{side=Math.sin(arc)*1.5;up=Math.cos(arc)*.5;forward=1+Math.cos(arc)*.8;}
                color=lane%4==0?WHITE:TEAL;
            }
            case "lupa"->{side=(u-.5)*(action.contains("wolf_dance")?5:3.2);up=(lane%2==0?1:-1)*(u-.5)*1.4;forward=1.3+Math.sin(u*Math.PI)*.6;color=lane%4==0?GOLD:RED;}
            default->{side=Math.sin(angle)*.15;up=Math.cos(angle)*.15;forward=.7+u*(action.contains("finale")?4.5:3.2);color=lane%3==0?BLUE:ROSE;}
        }
        return new Sample(side,up,forward,color,action.contains("liberation")?.85f:.6f,lane%9==0?Kind.STAR:Kind.DUST);
    }
    static List<LightPath> ribbons(String role,String action,double progress){
        if(action.equals("charge")||action.startsWith("echo"))return List.of();
        double p=Math.clamp(progress,0,1);var out=new ArrayList<LightPath>();
        switch(role){
            case "denia"->{
                if(action.equals("erosion_field")){
                    circle(out,PURPLE,.035f,3,-1.0,0,false);circle(out,TEAL,.018f,2.8,-.95,0,false);
                    for(int i=0;i<4;i++){double angle=i*Math.TAU/4;diamond(out,PURPLE,Math.cos(angle)*2.7,-.65,Math.sin(angle)*2.7,.25);}
                }else if(action.contains("disillusion")||action.contains("exile")){
                    for(int sign:new int[]{-1,1}){
                        stroke(out,PURPLE,.045f,t->new Vertex(sign*(.18+Math.sin(t*Math.PI)*2.3),Math.sin(t*Math.TAU)*.75,.7+t*1.6));
                        stroke(out,TEAL,.022f,t->new Vertex(sign*(.3+Math.sin(t*Math.PI)*1.65),Math.sin(t*Math.TAU)*.5+.15,1+t*1.3));
                    }
                    if(action.startsWith("skill_exile"))for(int i=0;i<3;i++)diamond(out,i%2==0?TEAL:PURPLE,(i-1)*.9,.2,1.2+i*.4,.45+p*.25);
                }else if(action.equals("liberation")){
                    for(int i=0;i<3;i++)circle(out,i==1?TEAL:PURPLE,.035f,.9+i*.45+p,.1,1.1,i%2==0);
                }else{
                    for(int i=0;i<3;i++)circle(out,i%2==0?TEAL:PURPLE,.025f,.25+i*.12,(i-1)*.3,.8+p*2.2,true);
                }
            }
            case "iuno"->{
                if(action.equals("full_moon")){
                    circle(out,BLUE,.03f,3,-1.0,0,false);circle(out,GOLD,.018f,2.8,-.98,0,false);
                    for(int i=0;i<8;i++){double angle=i*Math.TAU/8;stroke(out,i%2==0?BLUE:GOLD,.015f,t->new Vertex(Math.cos(angle)*2.75,-.9+t*.4,Math.sin(angle)*2.75));}
                }else if(action.equals("intro")){
                    // Archived official intro: a raised moon and the falling
                    // ring surround the arriving body, rather than a bow shot.
                    circle(out,BLUE,.045f,.8+p*.35,.65,0,true);
                    circle(out,GOLD,.02f,.7+p*.3,.65,0,true);
                    circle(out,BLUE,.03f,1.2,-.8,0,false);
                    for(int i=0;i<5;i++){double a=i*Math.TAU/5;stroke(out,i%2==0?BLUE:GOLD,.018f,t->new Vertex(Math.cos(a)*.9,-.6+t*1.6,Math.sin(a)*.9));}
                }else if(action.contains("perfection")||action.equals("liberation")||action.equals("outro")){
                    double r=1+p*1.4;
                    circle(out,BLUE,.045f,r,0,1,true);circle(out,GOLD,.022f,r*1.08,-.6,1,false);
                    for(int sign:new int[]{-1,1})stroke(out,BLUE,.03f,t->new Vertex(sign*Math.sin(t*Math.PI)*r,Math.cos(t*Math.PI)*r,1+sign*Math.sin(t*Math.PI)*r*.6));
                }else if(action.contains("bow")||action.startsWith("skill_beyond")){
                    stroke(out,GOLD,.035f,t->new Vertex(Math.sin(t*Math.PI)*.6,-.9+t*1.8,.85));
                    stroke(out,BLUE,.018f,t->new Vertex(0,-.9+t*1.8,.85));
                    for(int i=-1;i<=1;i++){double side=i*.13;stroke(out,i==0?WHITE:BLUE,i==0?.045f:.022f,t->new Vertex(side,0,.8+t*4.2));}
                }else{
                    circle(out,BLUE,.05f,1.25+p*.4,0,1.15,true);
                    stroke(out,GOLD,.025f,t->{double a=-2+t*3.8;return new Vertex(Math.sin(a)*1.5,Math.cos(a)*.7,1.1+Math.cos(a)*.45);});
                }
            }
            case "changli"->{
                boolean phoenix=action.contains("sacrifice")||action.equals("liberation");
                if(phoenix){
                    for(int sign:new int[]{-1,1})for(int feather=0;feather<4;feather++){
                        double offset=feather*.18,length=2.5-feather*.25;
                        stroke(out,feather==0?GOLD:RED,.045f,t->new Vertex(sign*(.2+t*length),.25+Math.sin(t*Math.PI)*(.9-offset)-t*.3,1.2-t*.8+offset));
                    }
                    circle(out,RED,.055f,1+p*2.5,-.9,1,false);
                }else if(action.startsWith("true_sight")){
                    for(int sign:new int[]{-1,1})stroke(out,sign<0?GOLD:RED,.04f,t->new Vertex(sign*Math.sin(t*Math.PI)*.6,action.contains("charge")?-.5+t*2.1:Math.sin(t*Math.PI)*.25,.6+t*3.1));
                }else{
                    double sign=action.endsWith("02")||action.endsWith("04")?-1:1;
                    stroke(out,RED,.06f,t->{double a=-2.2+t*4.4;return new Vertex(Math.sin(a)*1.8,sign*Math.cos(a)*.7,1+Math.cos(a)*.7);});
                    stroke(out,GOLD,.018f,t->{double a=-2.2+t*4.4;return new Vertex(Math.sin(a)*1.85,sign*Math.cos(a)*.72,1+Math.cos(a)*.72);});
                }
            }
            case "qingxiao"->{
                if(action.startsWith("vast_sword")||action.equals("liberation")){
                    // Eight display swords sample the observed rain; the server's hit count is separate.
                    for(int i=0;i<8;i++){double side=(i%4-1.5)*.85,depth=1+i/4d;stroke(out,i%3==0?WHITE:TEAL,.04f,t->new Vertex(side,3.5-t*4.2,depth));}
                    circle(out,BLUE,.025f,1.4+p*1.5,-.9,1.6,false);
                }else if(action.contains("ascend")||action.startsWith("heavy_banishing")){
                    for(int sign:new int[]{-1,1})stroke(out,sign<0?WHITE:BLUE,.045f,t->new Vertex(sign*Math.sin(t*Math.PI)*1.1,-.6+t*2.8,.8+t*1.6));
                }else{
                    stroke(out,TEAL,.045f,t->{double a=-2+t*4;return new Vertex(Math.sin(a)*1.7,Math.cos(a)*.35,1.1+Math.cos(a)*.6);});
                    if(action.contains("immortal")||action.startsWith("skill"))for(int i=-1;i<=1;i++){double side=i*.32;stroke(out,BLUE,.025f,t->new Vertex(side,.1,.5+t*3.1));}
                }
            }
            case "lupa"->{
                if(action.contains("wolf_bite")||action.contains("sharp_claw")){
                    for(int sign:new int[]{-1,1})stroke(out,RED,.065f,t->new Vertex((t-.5)*2.8,sign*(.1+Math.sin(t*Math.PI)*.75),1.5));
                    for(int tooth=0;tooth<6;tooth++){double x=(tooth-2.5)*.36;for(int sign:new int[]{-1,1})stroke(out,GOLD,.022f,t->new Vertex(x,sign*(.6-t*.4),1.5));}
                }else if(action.equals("liberation")||action.equals("skill_break_enemy")){
                    for(int i=0;i<3;i++){
                        double side=(i-1)*1.0;
                        if(action.equals("liberation")){
                            // Publisher release: pointed flame petals, not straight light poles.
                            stroke(out,RED,.075f,t->{double lift=1-Math.abs(t*2-1);
                                return new Vertex(side+Math.sin(t*Math.TAU)*lift*.36,-.8+lift*3.1,1.8+lift*.12);});
                        }else stroke(out,RED,.075f,t->new Vertex(side,-.8+t*3.1,1.8));
                    }
                    circle(out,RED,.06f,1+p*2.7,-.85,1.2,false);
                }else{
                    for(int sign:new int[]{-1,1})stroke(out,sign<0?RED:GOLD,sign<0?.065f:.025f,t->new Vertex((t-.5)*(action.contains("wolf_dance")?4.4:3),sign*(t-.5)*1.5,1.2+Math.sin(t*Math.PI)*.35));
                }
            }
            case "carlotta"->{
                if(action.startsWith("death_knell")||action.contains("finale")||action.equals("liberation")){
                    for(int i=0;i<6;i++){double angle=i*Math.TAU/6;stroke(out,i%2==0?ROSE:BLUE,.035f,t->new Vertex(Math.cos(angle)*(.2+t*1.5),Math.sin(angle)*(.2+t*1.5),1.4));}
                    diamond(out,WHITE,0,0,1.4,.5+p*.3);
                    stroke(out,ROSE,.055f,t->new Vertex(0,0,.7+t*(action.contains("finale")?5:3.8)));
                }else if(action.startsWith("skill")||action.contains("end_of_road")){
                    for(int i=0;i<5;i++){double sign=i-2;stroke(out,i%2==0?BLUE:ROSE,.03f,t->new Vertex(sign*t*.45,.05+Math.sin(t*Math.PI)*.2,.6+t*3.4));}
                }else for(int sign:new int[]{-1,1})stroke(out,sign<0?BLUE:ROSE,.022f,t->new Vertex(sign*.07,0,.7+t*3));
            }
        }
        return List.copyOf(out);
    }
    private static void stroke(List<LightPath> out,int color,float width,DoubleFunction<Vertex> point){
        var vertices=new ArrayList<Vertex>();for(int i=0;i<=24;i++)vertices.add(point.apply(i/24d));out.add(new LightPath(vertices,color,width));
    }
    private static void circle(List<LightPath> out,int color,float width,double radius,double up,double forward,boolean upright){
        stroke(out,color,width,t->new Vertex(Math.cos(t*Math.TAU)*radius,up+(upright?Math.sin(t*Math.TAU)*radius:0),forward+(upright?0:Math.sin(t*Math.TAU)*radius)));
    }
    private static void diamond(List<LightPath> out,int color,double side,double up,double forward,double radius){
        stroke(out,color,.026f,t->{double a=t*4;int edge=Math.min(3,(int)a);double v=a-edge;double[] x={0,1,0,-1,0},y={1,0,-1,0,1};return new Vertex(side+(x[edge]*(1-v)+x[edge+1]*v)*radius,up+(y[edge]*(1-v)+y[edge+1]*v)*radius,forward);});
    }
}
