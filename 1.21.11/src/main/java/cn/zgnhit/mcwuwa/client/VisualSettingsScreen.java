package cn.zgnhit.mcwuwa.client;

import cn.zgnhit.mcwuwa.platform.ClientVisualConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Client-only settings. No combat command or server-authoritative value is modified. */
public final class VisualSettingsScreen extends Screen {
    private final Screen parent;
    private Button own,other,budget,skins,body,overlays;
    private int step(){return height<220?18:22;}
    private int top(){return Math.max(18,(height-step()*7-32)/2);}
    public VisualSettingsScreen(Screen parent){super(Component.literal("鸣潮 · 战斗表现"));this.parent=parent;}
    @Override protected void init() {
        int span=Math.min(260,width-32),left=(width-span)/2,top=top(),step=step(),buttonHeight=step-4;
        own=addRenderableWidget(Button.builder(Component.empty(),button->{ClientVisualConfig.OWN_INTENSITY.set(nextIntensity(ClientVisualConfig.OWN_INTENSITY.get()));refresh();}).bounds(left,top,span,buttonHeight).build());
        other=addRenderableWidget(Button.builder(Component.empty(),button->{ClientVisualConfig.OTHER_INTENSITY.set(nextIntensity(ClientVisualConfig.OTHER_INTENSITY.get()));refresh();}).bounds(left,top+step,span,buttonHeight).build());
        budget=addRenderableWidget(Button.builder(Component.empty(),button->{int value=ClientVisualConfig.PARTICLE_BUDGET.get();ClientVisualConfig.PARTICLE_BUDGET.set(value<64?64:value<128?128:value<256?256:value<512?512:0);refresh();}).bounds(left,top+step*2,span,buttonHeight).build());
        skins=addRenderableWidget(Button.builder(Component.empty(),button->{ClientVisualConfig.AUTOMATIC_ROLE_SKINS.set(!ClientVisualConfig.AUTOMATIC_ROLE_SKINS.get());refresh();}).bounds(left,top+step*3,span,buttonHeight).build());
        body=addRenderableWidget(Button.builder(Component.empty(),button->{ClientVisualConfig.COMBAT_BODY_MODEL.set(!ClientVisualConfig.COMBAT_BODY_MODEL.get());refresh();}).bounds(left,top+step*4,span,buttonHeight).build());
        overlays=addRenderableWidget(Button.builder(Component.empty(),button->{ClientVisualConfig.QUIET_INTEGRATION_OVERLAYS.set(!ClientVisualConfig.QUIET_INTEGRATION_OVERLAYS.get());refresh();}).bounds(left,top+step*5,span,buttonHeight).build());
        addRenderableWidget(Button.builder(Component.literal("HUD位置与显示"),button->minecraft.setScreen(new HudLayoutScreen(this))).bounds(left,top+step*6,span,buttonHeight).build());
        addRenderableWidget(Button.builder(Component.literal("恢复默认"),button->{ClientVisualConfig.defaults();refresh();}).bounds(left,top+step*7,(span-4)/2,buttonHeight).build());
        addRenderableWidget(Button.builder(Component.literal("完成"),button->onClose()).bounds(left+(span+4)/2,top+step*7,(span-4)/2,buttonHeight).build());refresh();
    }
    private static double nextIntensity(double value){for(int percent:new int[]{0,25,45,50,75,100})if(percent>Math.round(value*100))return percent/100.0;return 0;}
    private void refresh() {
        own.setMessage(Component.literal("自己的技能特效："+Math.round(ClientVisualConfig.OWN_INTENSITY.get()*100)+"%"));
        other.setMessage(Component.literal("其他玩家特效："+Math.round(ClientVisualConfig.OTHER_INTENSITY.get()*100)+"%"));
        budget.setMessage(Component.literal("粒子数量上限："+ClientVisualConfig.PARTICLE_BUDGET.get()+" / 秒"));
        skins.setMessage(Component.literal("自动角色皮肤："+(ClientVisualConfig.AUTOMATIC_ROLE_SKINS.get()?"开启":"关闭")));
        body.setMessage(Component.literal("第三人称战斗外形："+(ClientVisualConfig.COMBAT_BODY_MODEL.get()?"内置连贯骨架":"跟随其他模型模组")));
        overlays.setMessage(Component.literal("兼容叠加界面避让："+(ClientVisualConfig.QUIET_INTEGRATION_OVERLAYS.get()?"开启":"关闭")));
    }
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial) {
        super.render(graphics,mouseX,mouseY,partial);
        graphics.drawCenteredString(font,title,width/2,top()-16,0xFFFFFFFF);
        graphics.drawCenteredString(font,Component.literal("皮肤仅改变外观，特效设置仅影响本地显示"),width/2,Math.min(height-10,top()+step()*7+24),0xFFAAB3BE);
    }
    @Override public void onClose(){ClientVisualConfig.SPEC.save();minecraft.setScreen(parent);}
}
