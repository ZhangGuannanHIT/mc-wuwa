package cn.zgnhit.mcwuwa.client;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;
import com.mojang.blaze3d.pipeline.RenderPipeline;

/** Character-specific, code-native circuit ornaments. Read-only server snapshot rendering.
 * Frozen tutorial art supplies ornaments; live spectrum/timing are MC adaptations.
 * Tutorial example numbers/filled energy never authorize or replace actual resources.
 */
final class ForteHud {
    private static final int TRACK=0xaa162129,EDGE=0xb4c6d2d7;
    private static final RenderPipeline ART_PIPELINE=artPipeline();
    private static RenderPipeline artPipeline() {
        var base=RenderPipelines.GUI_TEXTURED;
        var builder=RenderPipeline.builder().withLocation(Identifier.fromNamespaceAndPath("mc_wuwa","pipeline/forte_keyed"))
            .withVertexShader(base.getVertexShader()).withFragmentShader(Identifier.fromNamespaceAndPath("mc_wuwa","core/forte_keyed"))
            .withVertexFormat(base.getVertexFormat(),base.getVertexFormatMode()).withCull(base.isCull())
            .withDepthTestFunction(base.getDepthTestFunction()).withDepthWrite(base.isWriteDepth());
        base.getBlendFunction().ifPresent(builder::withBlend);
        base.getSamplers().forEach(builder::withSampler);
        for(var uniform:base.getUniforms()) {
            if(uniform.textureFormat()==null)builder.withUniform(uniform.name(),uniform.type());
            else builder.withUniform(uniform.name(),uniform.type(),uniform.textureFormat());
        }
        return builder.build();
    }
    private ForteHud(){ }
    record Style(String primary,String secondary,int segments,int color,int accent,String motif){ }
    static Style style(String slug,String form,JsonObject values) {
        return switch(slug) {
            case "zani" -> new Style(form.equals("blazing")?"blaze":"redundant",form.equals("blazing")?"":"blaze",form.equals("blazing")?5:1,0xffffb65d,0xffffe6bc,"scale");
            case "augusta" -> new Style("war","power",2,0xffffc35a,0xffffe9b0,"scale");
            case "suoming" -> new Style("obsession","",8,0xffd28bff,0xfff5d5ff,"scale");
            case "zhezhi" -> new Style("spirit","zhezhi_insight",3,0xffbde6e4,0xffffedcf,"feather");
            case "lucilla" -> new Style("impression","",3,0xff9cbef5,0xffe0edff,"prism");
            case "changli" -> new Style("forte","",4,0xffff9071,0xffffdcad,"feather");
            case "carlotta" -> new Style(form.equals("gunflower")?"carlotta_mirror":"substance","crystal",form.equals("gunflower")?4:1,0xffff9fbd,0xffffe3ec,"prism");
            case "iuno" -> new Style("spirituality","",1,0xffc8c9ff,0xffffefc7,"orbit");
            case "lupa" -> new Style("wolf_flame","wolf_soul",2,0xffff8769,0xffffe0a9,"sword");
            case "denia" -> new Style(form.equals("disillusion")?"conformal":"particles","dark_core",1,0xffe391de,0xfff5d6ff,"orbit");
            case "qingxiao" -> new Style(form.equals("immortal")?"sword_intent":"qin_heart",form.equals("immortal")?"":"sword_rhyme",1,0xffa9e9d0,0xfff6ecd0,"sword");
            case "phoebe" -> new Style("gospel","prayer",4,0xfff2d994,0xffffffdc,"star");
            case "suisui" -> new Style(form.equals("rain")?"fragrant_message":"water_breath","",3,0xffa9e6d0,0xfff4e9bc,"feather");
            case "cantarella" -> new Style("trance","shiver",5,0xffb9a2ef,0xffcce9ff,"orbit");
            case "aemeath" -> new Style("synchronization","resonance",2,0xff8ce5ec,0xfff2a6d6,"star");
            case "lynae" -> new Style(form.equals("cruise")?"lumiflow":"overflow","true_color",3,form.equals("cruise")?0xffa4eafa:0xffe9dd79,0xffd5bcff,"prism");
            case "mornye" -> new Style(form.equals("observation")?"relative_motion":"rest_energy","",1,form.equals("observation")?0xffb0a7fb:0xfffda283,0xffbdeaf3,"orbit");
            case "jinhsi" -> new Style("incandescence","",1,0xffe9e8cb,0xffffeeb0,"scale");
            case "camellya" -> new Style("pistils","",1,0xffe8849b,0xffffb4c2,"vine");
            case "yinlin" -> new Style("judgment","",1,0xffbf91eb,0xffffbad8,"thread");
            case "aero_rover" -> new Style("wind","",2,0xff90d9bc,0xffc5f4e7,"wind");
            case "cartethyia" -> new Style(form.equals("cartethyia")?"swords":"resolve","",form.equals("cartethyia")?3:2,0xff8ee9d2,0xffd7ffff,"sword");
            case "yangyang_xuanling" -> new Style("stream","feathers",2,0xff96c1ed,0xffe1efff,"feather");
            case "hsin" -> new Style(form.equals("illuminated")?"illuminated_heart":"worldly_heart","edicts",form.equals("illuminated")?3:1,0xffe5c47e,0xffffefd0,"seal");
            case "hiyuki" -> new Style(form.equals("future")?"cold":"mind",form.equals("future")?"iai":"chill",3,0xffaddced,0xffe8f6ff,"snow");
            case "chisa" -> new Style(form.equals("saw")?"saw_heat_points":"saw_echo",form.equals("saw")?"saw_echo":"lifeline",1,form.equals("saw")?0xfff196b5:0xffeed2d8,0xffffafc9,"saw");
            case "qiuyuan" -> new Style("ink","",3,0xff7fd2a4,0xffdef6c2,"wind");
            case "ciaccona" -> new Style("melody","",3,0xffa5dba8,0xfff2e1a5,"music");
            case "shorekeeper" -> new Style("data","",5,0xff92bdff,0xffe0f6ff,"seal");
            case "sigrika" -> new Style("period","sun_energy",2,0xffd2b7f7,0xffffe9b5,"seal");
            case "galbrena" -> new Style(form.equals("demon")?"purifying_fire":"sin_fire","embers",1,0xff81adff,0xffffd5ae,"sword");
            case "phrolova" -> new Style("echoes","notes",1,0xffc494ba,0xfff0bfcd,"music");
            default -> throw new IllegalArgumentException("Unknown circuit "+slug);
        };
    }
    static void draw(GuiGraphics g,String slug,String form,JsonObject values,JsonObject maxima,JsonObject abilities,int center,int y) {
        Style s=style(slug,form,values);double fraction=ratio(value(values,s.primary),value(maxima,s.primary));
        long tick=Minecraft.getInstance().level==null?0:Minecraft.getInstance().level.getGameTime();
        boolean qualified=false;
        for(String id:java.util.List.of("basic","skill"))if(abilities.has(id)){
            var state=abilities.getAsJsonObject(id);
            qualified|=state.has("enhanced")&&state.get("enhanced").getAsBoolean()&&state.has("ready")&&state.get("ready").getAsBoolean();
        }
        if(slug.equals("zhezhi")){zhezhi(g,values,center,y,tick,qualified);return;}
        if(newSix(g,slug,form,values,maxima,s,center,y,tick,qualified))return;
        if(SourcedCircuitHud.draw(g,slug,form,values,center,y,tick,qualified))return;
        double wave=(Math.sin(tick*.16)+1)/2;
        boolean full=fraction>=.999&&value(maxima,s.primary)>0;
        int left=center-76,right=center+76;
        artwork(g,slug,form,values,center,y,full);
        if(slug.equals("sigrika"))for(int i=0;i<4;i++){int rune=(int)value(values,"rune_"+i);diamond(g,center-18+i*12,y-8,3,rune==1?0xffffd18b:rune==2?0xff9dceff:TRACK);}
        if(slug.equals("hsin"))hsin(g,form,values,center,y,fraction,tick);
        if(slug.equals("cartethyia")&&s.primary.equals("swords")){swords(g,values,center,y);return;}
        // The sourced frame is the baseline. A second rectangular outline would
        // cross its curved end caps and make them look like detached ornaments.
        g.fill(left,y-2,right,y,TRACK);
        int end=left+(int)Math.round(152*fraction);
        if(end>left)g.fill(left,y-1,end,y,alpha(s.accent,180));
        if(full){int a=45+(int)(55*wave);g.fill(left,y-3,right,y-1,alpha(s.color,a));g.fill(left,y+6,right,y+8,alpha(s.color,a));}
        else if(end>left)g.fill(end-1,y-1,end+1,y+6,alpha(s.accent,180));
        if(qualified&&!full){diamond(g,left-5,y+2,2,alpha(s.accent,160+(int)(70*wave)));diamond(g,right+5,y+2,2,alpha(s.accent,160+(int)(70*wave)));}
        for(int i=1;i<s.segments;i++){int x=left+152*i/s.segments;g.fill(x-1,y-3,x+1,y,TRACK);}
        if(slug.equals("hsin")){ornament(g,left-7,y+2,s.motif,s.accent,full,wave);ornament(g,right+7,y+2,s.motif,s.accent,full,wave);}
        if(!slug.equals("phrolova"))spectrum(g,slug,s,center,y,fraction,tick);
        if(slug.equals("lynae")&&form.equals("cruise")) {
            for(int i=0;i<3;i++){int x=left+i*51;if(end>x)g.fill(x,y+3,Math.min(x+49,end),y+4,new int[]{0xff8ae5e4,0xffe49fda,0xffd2c0fa}[i]);}
        }
        if(slug.equals("cartethyia")&&s.primary.equals("swords")) {
            for(int i=0;i<3;i++){int x=center+(i-1)*48;g.fill(x,y-7,x+1,y-1,value(values,"swords")>=i+1?s.accent:0xff56646c);g.fill(x-2,y-3,x+3,y-2,s.accent);}
        }
        double maximum=value(maxima,s.secondary);
        if(maximum>24){
            int filled=(int)Math.round(110*Math.clamp(value(values,s.secondary)/maximum,0,1));
            g.fill(center-55,y+9,center+55,y+11,TRACK);if(filled>0)g.fill(center-55,y+9,center-55+filled,y+11,s.accent);
        }else if(maximum>0) {
            int count=(int)Math.min(24,maximum),spacing=count>10?6:10;
            int start=center-(count-1)*spacing/2;
            for(int i=0;i<count;i++) {
                int color=value(values,s.secondary)>=i+1?s.accent:0xff536069;
                int x=start+i*spacing;
                if(slug.equals("aemeath"))x=center+new int[]{-50,-27,27,50}[i];
                if(slug.equals("phrolova")) {
                    double note=value(values,"note_"+i);
                    if(note>0)color=switch((int)note){case 1->0xfff57891;case 2->0xff9e85fa;default->0xffffd8ed;};
                    g.fill(x-2,y-7,x+2,y-5,color);g.fill(x+1,y-12,x+2,y-6,color);g.fill(x+1,y-12,x+4,y-11,color);
                }else if(slug.equals("lynae")) {
                    x=center+(i-1)*49;g.renderOutline(x-22,y+6,44,5,0xaa8bd5e3);
                    if(value(values,s.secondary)>=i+1)g.fill(x-20,y+7,x+20,y+10,new int[]{0xff7ae2b8,0xff90e6f0,0xffdab3f1}[i]);
                }else diamond(g,x,y-(slug.equals("aemeath")?15:19),count>10?1:2,color);
            }
        }
        if(slug.equals("yangyang_xuanling")) {
            double oath=value(values,"oath");for(int i=0;i<6;i++)g.fill(center-17+i*6,y+8,center-14+i*6,y+9,oath>=i+1?s.accent:TRACK);
        }
        if(slug.equals("chisa")&&form.equals("saw")) {
            // Actual consumed total is a fine lower accent; remaining saw echoes stay the main gauge.
            int amount=(int)Math.round(152*ratio(value(values,"saw_consumed"),value(maxima,"saw_consumed")));
            if(amount>0)g.fill(left,y+7,left+amount,y+8,s.accent);
        }
        if(slug.equals("chisa")&&!form.equals("saw")) {
            int amount=(int)Math.round(152*ratio(value(values,"lifeline"),value(maxima,"lifeline")));
            g.fill(left,y+7,right,y+8,TRACK);if(amount>0)g.fill(left,y+7,left+amount,y+8,0xffe4c17a);
        }
    }
    /** Server supplies the cursor and perfect arc. This render path cannot
     * complete a charge or grant a resource. No opaque screen-covering disk. */
    static void focusRing(GuiGraphics g,JsonObject data,int cx,int cy){
        double cursor=Math.clamp(value(data,"cursor"),0,1);
        double half=Math.clamp(value(data,"halfWidth"),0,.5);
        for(int i=0;i<128;i++){
            double phase=i/128d,angle=phase*Math.PI*2-Math.PI/2;
            boolean perfect=Math.abs(phase-.5)<=half;
            int x=cx+(int)Math.round(Math.cos(angle)*24),y=cy+(int)Math.round(Math.sin(angle)*24);
            g.fill(x-1,y-1,x+1,y+1,perfect?0xffb8e9ff:0x9953667d);
        }
        double angle=cursor*Math.PI*2-Math.PI/2;
        int color=data.has("perfect")&&data.get("perfect").getAsBoolean()?0xfffff0b5:0xffe2edff;
        for(int r=19;r<=29;r++){
            int x=cx+(int)Math.round(Math.cos(angle)*r),y=cy+(int)Math.round(Math.sin(angle)*r);
            g.fill(x-1,y-1,x+1,y+1,color);
        }
    }
    /** Continuous, resource-driven fallback art for the six newly imported roles.
     * It uses no nonexistent source PNG or baked example energy. */
    private static boolean newSix(GuiGraphics g,String role,String form,JsonObject v,JsonObject caps,Style s,int c,int y,long tick,boolean ready) {
        if(!java.util.Set.of("changli","carlotta","iuno","lupa","denia","qingxiao").contains(role))return false;
        int left=c-76,right=c+76;double pulse=.5+.5*Math.sin(tick*.14);
        double max=value(caps,s.primary);if(role.equals("carlotta")&&form.equals("gunflower"))max=4;
        double progress=ratio(value(v,s.primary),max);
        boolean full=progress>=.999,lit=full||ready;
        // The track and its two tapered ends share the exact same pixel edge;
        // there is no separate detached ornament or blank seam at either end.
        continuous(g,left,right,y,progress,s.color,s.accent,lit,pulse);
        switch(role) {
            case "changli" -> {
                int count=(int)value(v,"forte");
                for(int i=0;i<4;i++) {
                    int x=left+19+i*38,col=i<count?s.accent:0x805d6874;
                    for(int row=0;row<6;row++){int half=Math.max(1,5-row);g.fill(x-half,y-4-row,x+half+1,y-3-row,col);}
                    g.fill(x-1,y-4,x+2,y+1,col);
                    if(i<3)g.fill(left+(i+1)*38,y,left+(i+1)*38+1,y+4,TRACK);
                }
                duration(g,left,right,y+8,value(v,"changli_true_sight"),240,s.accent);
                if(value(v,"changli_flaming_feather")>0)diamond(g,c,y-13,2,0xffffe0a1);
            }
            case "carlotta" -> {
                int crystals=(int)value(v,"crystal");
                for(int i=0;i<6;i++){int x=c-50+i*20;diamond(g,x,y-6,2,i<crystals?s.accent:0x80687789);g.fill(x,y-4,x+1,y,i<crystals?s.color:0x50687789);}
                if(form.equals("gunflower"))for(int i=1;i<4;i++)g.fill(left+i*38,y,left+i*38+1,y+4,TRACK);
                if(form.equals("gunflower"))duration(g,left,right,y+8,value(v,"carlotta_gunflower"),200,s.accent);
                else if(value(v,"carlotta_tinted_crystal")<=0)diamond(g,right+5,y+2,2,full?0xffffdc7e:0xff9d92ab);
            }
            case "iuno" -> {
                boolean bow=form.equals("new_moon"),phase=!form.equals("normal");
                int moon=phase?s.accent:0xff77828e;
                for(int row=-6;row<=6;row++){int half=(int)Math.sqrt(Math.max(0,36-row*row));int cut=bow?half/2:Math.max(0,half-2);if(half>0)g.fill(c-half,y-9+row,c+half+1,y-8+row,moon);if(cut>0)g.fill(c-cut+(bow?2:0),y-10+row,c+cut+1+(bow?2:0),y-9+row,0xff18242d);}
                if(phase)duration(g,left,right,y+8,value(v,"iuno_phase"),300,s.accent);
                else if(value(v,"iuno_full_moon")>0)duration(g,left,right,y+8,value(v,"iuno_full_moon"),600,s.accent);
                if(value(v,"iuno_full_moon")>0)diamond(g,right+5,y+2,2,0xffffe4a3);
            }
            case "lupa" -> {
                for(int i=0;i<2;i++){int x=c-14+i*28;int color=value(v,"wolf_soul")>=i+1?s.accent:0xff626a74;diamond(g,x,y-7,4,color);g.fill(x,y-3,x+1,y,color);}
                duration(g,left,right,y+8,value(v,"lupa_soul_lifetime"),200,s.accent);
                if(value(v,"lupa_match_point")>0){g.fill(left,y+5,right,y+6,alpha(0xffffa757,120+(int)(90*pulse)));}
                if(value(v,"lupa_assist_ready")>0)diamond(g,right+5,y+2,2,0xffffdc7e);
            }
            case "denia" -> {
                int cores=(int)value(v,"dark_core"),cap=(int)value(caps,"dark_core");
                for(int i=0;i<cap;i++){int x=c+(i*14)-(cap-1)*7;diamond(g,x,y-8,3,i<cores?s.accent:0xff626476);g.fill(x,y-5,x+1,y,i<cores?s.color:0x50626476);}
                if(form.equals("disillusion"))duration(g,left,right,y+8,value(v,"particles"),100,0xffeaa2c1);
                if(value(v,"denia_entropy_dark")>0||value(v,"denia_entropy_stage")>0){diamond(g,left-5,y+2,2,0xffffd58f);diamond(g,right+5,y+2,2,0xffffd58f);}
            }
            case "qingxiao" -> {
                if(!form.equals("immortal"))continuous(g,left,right,y+7,ratio(value(v,"sword_rhyme"),value(caps,"sword_rhyme")),0xffe6d8ae,0xfffff1cd,value(v,"sword_rhyme")>=100,pulse);
                else {g.fill(c,y-11,c+1,y-1,s.accent);g.fill(c-4,y-5,c+5,y-4,s.accent);diamond(g,c,y-12,2,s.accent);}
                if(value(v,"qingxiao_flight")>0)duration(g,left,right,y+14,value(v,"flight_qi"),value(caps,"flight_qi"),0xffb7d9fa);
            }
            default -> { }
        }
        if(lit) {
            int spark=left+(int)((tick%76)*2);g.fill(spark,y-1,Math.min(right,spark+3),y,alpha(s.accent,100+(int)(100*pulse)));
        }
        return true;
    }
    private static void continuous(GuiGraphics g,int left,int right,int y,double progress,int color,int accent,boolean lit,double pulse) {
        g.fill(left,y-1,right,y+5,0xb4344652);g.fill(left,y,right,y+4,TRACK);
        int end=left+(int)Math.round((right-left)*Math.clamp(progress,0,1));
        if(end>left){g.fill(left,y+1,end,y+3,color);g.fill(left,y,end,y+1,alpha(accent,190));}
        for(int row=0;row<3;row++){int inset=2-row;g.fill(left-3+row,y+inset,left,y+4-inset,0xb4b6c6ce);g.fill(right,y+inset,right+3-row,y+4-inset,0xb4b6c6ce);}
        if(lit){int glow=45+(int)(45*pulse);g.fill(left,y-2,right,y-1,alpha(accent,glow));g.fill(left,y+5,right,y+6,alpha(accent,glow));}
    }
    private static void duration(GuiGraphics g,int left,int right,int y,double value,double maximum,int color) {
        if(value<=0||maximum<=0)return;
        int end=left+(int)Math.round((right-left)*ratio(value,maximum));g.fill(left,y,right,y+1,0x70526472);if(end>left)g.fill(left,y,end,y+1,color);
    }
    private static void spectrum(GuiGraphics g,String slug,Style style,int center,int y,double fraction,long tick) {
        for(int i=0;i<50;i++) {
            int x=center-75+i*3;
            if((slug.equals("aemeath")||slug.equals("aero_rover")||slug.equals("cartethyia"))&&Math.abs(x-center)<10)continue;
            boolean filled=(i+.5)/50<=fraction;
            double beat=(Math.sin(i*.57+tick*.13)+Math.sin(i*.21-tick*.07)+2)/4;
            int height=filled?4+(int)(beat*10):3,color=filled?(slug.equals("hiyuki")?0xffeca7d8:style.color):0x7d849095;
            g.fill(x,y-height,x+2,y-1,color);
            if(filled)g.fill(x,y-height,x+2,y-height+1,style.accent);
        }
    }
    /** Connected authored brush frame; three live30-point regions and two insight seals. */
    private static void zhezhi(GuiGraphics g,JsonObject values,int center,int y,long tick,boolean qualified){
        int ink=0xffa6ceca,paper=0xfffff0df,dim=0xff455d62;
        int left=center-76,right=center+76;
        g.fill(left,y-2,right,y+4,0xb01b2d32);
        g.fill(left,y+4,right,y+5,ink);
        double spirit=Math.clamp(value(values,"spirit"),0,90);
        int end=left+(int)Math.round(152*spirit/90);
        if(end>left){g.fill(left,y,end,y+3,ink);g.fill(left,y,end,y+1,paper);}
        for(int i=1;i<3;i++){int x=left+152*i/3;g.fill(x,y-2,x+1,y+4,dim);}
        for(int side:new int[]{-1,1}){
            int x=center+side*76;
            for(int step=0;step<5;step++){
                int a=x+side*step,b=x+side*(step+2);
                g.fill(Math.min(a,b),y+3-step,Math.max(a,b)+1,y+4-step,ink);
            }
            int seal=center+side*7;diamond(g,seal,y-7,2,value(values,"zhezhi_insight")>=(side<0?1:2)?paper:dim);
        }
        if(qualified){int alpha=140+(int)(80*(Math.sin(tick*.15)+1)/2);g.fill(left,y+5,right,y+6,alpha(paper,alpha));}
    }
    /** Sprite UV cropping happens during rendering; original PNG pixels are unmodified. */
    static void sprite(GuiGraphics g,String file,int sx,int sy,int sw,int sh,int x,int y,int width,int height) {
        var texture=Identifier.fromNamespaceAndPath("mc_wuwa","textures/gui/forte/t_roleskilltree"+file+".png");
        g.blit(ART_PIPELINE,texture,x,y,(float)sx,(float)sy,width,height,sw,sh,1668,152);
    }
    /** One source-to-screen transform for the entire frame. Adjacent rectangles share
     * rounded edges, so neither UV margins nor GUI scale can pull an end cap away.
     * The central tutorial resource window is excluded; only live values fill it. */
    record SourceFrame(String file,int panel,int trackLeft,int trackRight,int baseline,int center,int y) {
        int x(int source){return center-76+(int)Math.round((source-trackLeft)*152.0/(trackRight-trackLeft));}
        int y(int source){return y+(int)Math.round((source-baseline)*152.0/(trackRight-trackLeft));}
        void part(GuiGraphics g,int left,int top,int right,int bottom){
            int x0=x(left),y0=y(top),x1=x(right),y1=y(bottom);
            if(x1>x0&&y1>y0)sprite(g,file,left+panel,top,right-left,bottom-top,x0,y0,x1-x0,y1-y0);
        }
        void frame(GuiGraphics g,int left,int top,int right,int bottom,int baseBottom){
            part(g,left,top,trackLeft,bottom);
            part(g,trackRight,top,right,bottom);
            part(g,trackLeft,baseline,trackRight,baseBottom);
        }
    }
    private static void artwork(GuiGraphics g,String slug,String form,JsonObject values,int center,int y,boolean full) {
        String file=switch(slug) {
            case "aemeath"->value(values,"resonance")>=4?"aimisi3":"aimisi1";case "lynae"->form.equals("cruise")?"linnai2":"linnai1";
            case "mornye"->"moning";case "jinhsi"->"jinxi";case "camellya"->"chun";case "yinlin"->"yingling";
            case "aero_rover"->"piaobozhefeng";case "cartethyia"->"katixiya";
            case "yangyang_xuanling"->value(values,"feathers")>=2?"xuanling3":"xuanling2";
            case "hiyuki"->"feixue1";case "chisa"->form.equals("saw")?"qianxiao4":"qianxiao1";case "phrolova"->"fuluoluo";
            default->"";
        };
        if(file.isEmpty())return; // Hsin's frozen tutorial URLs are404; retain labelled drawn fallback.
        if(slug.equals("cartethyia")&&form.equals("cartethyia"))return; // Three discrete swords, not a continuous energy bar.
        int panel=slug.equals("mornye")&&form.equals("observation")||slug.equals("cartethyia")&&!form.equals("cartethyia")?885:0;
        int trackLeft=slug.equals("camellya")?215:slug.equals("phrolova")?180:slug.equals("chisa")?165:slug.equals("jinhsi")||slug.equals("yinlin")?155:140;
        int trackRight=slug.equals("camellya")?680:slug.equals("phrolova")?670:650;
        int baseY=switch(slug){case "aemeath","mornye"->96;case "lynae"->106;case "jinhsi"->54;case "yinlin"->62;case "aero_rover"->72;
            case "camellya"->87;case "hiyuki"->80;case "phrolova"->105;case "cartethyia"->68;case "chisa"->72;default->94;};
        var frame=new SourceFrame(file,panel,trackLeft,trackRight,baseY,center,y);
        int bottom=switch(slug){case "aemeath","mornye","lynae"->128;case "jinhsi"->64;case "yinlin"->70;
            case "camellya"->140;case "hiyuki"->113;case "phrolova"->108;case "cartethyia"->104;case "yangyang_xuanling"->124;default->102;};
        frame.frame(g,slug.equals("camellya")?35:70,15,slug.equals("camellya")?720:730,bottom,slug.equals("chisa")?78:bottom);
        if(slug.equals("chisa"))frame.part(g,trackLeft,88,trackRight,102); // Teeth below the live lifeline, never its baked example fill.
        if(slug.equals("aemeath")||slug.equals("aero_rover"))
            frame.part(g,350,28,440,baseY);
        if(slug.equals("cartethyia"))frame.part(g,382,0,408,baseY);
        if(slug.equals("hiyuki"))for(int i=0;i<2;i++)frame.part(g,267+i*188,62,327+i*188,baseY);
        if(slug.equals("lynae")) {
            frame.part(g,140,27,355,59);
            frame.part(g,440,27,650,59);
            diamond(g,center,y-18,3,0xffaee8ef); // Numeric window deliberately omitted by user preference.
        }
        if(slug.equals("phrolova")) {
            for(int row=0;row<5;row++){
                int lineY=frame.y(69+row*9);
                g.fill(frame.x(trackLeft),lineY,frame.x(trackRight),lineY+1,0x906d253c);
            }
        }
    }
    private static void swords(GuiGraphics g,JsonObject values,int center,int y) {
        String[] shadows={"outsider_sword","divine_sword","human_sword"};
        String[] rights={"outsider_power","divine_intent","human_right"};
        int[] colors={0xff8ccfff,0xff7cebd6,0xffc8b7ff};
        for(int i=0;i<3;i++) {
            int x=center+(i-1)*46;
            if(value(values,rights[i])>0){
                // The sourced tutorial sprite includes the recovered authority halo.
                // Collecting a world shadow changes the circuit appearance, not to empty.
                sprite(g,"katixiya",170+i*167,10,100,99,x-13,y-23,26,26);
            }
            else if(value(values,shadows[i])>0){
                int c=colors[i];g.fill(x,y-21,x+1,y-1,c);g.fill(x-3,y-8,x+4,y-6,c);diamond(g,x,y-20,2,c);
                for(int n=-3;n<=3;n++){int h=2+(int)(Math.sin(n*.9)*2+2);g.fill(x+n*3,y-10-h,x+n*3+1,y-10+h,alpha(c,115));}
            }
            else{ornament(g,x,y-10,"sword",0xff6e8086,false,0);}
            if(i<2)diamond(g,x+23,y-9,2,0xff89dacd);
        }
    }
    /** Complete vector frame for unavailable Xin tutorial bitmaps. This is authored
     * palace/eave ornamentation; it never impersonates recovered original pixels. */
    private static void hsin(GuiGraphics g,String form,JsonObject values,int center,int y,double fill,long tick) {
        boolean light=form.equals("illuminated");int ink=light?0xffe6d4a1:0xffe3a48b,dim=0x805e7880;
        g.fill(center-87,y+7,center+88,y+8,ink);
        for(int side:new int[]{-1,1}) {
            int x=center+side*87;
            for(int step=0;step<5;step++){int a=x+side*step*2;g.fill(Math.min(a,a+side*4),y-6-step,Math.max(a,a+side*4)+1,y-5-step,ink);}
            g.fill(x-2,y-6,x+3,y+7,ink);g.fill(x-7,y+7,x+8,y+8,ink);
            diamond(g,x,y-13,3,ink);g.fill(x,y-10,x+1,y-6,ink);
        }
        // Three suspended palace pavilions in light aspect; one heart seal in worldly.
        int n=light?3:1;
        for(int i=0;i<n;i++) {
            int x=center+(i-(n-1)/2)*35,top=y-16;
            boolean lit=fill>=(i+1)/(double)n;int c=lit?ink:dim;
            for(int row=0;row<4;row++)g.fill(x-row*2-1,top+row,x+row*2+2,top+row+1,c);
            g.fill(x-6,top+5,x-5,top+10,c);g.fill(x+5,top+5,x+6,top+10,c);g.fill(x-7,top+10,x+8,top+11,c);
            if(lit&&((tick/12+i)%3==0))diamond(g,x,top-3,1,ink);
        }
        if(light&&value(values,"edicts")>0)for(int i=0;i<7;i++) {
            int x=center-36+i*12;int c=value(values,"edicts")>i*3?ink:dim;
            g.fill(x-2,y+10,x+3,y+11,c);g.fill(x,y+9,x+1,y+13,c);
        }
    }
    private static void ornament(GuiGraphics g,int x,int y,String motif,int color,boolean full,double wave) {
        int size=full?3:2;diamond(g,x,y,size,color);
        switch(motif) {
            case "star","snow" -> {g.fill(x-5,y,x+6,y+1,color);g.fill(x,y-4,x+1,y+5,color);}
            case "vine","feather","wind" -> {for(int i=0;i<4;i++)g.fill(x-6+i*3,y+(i%2==0?-3:3),x-4+i*3,y+(i%2==0?-2:4),color);}
            case "thread","music" -> {g.fill(x-5,y-4,x-4,y+5,color);g.fill(x+4,y-4,x+5,y+5,color);}
            case "saw" -> {for(int i=-3;i<=3;i+=2)g.fill(x+i,y-4,x+i+1,y+5,color);}
            case "scale","seal","sword" -> {g.fill(x-5,y-2,x-4,y+3,color);g.fill(x+4,y-2,x+5,y+3,color);}
            default -> {g.fill(x-4,y-3,x-3,y+4,color);g.fill(x+3,y-3,x+4,y+4,color);}
        }
        if(full&&wave>.65)diamond(g,x,y-6,1,color);
    }
    static void diamond(GuiGraphics g,int x,int y,int radius,int color){for(int dy=-radius;dy<=radius;dy++){int dx=radius-Math.abs(dy);g.fill(x-dx,y+dy,x+dx+1,y+dy+1,color);}}
    static int alpha(int color,int alpha){return color&0x00ffffff|alpha<<24;}
    private static double value(JsonObject object,String key){return object.has(key)?object.get(key).getAsDouble():0;}
    private static double ratio(double value,double maximum){return maximum>0?Math.clamp(value/maximum,0,1):0;}
}
