package cn.zgnhit.mcwuwa.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Edits the existing client HUD offsets, with live clamped rendering and reversible defaults. */
public final class HudLayoutScreen extends Screen {
    private final Screen parent;private CombatHud.Part part=CombatHud.Part.TEAM;private Button selection,visibility;
    private boolean compact(){return height<230;}
    private int top(){return Math.max(18,(height-(compact()?154:188))/2);}
    public HudLayoutScreen(Screen parent){super(Component.literal("鸣潮 · HUD布局"));this.parent=parent;}
    @Override protected void init(){
        int span=Math.min(288,width-24),left=(width-span)/2,top=top(),buttonHeight=compact()?16:20;
        selection=addRenderableWidget(Button.builder(Component.empty(),b->{part=CombatHud.Part.values()[(part.ordinal()+1)%CombatHud.Part.values().length];refresh();}).bounds(left,top,span,buttonHeight).build());
        int[] steps={-10,-1,1,10};
        for(int axis=0;axis<2;axis++)for(int i=0;i<steps.length;i++){
            boolean vertical=axis==1;int delta=steps[i];
            addRenderableWidget(Button.builder(Component.literal((delta>0?"+":"")+delta),b->CombatHud.move(part,vertical,delta)).bounds(left+i*(span+4)/4,top+(compact()?36:46)+axis*(compact()?33:44),(span-12)/4,buttonHeight).build());
        }
        visibility=addRenderableWidget(Button.builder(Component.empty(),b->{CombatHud.toggle(part);refresh();}).bounds(left,top+(compact()?106:136),(span-4)/2,buttonHeight).build());
        addRenderableWidget(Button.builder(Component.literal("重置当前区域"),b->{CombatHud.reset(part);refresh();}).bounds(left+(span+4)/2,top+(compact()?106:136),(span-4)/2,buttonHeight).build());
        addRenderableWidget(Button.builder(Component.literal("全部恢复默认"),b->{CombatHud.resetLayout();refresh();}).bounds(left,top+(compact()?130:160),(span-4)/2,buttonHeight).build());
        addRenderableWidget(Button.builder(Component.literal("完成"),b->onClose()).bounds(left+(span+4)/2,top+(compact()?130:160),(span-4)/2,buttonHeight).build());refresh();
    }
    private void refresh(){selection.setMessage(Component.literal("调整区域："+part.label+"  ›"));visibility.setMessage(Component.literal("区域显示："+(CombatHud.visible(part)?"开启":"关闭")));}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        super.render(g,mouseX,mouseY,partial);int top=top();
        g.drawCenteredString(font,title,width/2,top-16,0xfff1f2ed);
        g.drawCenteredString(font,Component.literal("水平偏移："+CombatHud.offsetValue(part,false)),width/2,top+(compact()?24:31),0xffc5d7df);
        g.drawCenteredString(font,Component.literal("垂直偏移："+CombatHud.offsetValue(part,true)),width/2,top+(compact()?57:75),0xffc5d7df);
        g.drawCenteredString(font,Component.literal("正值向右／向下，超出屏幕时自动限制"),width/2,top+(compact()?92:116),0xffaab3be);
    }
    @Override public void onClose(){CombatHud.saveLayout();minecraft.setScreen(parent);}
}
