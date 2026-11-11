package cn.zgnhit.mcwuwa.client;

/** Source-observed silhouettes, sampled in the actor's horizontal local frame.
 * These points are decoration only: no hit boxes, resource grants or camera work.
 * Timing/scale use the accepted MC action; original GIF edits are not frame clocks. */
public final class NewRoleVfxProfiles {
    enum Kind { DUST, STAR, NOTE, SLASH, STONE, NONE }
    record Sample(double side,double up,double forward,int color,float size,Kind kind){}
    private static final int MINT=0xff82f3d0,WHITE=0xffeffff6,GOLD=0xffffdc8d,BLUE=0xff8dd4ff,VIOLET=0xffc7a2ff,PINK=0xffffa9c5;
    private NewRoleVfxProfiles(){}
    static boolean supports(String role){return switch(role){case "qiuyuan","ciaccona","shorekeeper","sigrika","galbrena"->true;default->false;};}
    static Sample sample(String full,double progress,double u,long elapsed){
        if(AeroSkillVfx.supports(full))return AeroSkillVfx.sample(full,progress,u);
        if(CamellyaVineVfx.supports(full))return CamellyaVineVfx.sample(full,progress,u);
        if(HiyukiFrostFormVfx.supports(full))return HiyukiFrostFormVfx.sample(full,progress,u);
        if(PilotCastContactVfx.supports(full))return PilotCastContactVfx.sample(full,progress,u);
        if(RemainingCastContactVfx.supports(full))return RemainingCastContactVfx.sample(full,progress,u);
        if(TideCasterContactVfx.supports(full))return TideCasterContactVfx.sample(full,progress,u);
        if(SealPuppetContactVfx.supports(full))return SealPuppetContactVfx.sample(full,progress,u);
        if(ScissorBambooVfx.supports(full))return ScissorBambooVfx.sample(full,progress,u);
        if(MelodyButterflyVfx.supports(full))return MelodyButterflyVfx.sample(full,progress,u);
        if(SigrikaSunVfx.supports(full))return SigrikaSunVfx.sample(full,progress,u);
        if(IunoMoonRingVfx.supports(full))return IunoMoonRingVfx.sample(full,progress,u);
        if(DeniaDreamVfx.supports(full))return DeniaDreamVfx.sample(full,progress,u);
        if(WolfCrystalVfx.supports(full))return WolfCrystalVfx.sample(full,progress,u);
        if(InkInfernalVfx.supports(full))return InkInfernalVfx.sample(full,progress,u);
        if(LucillaFilmVfx.supports(full))return LucillaFilmVfx.sample(full,progress,u);
        if(SuomingParasolVfx.supports(full))return SuomingParasolVfx.sample(full,progress,u);
        if(AugustaThunderVfx.supports(full))return AugustaThunderVfx.sample(full,progress,u);
        if(ZaniGuardBladeVfx.supports(full))return ZaniGuardBladeVfx.sample(full,progress,u);
        if(PilotNormalTimingVfx.supports(full))return PilotNormalTimingVfx.sample(full,progress,u);
        if(RemainingNormalAttackVfx.supports(full))return RemainingNormalAttackVfx.sample(full,progress,u);
        if(CasterNormalAttackVfx.supports(full))return CasterNormalAttackVfx.sample(full,progress,u);
        if(AerialSwordChainVfx.supports(full))return AerialSwordChainVfx.sample(full,progress,u);
        if(QingxiaoZitherVfx.supports(full))return QingxiaoZitherVfx.sample(full,progress,u);
        if(CarlottaGunflowerVfx.supports(full))return CarlottaGunflowerVfx.sample(full,progress,u);
        if(IunoMoonBowVfx.supports(full))return IunoMoonBowVfx.sample(full,progress,u);
        if(SupportNormalAttackVfx.supports(full))return SupportNormalAttackVfx.sample(full,progress,u);
        if(NextNormalAttackVfx.supports(full))return NextNormalAttackVfx.sample(full,progress,u);
        if(FiveNormalAttackVfx.supports(full))return FiveNormalAttackVfx.sample(full,progress,u);
        if(BlazePuppetAttackVfx.supports(full))return BlazePuppetAttackVfx.sample(full,progress,u);
        if(FanInfernalVfx.supports(full))return FanInfernalVfx.sample(full,progress,u);
        if(CantarellaTideVfx.supports(full))return CantarellaTideVfx.sample(full,progress,u);
        if(PhoebePrismVfx.supports(full))return PhoebePrismVfx.sample(full,progress,u);
        if(ScissorInkMelodyVfx.supports(full))return ScissorInkMelodyVfx.sample(full,progress,u);
        if(SupportBladeAttackVfx.supports(full))return SupportBladeAttackVfx.sample(full,progress,u);
        if(FiveEnhancedAttackVfx.supports(full))return FiveEnhancedAttackVfx.sample(full,progress,u);
        if(CrystalDreamAttackVfx.supports(full))return CrystalDreamAttackVfx.sample(full,progress,u);
        if(MoonSwordWolfAttackVfx.supports(full))return MoonSwordWolfAttackVfx.sample(full,progress,u);
        if(EnhancedAttackVfx.supports(full))return EnhancedAttackVfx.sample(full,progress,u);
        if(NextFiveReleaseVfx.supports(full))return NextFiveReleaseVfx.sample(full,progress,u);
        if(TideFireReleaseVfx.supports(full))return TideFireReleaseVfx.sample(full,progress,u);
        int slash=full.indexOf('/');if(slash<0)return null;
        String role=full.substring(0,slash),a=full.substring(slash+1);
        if(role.equals("zani"))return ZaniVfx.sample(a,progress,u,elapsed);
        if(role.equals("suoming"))return SuomingVfx.sample(a,progress,u,elapsed);
        if(role.equals("lucilla"))return LucillaVfx.sample(a,progress,u,elapsed);
        if(role.equals("augusta"))return AugustaVfx.sample(a,progress,u,elapsed);
        if(role.equals("zhezhi"))return ZhezhiVfx.sample(a,progress,u,elapsed);
        if(TideRoleVfx.supports(role))return TideRoleVfx.sample(role,a,progress,u,elapsed);
        if(NewSixRoleVfx.supports(role))return NewSixRoleVfx.sample(role,a,progress,u,elapsed);
        if(!supports(role))return SupplementalActionVfx.sample(full,progress,u);
        double p=Math.clamp(progress,0,1),angle=u*Math.TAU,arc=-2.1+u*4.2;
        // A held input is anticipation, not an accepted attack. Keep its cue
        // close to the hands instead of repeatedly drawing an attacking slash.
        if(a.equals("charge")){
            int color=switch(role){case "shorekeeper"->BLUE;case "sigrika"->GOLD;case "galbrena"->VIOLET;default->MINT;};
            return charge(color,u,elapsed);
        }
        int lane=(int)(u*24),stage=1,at=a.lastIndexOf("_0");
        if(at>=0&&at+2<a.length()&&Character.isDigit(a.charAt(at+2)))stage=a.charAt(at+2)-'0';
        return switch(role){
            case "qiuyuan"->{
                int c=lane%5==0?WHITE:MINT;
                if(a.equals("skill_run"))yield point((lane%2==0?-.3:.3),-.65+u*.12,-.2-u*2.5,c,.45f,Kind.DUST);
                if(a.startsWith("ink_heavy_03")||a.equals("drunken_exit"))
                    yield point((u-.5)*.20,2.4-u*3.5,1.3+p*1.8,c,1.15f,lane%9==0?Kind.STAR:Kind.DUST);
                if(a.startsWith("ink_heavy_02"))
                    yield point(Math.sin(arc)*1.9,-.8+u*2.8,1.15+Math.cos(arc)*.45,c,1,Kind.DUST);
                if(a.startsWith("ink_heavy_01"))
                    yield point(Math.sin(arc)*2.15,.12+Math.sin(arc*2)*.20,1.0+Math.cos(arc)*1.2,c,1,Kind.DUST);
                if(a.startsWith("liberation")){
                    if(lane%3==0)yield point(Math.cos(angle)*(1+p*3),u*2.5-.6,Math.sin(angle)*(1+p*3),MINT,.7f,Kind.DUST);
                    yield point(Math.sin(arc)*(1.3+p*2),.15+Math.cos(arc)*.15,Math.cos(arc)*(1.3+p*2),c,1.2f,Kind.DUST);
                }
                if(a.startsWith("skill")||a.equals("intro"))yield point((u-.5)*.14,.15+(u-.5)*.35,.5+u*3.6,c,.85f,Kind.DUST);
                yield point(Math.sin(arc)*(a.startsWith("answer_")?1.65:1.15),(stage%2==0?-1:1)*Math.cos(arc)*.4,1.0+Math.cos(arc)*.45,c,.65f,Kind.DUST);
            }
            case "ciaccona"->{
                boolean recital=a.equals("recital")||a.startsWith("solo_")||a.equals("liberation");
                if(recital){double r=1.5+(lane%3)*.12;yield point(Math.cos(angle+p)*r,-.4+(lane%3)*.23,Math.sin(angle+p)*r,lane%4==0?GOLD:MINT,.65f,lane%6==0?Kind.NOTE:Kind.DUST);}
                if(a.equals("liberation_note"))yield point(Math.cos(angle)*(1+p*3),-.35,Math.sin(angle)*(1+p*3),lane%3==0?GOLD:MINT,.8f,Kind.NOTE);
                if(a.contains("heavy_quartet"))yield point(Math.cos(angle)*(1.1+p*1.5),Math.sin(angle)*(.5+p*.25),1.3+p*2.3,lane%3==0?GOLD:MINT,1,Kind.DUST);
                if(a.equals("skill"))yield point(Math.sin(arc)*1.5,Math.cos(arc)*.55,1.2+Math.cos(arc)*.5,lane%4==0?GOLD:MINT,.8f,lane%7==0?Kind.NOTE:Kind.DUST);
                if(a.startsWith("air_"))yield point(Math.cos(angle)*.45,-u*1.7,.8+u*1.7,lane%4==0?GOLD:MINT,.65f,Kind.DUST);
                if(stage==4)yield point(Math.cos(angle)*1.25,Math.sin(angle)*.65,1.1+p*1.5,lane%3==0?GOLD:MINT,.7f,lane%6==0?Kind.NOTE:Kind.DUST);
                yield point(Math.sin(angle)*.10,Math.cos(angle)*.10,.75+u*2.5,lane%4==0?GOLD:MINT,.5f,lane%8==0?Kind.NOTE:Kind.DUST);
            }
            case "shorekeeper"->{
                int c=lane%5==0?GOLD:lane%2==0?BLUE:VIOLET;
                if(a.equals("intro_insight"))yield point((lane%3-1)*.8,2.6-u*3.1,1.4+p*.4,c,1.05f,lane%6==0?Kind.STAR:Kind.DUST);
                if(a.equals("air_evolution"))yield point(Math.cos(angle)*(.3+u),1.5-u*3,Math.sin(angle)*(.3+u),c,.9f,Kind.DUST);
                if(a.equals("heavy_deduction")){double r=2.5*(1-p)+.25;yield point(Math.cos(angle)*r,-.5+u*.7,Math.sin(angle)*r,c,.8f,Kind.DUST);}
                if(a.equals("liberation"))yield point(Math.cos(angle)*(1+p*3),-.95+(lane%3)*.05,Math.sin(angle)*(1+p*3),c,.75f,lane%5==0?Kind.STAR:Kind.DUST);
                if(a.equals("skill")||a.equals("intro"))yield point(Math.sin(angle)*(.45+u*.8),Math.cos(angle)*.65,.6+p*2.5,c,.7f,lane%7==0?Kind.STAR:Kind.DUST);
                yield point(Math.sin(arc)*(1+stage*.09),Math.cos(arc)*.45,.7+Math.cos(arc)*.65,c,.6f,lane%8==0?Kind.STAR:Kind.DUST);
            }
            case "sigrika"->{
                boolean sun=a.contains("sun"),chain=a.contains("chain"),burst=a.contains("burst");
                int c=sun||lane%4==0?GOLD:lane%3==0?WHITE:VIOLET;
                if(a.equals("skill_semantics")){
                    double radius=.5+p*2.7;yield point(Math.cos(angle)*radius,Math.sin(angle)*radius,1.4,c,1.05f,lane%6==0?Kind.STAR:Kind.DUST);
                }
                if(a.equals("liberation"))yield point(Math.cos(angle)*(1.2+p*2.8),-.4+Math.sin(angle)*.35,Math.sin(angle)*(1.2+p*2.8),c,1,Kind.STAR);
                if(sun)yield point(Math.cos(angle)*(.5+p*.8),Math.sin(angle)*(.5+p*.8),1.2+p*1.6,GOLD,.9f,lane%8==0?Kind.STAR:Kind.DUST);
                if(chain)yield point(Math.sin(arc)*2.1,Math.cos(arc)*.65,1.3+p*1.4,c,.95f,Kind.DUST);
                if(burst)yield point(Math.cos(angle)*(.5+p*1.7),Math.sin(angle)*(.5+p*1.7),1.3,c,1,Kind.DUST);
                if(a.contains("insight")||a.equals("counter_insight"))yield point(Math.cos(angle)*.7,Math.sin(angle)*.7,1.15,c,.85f,lane%7==0?Kind.STAR:Kind.DUST);
                yield point((stage%2==0?-.35:.35)+Math.sin(angle)*.25,Math.cos(angle)*.3,.7+u*1.0,c,.6f,Kind.DUST);
            }
            case "galbrena"->{
                int c=lane%5==0?0xffffb28b:lane%2==0?0xff8ab7ff:0xffc8a0ff;
                if(a.equals("liberation"))yield point(Math.sin(angle)*(.6+p*1.5),u*4.5-1,1.5+Math.cos(angle)*.45,c,1.1f,lane%8==0?Kind.STAR:Kind.DUST);
                if(a.equals("air_rain")||a.equals("demon_air_rain"))yield point((lane%2==0?-.3:.3),-.4-u*2.5,.4+(a.startsWith("demon")?u:0),c,.6f,Kind.DUST);
                if(a.equals("skill_ascend")||a.equals("demon_basic_04")||a.equals("demon_basic_05"))yield point((lane%2==0?-1:1)*(.3+u*1.8),Math.sin(u*Math.PI)*1.2-.25,.4+u*.8,c,.95f,Kind.DUST);
                if(a.contains("heavy")||a.contains("skill")||a.contains("counter"))yield point(Math.sin(arc)*1.7,Math.cos(arc)*.55,.9+p*1.7,c,.8f,Kind.DUST);
                if(a.equals("hellwalk"))yield point(Math.sin(angle)*.35,-.7,.5+u*2.4,c,.55f,Kind.DUST);
                yield point((lane%2==0?-.32:.32)+Math.sin(angle)*.05,Math.cos(angle)*.05,.8+u*3,c,.55f,lane%10==0?Kind.STAR:Kind.DUST);
            }
            default->null;
        };
    }
    private static Sample point(double x,double y,double z,int color,float size,Kind kind){return new Sample(x,y,z,color,size,kind);}
    static Sample charge(int color,double u,long elapsed){
        double radius=.08+.035*Math.sin(elapsed*.15),angle=u*Math.TAU;
        return point(.3+Math.cos(angle)*radius,-.12+Math.sin(angle)*radius,.5,color,.45f,Kind.DUST);
    }
    public record Vertex(double side,double up,double forward){}
    public record LightPath(java.util.List<Vertex> points,int color,float width){
        public LightPath{points=java.util.List.copyOf(points);}
    }
    /** Ordered continuous strokes are separate from scattered particle samples.
     * They retain their silhouettes at low particle settings and in first person. */
    /** Four vertices per decorative face; never used for collisions or damage. */
    public record LightSurface(java.util.List<Vertex> vertices,int color,String texture){
        public LightSurface(java.util.List<Vertex> vertices,int color){this(vertices,color,"");}
        public LightSurface{vertices=java.util.List.copyOf(vertices);if(vertices.size()!=4)throw new IllegalArgumentException("A surface needs four vertices");}
    }
    public static java.util.List<LightSurface> surfaces(String action,double progress){
        if(AutonomousContactVfx.supports(action))return AutonomousContactVfx.surfaces(action,progress);
        if(AeroSkillVfx.supports(action))return AeroSkillVfx.surfaces(action,progress);
        if(CamellyaLiberationBloomVfx.supports(action))return CamellyaLiberationBloomVfx.surfaces(action,progress);
        if(ResponseContactVfx.supports(action))return ResponseContactVfx.surfaces(action,progress);
        if(action.startsWith("qingxiao/")&&AerialSwordChainVfx.supports(action))return AerialSwordChainVfx.surfaces(action,progress);
        if(CamellyaVineVfx.supports(action))return CamellyaVineVfx.surfaces(action,progress);
        if(HiyukiFrostFormVfx.supports(action))return HiyukiFrostFormVfx.surfaces(action,progress);
        if(PilotCastContactVfx.supports(action))return PilotCastContactVfx.surfaces(action,progress);
        if(RemainingCastContactVfx.supports(action))return RemainingCastContactVfx.surfaces(action,progress);
        return retainedSurfaces(action,progress);
    }
    static java.util.List<LightSurface> retainedSurfaces(String action,double progress){
        if(TideCasterContactVfx.supports(action))return TideCasterContactVfx.surfaces(action,progress);
        if(SealPuppetContactVfx.supports(action))return SealPuppetContactVfx.surfaces(action,progress);
        if(ScissorBambooVfx.supports(action))return ScissorBambooVfx.surfaces(action,progress);
        if(MelodyButterflyVfx.supports(action))return MelodyButterflyVfx.surfaces(action,progress);
        if(SigrikaSunVfx.supports(action))return SigrikaSunVfx.surfaces(action,progress);
        if(IunoMoonRingVfx.supports(action))return IunoMoonRingVfx.surfaces(action,progress);
        if(DeniaDreamVfx.supports(action))return DeniaDreamVfx.surfaces(action,progress);
        if(WolfCrystalVfx.supports(action))return WolfCrystalVfx.surfaces(action,progress);
        if(InkInfernalVfx.supports(action))return InkInfernalVfx.surfaces(action,progress);
        if(LucillaFilmVfx.supports(action))return LucillaFilmVfx.surfaces(action,progress);
        if(SuomingParasolVfx.supports(action))return SuomingParasolVfx.surfaces(action,progress);
        if(AugustaThunderVfx.supports(action))return AugustaThunderVfx.surfaces(action,progress);
        if(ZaniGuardBladeVfx.supports(action))return ZaniGuardBladeVfx.surfaces(action,progress);
        if(PilotNormalTimingVfx.supports(action))return PilotNormalTimingVfx.surfaces(action,progress);
        if(RemainingNormalAttackVfx.supports(action))return RemainingNormalAttackVfx.surfaces(action,progress);
        if(SupportNormalAttackVfx.supports(action))return SupportNormalAttackVfx.surfaces(action,progress);
        if(NextNormalAttackVfx.supports(action))return NextNormalAttackVfx.surfaces(action,progress);
        if(CasterNormalAttackVfx.supports(action))return CasterNormalAttackVfx.surfaces(action,progress);
        if(FiveNormalAttackVfx.supports(action))return FiveNormalAttackVfx.surfaces(action,progress);
        if(BlazePuppetAttackVfx.supports(action))return BlazePuppetAttackVfx.surfaces(action,progress);
        if(FanInfernalVfx.supports(action))return FanInfernalVfx.surfaces(action,progress);
        if(CantarellaTideVfx.supports(action))return CantarellaTideVfx.surfaces(action,progress);
        if(PhoebePrismVfx.supports(action))return PhoebePrismVfx.surfaces(action,progress);
        if(ScissorInkMelodyVfx.supports(action))return ScissorInkMelodyVfx.surfaces(action,progress);
        if(SupportBladeAttackVfx.supports(action))return SupportBladeAttackVfx.surfaces(action,progress);
        if(FiveEnhancedAttackVfx.supports(action))return FiveEnhancedAttackVfx.surfaces(action,progress);
        if(CrystalDreamAttackVfx.supports(action))return CrystalDreamAttackVfx.surfaces(action,progress);
        if(MoonSwordWolfAttackVfx.supports(action))return MoonSwordWolfAttackVfx.surfaces(action,progress);
        if(EnhancedAttackVfx.supports(action))return EnhancedAttackVfx.surfaces(action,progress);
        if(action.startsWith("phrolova/"))return PhrolovaPilotVfx.surfaces(action,progress);
        return MornyePilotVfx.supports(action)?MornyePilotVfx.surfaces(action,progress):AemeathPilotVfx.supports(action)?AemeathPilotVfx.surfaces(action,progress):LynaePilotVfx.surfaces(action,progress);
    }
    public static java.util.List<LightPath> ribbons(String full,double progress){
        if(AeroSkillVfx.supports(full))return AeroSkillVfx.ribbons(full,progress);
        if(ResponseContactVfx.supports(full))return java.util.List.of();
        if(AutonomousContactVfx.supports(full))return AutonomousContactVfx.ribbons(full,progress);
        if(CamellyaVineVfx.supports(full))return CamellyaVineVfx.ribbons(full,progress);
        if(HiyukiFrostFormVfx.supports(full))return HiyukiFrostFormVfx.ribbons(full,progress);
        if(PilotCastContactVfx.supports(full))return PilotCastContactVfx.ribbons(full,progress);
        if(RemainingCastContactVfx.supports(full))return RemainingCastContactVfx.ribbons(full,progress);
        return retainedRibbons(full,progress);
    }
    static java.util.List<LightPath> retainedRibbons(String full,double progress){
        if(TideCasterContactVfx.supports(full))return TideCasterContactVfx.ribbons(full,progress);
        if(SealPuppetContactVfx.supports(full))return SealPuppetContactVfx.ribbons(full,progress);
        if(ScissorBambooVfx.supports(full))return ScissorBambooVfx.ribbons(full,progress);
        if(MelodyButterflyVfx.supports(full))return MelodyButterflyVfx.ribbons(full,progress);
        if(SigrikaSunVfx.supports(full))return SigrikaSunVfx.ribbons(full,progress);
        if(IunoMoonRingVfx.supports(full))return IunoMoonRingVfx.ribbons(full,progress);
        if(DeniaDreamVfx.supports(full))return DeniaDreamVfx.ribbons(full,progress);
        if(WolfCrystalVfx.supports(full))return WolfCrystalVfx.ribbons(full,progress);
        if(InkInfernalVfx.supports(full))return InkInfernalVfx.ribbons(full,progress);
        if(LucillaFilmVfx.supports(full))return LucillaFilmVfx.ribbons(full,progress);
        if(SuomingParasolVfx.supports(full))return SuomingParasolVfx.ribbons(full,progress);
        if(AugustaThunderVfx.supports(full))return AugustaThunderVfx.ribbons(full,progress);
        if(ZaniGuardBladeVfx.supports(full))return ZaniGuardBladeVfx.ribbons(full,progress);
        if(PilotNormalTimingVfx.supports(full))return PilotNormalTimingVfx.ribbons(full,progress);
        if(RemainingNormalAttackVfx.supports(full))return RemainingNormalAttackVfx.ribbons(full,progress);
        if(CasterNormalAttackVfx.supports(full))return CasterNormalAttackVfx.ribbons(full,progress);
        if(AerialSwordChainVfx.supports(full))return AerialSwordChainVfx.ribbons(full,progress);
        if(QingxiaoZitherVfx.supports(full))return QingxiaoZitherVfx.ribbons(full,progress);
        if(CarlottaGunflowerVfx.supports(full))return CarlottaGunflowerVfx.ribbons(full,progress);
        if(IunoMoonBowVfx.supports(full))return IunoMoonBowVfx.ribbons(full,progress);
        if(SupportNormalAttackVfx.supports(full))return SupportNormalAttackVfx.ribbons(full,progress);
        if(NextNormalAttackVfx.supports(full))return NextNormalAttackVfx.ribbons(full,progress);
        if(FiveNormalAttackVfx.supports(full))return FiveNormalAttackVfx.ribbons(full,progress);
        if(BlazePuppetAttackVfx.supports(full))return BlazePuppetAttackVfx.ribbons(full,progress);
        if(FanInfernalVfx.supports(full))return FanInfernalVfx.ribbons(full,progress);
        if(CantarellaTideVfx.supports(full))return CantarellaTideVfx.ribbons(full,progress);
        if(PhoebePrismVfx.supports(full))return PhoebePrismVfx.ribbons(full,progress);
        if(ScissorInkMelodyVfx.supports(full))return ScissorInkMelodyVfx.ribbons(full,progress);
        if(SupportBladeAttackVfx.supports(full))return SupportBladeAttackVfx.ribbons(full,progress);
        if(FiveEnhancedAttackVfx.supports(full))return FiveEnhancedAttackVfx.ribbons(full,progress);
        if(CrystalDreamAttackVfx.supports(full))return CrystalDreamAttackVfx.ribbons(full,progress);
        if(MoonSwordWolfAttackVfx.supports(full))return MoonSwordWolfAttackVfx.ribbons(full,progress);
        if(EnhancedAttackVfx.supports(full))return EnhancedAttackVfx.ribbons(full,progress);
        if(NextFiveReleaseVfx.supports(full))return NextFiveReleaseVfx.ribbons(full,progress);
        if(TideFireReleaseVfx.supports(full))return TideFireReleaseVfx.ribbons(full,progress);
        int slash=full.indexOf('/');if(slash<0)return java.util.List.of();
        String role=full.substring(0,slash),a=full.substring(slash+1);
        if(role.equals("zani"))return ZaniVfx.ribbons(a,progress);
        if(role.equals("suoming"))return SuomingVfx.ribbons(a,progress);
        if(role.equals("lucilla"))return LucillaVfx.ribbons(a,progress);
        if(role.equals("augusta"))return AugustaVfx.ribbons(a,progress);
        if(role.equals("zhezhi"))return ZhezhiVfx.ribbons(a,progress);
        if(TideRoleVfx.supports(role))return TideRoleVfx.ribbons(role,a,progress);
        if(NewSixRoleVfx.supports(role))return NewSixRoleVfx.ribbons(role,a,progress);
        if(!supports(role))return SupplementalActionVfx.ribbons(full,progress);
        double p=Math.clamp(progress,0,1);var paths=new java.util.ArrayList<LightPath>();
        switch(role){
            case "qiuyuan"->{
                if(a.equals("skill_run")){
                    for(int sign:new int[]{-1,1})curve(paths,MINT,.025f,t->new Vertex(sign*(.22+t*.28),-.65+Math.sin(t*Math.PI)*.12,-.2-t*2.5));
                    break;
                }
                if(a.startsWith("ink_heavy_03")||a.equals("drunken_exit")||a.equals("outro"))
                    curve(paths,MINT,.10f,t->new Vertex(0,2.6-t*3.5,1.0+p*1.7));
                else if(a.startsWith("ink_heavy_02"))curve(paths,MINT,.09f,t->new Vertex(Math.sin(-1.9+t*3.8)*1.8,-.9+t*2.8,1.25));
                else if(a.startsWith("liberation")){
                    circle(paths,MINT,.07f,1.4+p*2.3,-.45,0,false);
                    for(int i=0;i<5;i++){double theta=i*Math.TAU/5;double x=Math.cos(theta)*2.6,z=Math.sin(theta)*2.6;
                        curve(paths,MINT,.025f,t->new Vertex(x,-.9+t*2.7,z));}
                }else if(a.startsWith("skill")||a.equals("intro")){
                    curve(paths,MINT,.045f,t->new Vertex(0,.12,.4+t*3.3));
                    curve(paths,WHITE,.018f,t->new Vertex(.08,.18,.4+t*3.3));
                }else if(a.equals("air"))curve(paths,MINT,.06f,t->new Vertex(.1,1.4-t*3,1));
                else {
                    boolean reverse=a.endsWith("02")||a.endsWith("04");
                    curve(paths,a.startsWith("answer_")?WHITE:MINT,a.contains("heavy")?.09f:.045f,t->{double theta=-2+(reverse?1-t:t)*4;return new Vertex(Math.sin(theta)*(a.contains("heavy")?2:1.3),.1+Math.cos(theta)*.2,.8+Math.cos(theta)*.7);});
                }
            }
            case "ciaccona"->{
                if(a.equals("recital")||a.startsWith("solo_")||a.equals("liberation")||a.equals("outro")){
                    for(int i=0;i<3;i++)circle(paths,i==1?GOLD:MINT,.018f,1.65,-.45+i*.18,0,false);
                }else if(a.startsWith("liberation_note")){
                    circle(paths,a.endsWith("yellow")?GOLD:MINT,.045f,.65+p*3,-.9,0,false);
                    circle(paths,a.endsWith("yellow")?WHITE:GOLD,.018f,.85+p*3,-.85,0,false);
                }else if(a.contains("heavy_quartet")){
                    circle(paths,a.endsWith("yellow")?GOLD:MINT,.055f,.65+p*1.1,0,1.1+p*2.3,true);
                    circle(paths,a.endsWith("yellow")?WHITE:GOLD,.022f,.8+p*1.1,0,1.1+p*2.3,true);
                }else if(a.equals("basic_04")){
                    circle(paths,MINT,.03f,1.1+p*.5,0,1.1,true);
                    circle(paths,GOLD,.018f,1.25+p*.5,0,1.1,true);
                }else if(a.startsWith("air_")){
                    for(int sign:new int[]{-1,1})curve(paths,sign<0?MINT:GOLD,.025f,t->new Vertex(sign*.25,-t*2,.8+t*.6));
                }else if(a.equals("skill")||a.equals("heavy"))curve(paths,MINT,.045f,t->new Vertex(Math.sin(-2+t*4)*1.6,Math.cos(-2+t*4)*.5,1.1));
                else for(int i=0;i<2;i++){double x=i==0?-.1:.1;curve(paths,i==0?MINT:GOLD,.018f,t->new Vertex(x,0,.8+t*2.6));}
            }
            case "shorekeeper"->{
                if(a.equals("intro_insight")){for(int i=0;i<3;i++){double x=(i-1)*.75;curve(paths,i==1?GOLD:BLUE,.075f,t->new Vertex(x,2.7-t*3.6,1.4));}}
                else if(a.equals("air_evolution")){
                    for(int sign:new int[]{-1,1})curve(paths,sign<0?BLUE:GOLD,.035f,t->{double theta=t*Math.TAU+p*2;return new Vertex(sign*Math.cos(theta)*(.3+t),1.4-t*3,Math.sin(theta)*(.3+t));});
                }
                else if(a.equals("heavy_deduction"))curve(paths,VIOLET,.06f,t->{double r=.25+2*(1-p)*(1-t*.6),theta=t*Math.TAU*1.5;return new Vertex(Math.cos(theta)*r,-.55+t*.5,Math.sin(theta)*r);});
                else if(a.equals("liberation")||a.equals("outro")){circle(paths,BLUE,.04f,.8+p*1.8,-.8,0,false);circle(paths,GOLD,.02f,.6+p*1.8,-.75,0,false);}
                else {curve(paths,BLUE,.035f,t->new Vertex(Math.sin(t*Math.TAU)*1.05,Math.sin(t*Math.TAU*2)*.3,.9+Math.cos(t*Math.TAU)*.3));
                    curve(paths,GOLD,.018f,t->new Vertex(Math.sin(t*Math.TAU+.3)*1.05,Math.sin(t*Math.TAU*2+.3)*.3,.9+Math.cos(t*Math.TAU+.3)*.3));}
            }
            case "sigrika"->{
                if(a.contains("_chain"))for(int i=0;i<2;i++){double height=i*.35;curve(paths,VIOLET,.065f,t->new Vertex(Math.sin(-2+t*4)*2,height+Math.cos(-2+t*4)*.5,1.2));}
                else if(a.contains("_sun")||a.equals("liberation")){
                    circle(paths,GOLD,.055f,.8+p*.7,0,1.5,true);
                    for(int i=0;i<8;i++){double theta=i*Math.TAU/8;curve(paths,GOLD,.025f,t->new Vertex(Math.cos(theta)*(.9+t*.45),Math.sin(theta)*(.9+t*.45),1.5));}
                }else if(a.contains("_burst")||a.equals("skill_semantics")){
                    circle(paths,VIOLET,.07f,.6+p*1.5,0,1.5,true);circle(paths,GOLD,.025f,.8+p*1.5,0,1.5,true);
                    if(a.equals("skill_semantics"))for(int i=0;i<3;i++){double theta=i*Math.TAU/3;curve(paths,GOLD,.045f,t->new Vertex(Math.cos(theta)*t*2.3,Math.sin(theta)*t*2.3,1.5));}
                }else if(a.equals("air")||a.equals("air_counter"))circle(paths,GOLD,.04f,.5+p*.4,.6-p*2,1.2,false);
                else circle(paths,a.contains("insight")?GOLD:VIOLET,.035f,.35+p*.45,0,1.2,true);
            }
            case "galbrena"->{
                if(a.equals("demon_aura")){
                    double flutter=Math.sin(p*Math.TAU)*.05;
                    for(int sign:new int[]{-1,1})for(int layer=0;layer<2;layer++){
                        double offset=layer*.15;
                        curve(paths,layer==0?BLUE:VIOLET,layer==0?.045f:.025f,t->new Vertex(sign*(.22+t*1.25),.15+Math.sin(t*Math.PI)*(.5+flutter)-t*.4+offset,-.2-t*.55));
                    }
                    break;
                }
                if(a.equals("liberation")){
                    for(int i=0;i<3;i++){double x=(i-1)*.7;curve(paths,i==1?WHITE:VIOLET,.09f,t->new Vertex(x,-.8+t*4.1,1.5));}
                    circle(paths,BLUE,.04f,.9+p*1.8,-.75,1.5,false);
                }else if(a.contains("heavy")||a.contains("skill")||a.contains("counter")||a.equals("demon_basic_04")||a.equals("demon_basic_05")){
                    for(int sign:new int[]{-1,1})curve(paths,sign<0?BLUE:VIOLET,.065f,t->new Vertex(sign*(.15+t*1.8),Math.sin(t*Math.PI)*1.05-.25,.6+t*.65));
                }else if(a.contains("air_rain"))for(int sign:new int[]{-1,1})curve(paths,sign<0?BLUE:VIOLET,.022f,t->new Vertex(sign*.28,-.3-t*2.5,a.startsWith("demon_")?.3+t:.3));
                else if(a.equals("hellwalk")){
                    for(int sign:new int[]{-1,1})curve(paths,sign<0?BLUE:VIOLET,.03f,t->new Vertex(sign*.35,-.85,-t*2.4));
                }
                else if(a.equals("air")||a.equals("demon_air")){
                    for(int sign:new int[]{-1,1})curve(paths,sign<0?BLUE:VIOLET,.035f,t->new Vertex(sign*.25,.4-t*2.5,.7));
                }
                else for(int sign:new int[]{-1,1})curve(paths,sign<0?BLUE:GOLD,.022f,t->new Vertex(sign*.3,0,.85+t*3));
            }
        }
        return java.util.List.copyOf(paths);
    }
    private static void curve(java.util.List<LightPath> out,int color,float width,java.util.function.DoubleFunction<Vertex> f){
        var points=new java.util.ArrayList<Vertex>();for(int i=0;i<=24;i++)points.add(f.apply(i/24d));out.add(new LightPath(points,color,width));
    }
    private static void circle(java.util.List<LightPath> out,int color,float width,double radius,double up,double forward,boolean upright){
        curve(out,color,width,t->{double theta=t*Math.TAU;return new Vertex(Math.cos(theta)*radius,up+(upright?Math.sin(theta)*radius:0),forward+(upright?0:Math.sin(theta)*radius));});
    }
}
