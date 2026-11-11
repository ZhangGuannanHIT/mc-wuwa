package cn.zgnhit.mcwuwa.platform.entity;

import cn.zgnhit.mcwuwa.core.CharacterId;
import cn.zgnhit.mcwuwa.core.SurveyMotorcycleRules;
import cn.zgnhit.mcwuwa.platform.ModAttachments;
import cn.zgnhit.mcwuwa.platform.ModItems;
import net.minecraft.network.syncher.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.nbt.CompoundTag;

import net.minecraft.world.phys.Vec3;

/** Native vehicle controls, collision, mounting and persistence; no combat resource authority. */
public final class SurveyMotorcycleEntity extends Boat implements ConstructVisual {
    private float wheelDegrees;
    private float steeringDegrees, previousVisualYaw,wingExpansion;
    private float previousWheelDegrees,previousSteeringDegrees,previousWingExpansion;
    private Vec3 lastPosition;
    private final SurveyMotorcycleRules.Aerial aerial=new SurveyMotorcycleRules.Aerial();
    private int seenLift;
    private long lastAutoHop=-100;
    public float wheelDegrees(float partial){return net.minecraft.util.Mth.rotLerp(partial,previousWheelDegrees,wheelDegrees);}
    public float steeringDegrees(float partial){return net.minecraft.util.Mth.lerp(partial,previousSteeringDegrees,steeringDegrees);}
    public float wingExpansion(float partial){return net.minecraft.util.Mth.lerp(partial,previousWingExpansion,wingExpansion);}
    private static final EntityDataAccessor<Float> KINETIC=SynchedEntityData.defineId(SurveyMotorcycleEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> CAP=SynchedEntityData.defineId(SurveyMotorcycleEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> BOOST=SynchedEntityData.defineId(SurveyMotorcycleEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> GLIDE=SynchedEntityData.defineId(SurveyMotorcycleEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> LEAPS=SynchedEntityData.defineId(SurveyMotorcycleEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> AIR_ENERGY=SynchedEntityData.defineId(SurveyMotorcycleEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFT=SynchedEntityData.defineId(SurveyMotorcycleEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> LIFT_AT=SynchedEntityData.defineId(SurveyMotorcycleEntity.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> LIFT_SPEED=SynchedEntityData.defineId(SurveyMotorcycleEntity.class,EntityDataSerializers.FLOAT);
    public SurveyMotorcycleEntity(EntityType<? extends Boat> type,Level level){super(type,level);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder data){super.defineSynchedData(data);data.define(KINETIC,1000f);data.define(CAP,1000f);data.define(BOOST,false);
        data.define(GLIDE,false);data.define(LEAPS,SurveyMotorcycleRules.LEAP_CAPACITY);data.define(AIR_ENERGY,SurveyMotorcycleRules.GLIDE_CAPACITY);data.define(LIFT,0);data.define(LIFT_AT,0L);data.define(LIFT_SPEED,0f);}
    @Override public float getGroundFriction(){return .9f;}
    @Override protected net.minecraft.sounds.SoundEvent getPaddleSound(){return null;}
    @Override protected int getMaxPassengers(){return 1;}
    @Override protected boolean canAddPassenger(net.minecraft.world.entity.Entity rider){return rider instanceof Player&&super.canAddPassenger(rider);}
    @Override protected float getSinglePassengerXOffset(){return 0;}
    @Override public net.minecraft.world.item.Item getDropItem(){return ModItems.SURVEY_MOTORCYCLE.get();}
    @Override protected Vec3 getPassengerAttachmentPoint(net.minecraft.world.entity.Entity rider, net.minecraft.world.entity.EntityDimensions size, float scale){
        Vec3 point=super.getPassengerAttachmentPoint(rider,size,scale);return new Vec3(point.x,.65*scale,point.z);
    }
    /** Real collision remains native. Horizontal motion stays in the native boat input protocol. */
    public void applyLandMotion(){
        Vec3 velocity=getDeltaMovement();
        boolean boost=boosting()&&kinetic()>0&&!isInWater();
        double friction=isInWater()?.8:boost?.95:.9;
        double y=velocity.y-(isNoGravity()?0:.04);
        int lift=entityData.get(LIFT);
        if(lift!=seenLift){
            seenLift=lift;
            // Native boats are driven by the local rider. Apply each server-authorized
            // impulse exactly once there, rather than fighting vehicle position packets.
            if(getControllingPassenger()!=null&&level().getGameTime()-entityData.get(LIFT_AT)<=12)
                y=entityData.get(LIFT_SPEED);
        }
        if(getControllingPassenger()!=null&&!isInWater()&&fullyGrounded()&&level().getGameTime()-lastAutoHop>8&&velocity.horizontalDistanceSqr()>.0025){
            Vec3 ahead=new Vec3(velocity.x,0,velocity.z).normalize().scale(Math.max(.25,velocity.horizontalDistance()));
            var obstacle=getBoundingBox().move(ahead);
            if(!level().noCollision(this,obstacle)&&level().noCollision(this,obstacle.move(0,1.001,0))){
                y=Math.max(y,.30);lastAutoHop=level().getGameTime();
            }
        }
        if(gliding()&&getControllingPassenger()!=null&&!isInWater()&&y<=0)y=boost?0:Math.max(y,-.035);
        setDeltaMovement(velocity.x*friction,y,velocity.z*friction);
    }
    /** Both axle contact patches must be supported before aerial charges can regenerate. */
    public boolean fullyGrounded(){
        Vec3 axis=Vec3.directionFromRotation(0,getYRot()).scale(.48);
        for(int side:new int[]{-1,1}){
            Vec3 at=position().add(axis.scale(side));
            if(level().noCollision(this,new net.minecraft.world.phys.AABB(at.x-.18,at.y-.09,at.z-.18,at.x+.18,at.y+.02,at.z+.18)))return false;
        }
        return true;
    }
    public boolean gliding(){return entityData.get(GLIDE);}
    /** Native movement validation recognizes only an occupied, funded glide as legitimate flight. */
    public boolean isFlyingVehicle(){return gliding()&&glideEnergy()>0&&getControllingPassenger()!=null&&!isInWater();}
    public int leapCharges(){return entityData.get(LEAPS);}
    public int glideEnergy(){return entityData.get(AIR_ENERGY);}
    public void jumpInput(Player driver,boolean pressed,boolean cancel,int button){
        if(level().isClientSide()||getControllingPassenger()!=driver||button<0||button>1)return;
        boolean usable=driver.isAlive()&&!isInWater()&&driver.containerMenu==driver.inventoryMenu;
        if(cancel){aerial.cancelInput();syncAerial();return;}
        if(!usable)return;
        if(pressed)aerial.press(level().getGameTime(),fullyGrounded(),button);
        else lift(aerial.release(level().getGameTime(),fullyGrounded(),true,button));
        syncAerial();
    }
    private void lift(SurveyMotorcycleRules.Lift lift){
        if(lift==SurveyMotorcycleRules.Lift.NONE)return;
        entityData.set(LIFT_SPEED,lift==SurveyMotorcycleRules.Lift.CHARGED?.68f:.34f);
        entityData.set(LIFT_AT,level().getGameTime());entityData.set(LIFT,entityData.get(LIFT)+1);
        if(level() instanceof net.minecraft.server.level.ServerLevel server){
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,getX(),getY()+.2,getZ(),12,.45,.1,.45,.04);
            server.playSound(null,blockPosition(),net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_LAUNCH,net.minecraft.sounds.SoundSource.PLAYERS,.35f,lift==SurveyMotorcycleRules.Lift.CHARGED?.75f:1.35f);
        }
    }
    private void syncAerial(){entityData.set(GLIDE,aerial.gliding());entityData.set(LEAPS,aerial.charges());entityData.set(AIR_ENERGY,aerial.energy());}
    public void restoreKinetic(float stored){entityData.set(KINETIC,Float.isFinite(stored)?Math.clamp(stored,0,1600):1000);}
    public void limitDriveSpeed(){
        Vec3 velocity=getDeltaMovement();double speed=velocity.horizontalDistance(),limit=boosting()&&kinetic()>0?.75:.4;
        if(speed>limit)setDeltaMovement(velocity.x*limit/speed,velocity.y,velocity.z*limit/speed);
    }
    public net.minecraft.world.item.ItemStack packedItem(){
        var stack=ModItems.SURVEY_MOTORCYCLE.get().getDefaultInstance();
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA,stack,tag->tag.putFloat("mc_wuwa_kinetic",kinetic()));
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA,stack,tag->{tag.putInt("mc_wuwa_leaps",leapCharges());tag.putInt("mc_wuwa_air_energy",glideEnergy());});
        return stack;
    }
    public float kinetic(){return entityData.get(KINETIC);}
    public float kineticCapacity(){return entityData.get(CAP);}
    public boolean boosting(){return entityData.get(BOOST);}
    public boolean toggleBoost(Player driver){
        if(level().isClientSide()||getControllingPassenger()!=driver)return false;
        entityData.set(BOOST,!boosting()&&kinetic()>0);return true;
    }
    @Override public void tick(){
        previousWheelDegrees=wheelDegrees;previousSteeringDegrees=steeringDegrees;previousWingExpansion=wingExpansion;
        freezeRidingSurface();
        super.tick();
        boolean moving=getDeltaMovement().horizontalDistanceSqr()>1e-6||lastPosition!=null&&position().subtract(lastPosition).horizontalDistanceSqr()>1e-6;
        lastPosition=position();
        if(!level().isClientSide()){
            Player driver=getControllingPassenger() instanceof Player player?player:null;boolean lynae=false;
            if(driver!=null){var team=ModAttachments.team(driver).state();for(int i=0;i<3;i++)if(team.at(i).filter(role->role.character()==CharacterId.LYNAE).isPresent())lynae=true;}
            float cap=(float)SurveyMotorcycleRules.capacity(lynae);entityData.set(CAP,cap);
            if(driver==null||isInWater()||kinetic()<=0)entityData.set(BOOST,false);
            entityData.set(KINETIC,(float)SurveyMotorcycleRules.advance(kinetic(),cap,boosting(),moving,1));
            if(kinetic()<=0)entityData.set(BOOST,false);
            lift(aerial.tick(level().getGameTime(),fullyGrounded(),driver!=null&&driver.isAlive()&&!isInWater()));syncAerial();
        }
        if(isInWater())entityData.set(BOOST,false);
        Vec3 velocity=getDeltaMovement();double speed=velocity.horizontalDistance();
        double limit=(boosting()&&kinetic()>0?.75:.4);
        limitDriveSpeed();
        wheelDegrees=(wheelDegrees+(float)(Math.min(speed,limit)*180/(Math.PI*.35)))%360;
        float turn=net.minecraft.util.Mth.wrapDegrees(getYRot()-previousVisualYaw);previousVisualYaw=getYRot();
        steeringDegrees+=(Math.clamp(-turn*5,-28,28)-steeringDegrees)*.35f;
        wingExpansion+=( (gliding()?1f:0f)-wingExpansion)*.35f;
    }
    /** Frost Walker-style surface road. Run before movement so the front wheel reaches ice, not water. */
    private void freezeRidingSurface(){
        if(!(level() instanceof net.minecraft.server.level.ServerLevel server)||getControllingPassenger()==null)return;
        var center=net.minecraft.core.BlockPos.containing(getX(),getY()+.05,getZ()).below();
        if(getY()-(center.getY()+1)>.5)return;
        var ice=net.minecraft.world.level.block.Blocks.FROSTED_ICE.defaultBlockState();
        for(var pos:net.minecraft.core.BlockPos.betweenClosed(center.offset(-3,0,-3),center.offset(3,0,3))){
            if(pos.distSqr(center)>9)continue;
            var water=server.getBlockState(pos);
            // Only exposed source water: no waterlogged blocks, flowing streams, or submerged roads.
            if(!water.is(net.minecraft.world.level.block.Blocks.WATER)||!water.getFluidState().isSource()
                ||!server.getBlockState(pos.above()).isAir())continue;
            if(server.setBlockAndUpdate(pos,ice))
                server.scheduleTick(pos,net.minecraft.world.level.block.Blocks.FROSTED_ICE,60+random.nextInt(61));
        }
    }
    @Override public InteractionResult interact(Player player,InteractionHand hand){
        if(player.isShiftKeyDown()&&!isVehicle()){
            if(!level().isClientSide()){var item=packedItem();if(!player.addItem(item))player.drop(item,false);discard();}
            return InteractionResult.SUCCESS;
        }
        return super.interact(player,hand);
    }
    @Override protected void destroy(net.minecraft.world.damagesource.DamageSource source){
        if(level().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOENTITYDROPS))spawnAtLocation(packedItem());
        discard();
    }
    public void restoreAerial(int charges,int energy){aerial.restore(charges,energy);syncAerial();}
    @Override protected void addAdditionalSaveData(CompoundTag output){super.addAdditionalSaveData(output);output.putFloat("mc_wuwa_kinetic",kinetic());output.putInt("mc_wuwa_leaps",leapCharges());output.putInt("mc_wuwa_air_energy",glideEnergy());}
    @Override protected void readAdditionalSaveData(CompoundTag input){super.readAdditionalSaveData(input);restoreKinetic((input.contains("mc_wuwa_kinetic",99) ? input.getFloat("mc_wuwa_kinetic") : 1000));restoreAerial((input.contains("mc_wuwa_leaps",99) ? input.getInt("mc_wuwa_leaps") : SurveyMotorcycleRules.LEAP_CAPACITY),(input.contains("mc_wuwa_air_energy",99) ? input.getInt("mc_wuwa_air_energy") : SurveyMotorcycleRules.GLIDE_CAPACITY));entityData.set(BOOST,false);}
    @Override public String modelId(){return "survey_motorcycle";}
    @Override public String animation(){return "idle";}
    @Override public float visualScale(){return 1;}
    @Override public int durationTicks(){return 80;}
    @Override public long startTick(){return tickCount;}
}
