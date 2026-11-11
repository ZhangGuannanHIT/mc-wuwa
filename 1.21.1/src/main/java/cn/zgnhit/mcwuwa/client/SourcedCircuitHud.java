package cn.zgnhit.mcwuwa.client;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import static cn.zgnhit.mcwuwa.client.ForteHud.*;

/** Original tutorial ornaments around live resources; no tutorial example gauge is copied. */
final class SourcedCircuitHud {
    private static final int DIM=0x806f7c88;
    private SourcedCircuitHud(){ }
    static boolean draw(GuiGraphics g,String role,String form,JsonObject values,int center,int y,long tick,boolean qualified){
        switch(role){
            case "phrolova"->score(g,values,center,y,tick);
            case "shorekeeper"->shore(g,values,center,y,tick,qualified);
            case "sigrika"->runes(g,values,center,y,tick,qualified);
            case "galbrena"->fire(g,form,values,center,y,tick,qualified);
            case "qiuyuan","ciaccona"->aero(g,role,values,center,y,tick,qualified);
            default->{return false;}
        }
        return true;
    }
    private static void score(GuiGraphics g,JsonObject v,int c,int y,long tick){
        var frame=new SourceFrame("fuluoluo",0,180,670,105,c,y);
        frame.frame(g,70,16,725,120,108);
        for(int line=0;line<5;line++)g.fill(c-76,y-11+line*3,c+76,y-10+line*3,0x906d253c);
        int count=(int)Math.clamp(value(v,"notes"),0,6);
        int converting=(int)value(v,"phrolova_color_conversion");
        boolean finalNote=value(v,"final_note")>0&&converting==0;
        boolean conducting=value(v,"conducting")>0;
        int current=(int)value(v,"note_position");
        for(int i=0;i<6;i++){
            int x=c-60+i*24;
            if(i>=count){diamond(g,x,y-4,2,DIM);continue;}
            int kind=(int)value(v,"note_"+i);
            if(finalNote||conducting&&current==i||converting>0&&i==(12-converting)/2){
                int opacity=45+(int)(35*(.5+.5*Math.sin(tick*.2+i*.4)));
                g.fill(x-9,y-25,x+10,y+4,alpha(kind==2?0xffbd93ff:0xffff859d,opacity));
            }
            // Three independent source glyphs; tutorial example fills are never
            // copied into empty slots. Color has the original higher flame crest.
            if(kind==2)sprite(g,"fuluoluo",290,14,55,104,x-9,y-28,18,34);
            else sprite(g,"fuluoluo",kind==1?450:220,48,50,70,x-8,y-18,16,23);
            if(conducting&&current==i)diamond(g,x,y+7,2,0xffffdeed);
        }
        // Echo stacks remain a separate live gauge below the six-note score.
        int end=c-76+(int)Math.round(152*Math.clamp(value(v,"echoes")/24,0,1));
        g.fill(c-76,y+11,c+76,y+12,DIM);
        if(end>c-76)g.fill(c-76,y+11,end,y+12,0xffdc91b3);
    }
    private static void shore(GuiGraphics g,JsonObject v,int c,int y,long tick,boolean ready){
        int data=(int)Math.clamp(value(v,"data"),0,5);boolean full=data==5;
        int color=full?0xffffdc75:0xffd0dce7;
        new SourceFrame("shouanren",full?885:0,140,650,78,c,y).frame(g,75,20,725,100,100);
        for(int group=0;group<5;group++)for(int bar=0;bar<6;bar++){
            int x=c-75+group*31+bar*4;boolean filled=group<data;
            int h=filled?4+(int)(6*(.5+.5*Math.sin(bar*.8+group*.7+tick*.12))):3;
            g.fill(x,y-h,x+2,y,filled?color:DIM);
            if(filled)g.fill(x,y-h,x+2,y-h+1,0xfffff1c3);
        }
        int stage=(int)value(v,"domain_stage");
        if(stage>0){
            int[] colors={0xff9edbff,0xffc7a6ff,0xffffdf96};
            for(int i=0;i<3;i++)diamond(g,c-12+i*12,y-18,2,i<stage?colors[i]:DIM);
            duration(g,c,y+5,value(v,"domain_ticks"),Math.max(1,value(v,"domain_max")),colors[Math.clamp(stage-1,0,2)]);
        }
        if(full||ready)halo(g,c,y,color,tick);
    }
    private static void runes(GuiGraphics g,JsonObject v,int c,int y,long tick,boolean ready){
        double period=Math.clamp(value(v,"period"),0,100);
        // Use the empty frame in every state. Its tutorial's alternate panel
        // contains a baked half/full period line, not a live resource layer.
        new SourceFrame("xigelika",0,150,630,99,c,y).frame(g,95,97,690,143,143);
        int end=c-76+(int)(152*period/100);
        if(end>c-76){g.fill(c-76,y+6,end,y+7,0xffffdea1);g.fill(c-76,y+7,end,y+8,0x80cb9feb);}
        for(int i=0;i<4;i++){
            int x=c-57+i*38,kind=(int)value(v,"rune_"+i);
            if(kind==1||kind==2)sprite(g,"xigelika1",kind==1?145:275,20,110,82,x-15,y-24,30,23);

        }
        double energy=Math.clamp(value(v,"sun_energy"),0,60);
        for(int i=0;i<6;i++)diamond(g,c-25+i*10,y+9,1,energy>=(i+1)*10?0xffffe8ac:DIM);
        // The two stored rune-copy effects are distinct; they can coexist.
        if(value(v,"echo")>0)diamond(g,c-83,y-22,2,0xffefe4ff);
        if(value(v,"focus")>0)diamond(g,c+83,y-22,2,0xffffdc91);
        for(int i=0;i<6;i++)if(value(v,"blessings")>i)g.fill(c-15+i*5,y-30,c-12+i*5,y-29,0xffe9d2a8);
        if(period>=100||ready)halo(g,c,y,0xffe8c4ff,tick);
    }
    private static void fire(GuiGraphics g,String form,JsonObject v,int c,int y,long tick,boolean ready){
        boolean demon=form.equals("demon");double amount=Math.clamp(value(v,demon?"purifying_fire":"sin_fire"),0,100);
        int offset=demon?885:0;
        int baseline=demon?86:98;
        var frame=new SourceFrame("jiabeilina",offset,demon?145:160,demon?650:610,baseline,c,y);
        frame.frame(g,70,16,730,128,demon?93:105);
        if(!demon)frame.part(g,265,20,340,baseline);
        for(int i=0;i<50;i++){
            int x=c-75+i*3;boolean filled=(i+.5)*2<=amount;
            int height=filled?4+(int)(8*(.5+.5*Math.sin(i*.42+tick*.16))):3;
            int color=demon?0xffb1b3ff:i<17?0xffffba8e:0xffaaa9fb;
            g.fill(x,y-height,x+2,y,filled?color:DIM);
            if(filled)g.fill(x,y-height,x+2,y-height+1,0xffe4dcff);
        }
        for(int i=0;i<5;i++)diamond(g,c-20+i*10,y+9,2,value(v,"embers")>=(i+1)*8?0xffffba83:DIM);
        if(value(v,"inner_burn_ticks")>0)duration(g,c,y-18,value(v,"inner_burn_ticks"),80,0xffffba83);
        if(!demon&&amount>=100||ready)halo(g,c,y,0xffc5bcff,tick);
    }
    private static void halo(GuiGraphics g,int c,int y,int color,long tick){
        int opacity=45+(int)(40*(.5+.5*Math.sin(tick*.15)));
        g.fill(c-76,y-3,c+76,y-1,alpha(color,opacity));
        for(int sign:new int[]{-1,1})diamond(g,c+sign*81,y-2,2,alpha(color,150+opacity));
    }
    private static void aero(GuiGraphics g,String role,JsonObject v,int c,int y,long tick,boolean ready){
        boolean sword=role.equals("qiuyuan");double fraction=Math.clamp(value(v,sword?"ink":"melody")/(sword?600:3),0,1);
        boolean full=fraction>=1;int color=full?(sword?0xff8effdc:0xffffe49a):0xffa5d9b0;
        if(sword)new SourceFrame("qiuyuan",0,150,630,89,c,y).frame(g,95,78,690,113,113);
        else new SourceFrame("xiakong",0,145,650,72,c,y).frame(g,108,40,690,98,98);
        for(int group=0;group<3;group++)for(int bar=0;bar<13;bar++){
            int x=c-75+group*52+bar*4;boolean filled=(group*13+bar+.5)/39<=fraction;
            int height=filled?4+(int)(7*(.5+.5*Math.sin(group*.8+bar*.65+tick*.12))):3;
            g.fill(x,y-height,x+2,y,filled?color:DIM);
            if(filled)g.fill(x,y-height,x+2,y-height+1,0xffe8ffe8);
        }
        if(full||ready)halo(g,c,y,color,tick);
        if(sword&&value(v,"drunk_ticks")>0){
            int stage=(int)value(v,"ink_stage");
            for(int i=1;i<=3;i++)diamond(g,c+(i-2)*13,y-19,2,i==stage?0xffe8fff4:DIM);
            duration(g,c,y+5,value(v,"drunk_ticks"),160,0xff8effdc);
        }
    }
    private static void duration(GuiGraphics g,int c,int y,double remaining,double maximum,int color){
        int half=24,end=c-half+(int)Math.round(half*2*Math.clamp(remaining/maximum,0,1));
        g.fill(c-half,y,c+half,y+1,DIM);if(end>c-half)g.fill(c-half,y,end,y+1,color);
    }
    private static double value(JsonObject o,String key){return o.has(key)?o.get(key).getAsDouble():0;}
}
