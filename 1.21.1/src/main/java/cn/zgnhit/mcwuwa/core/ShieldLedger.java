package cn.zgnhit.mcwuwa.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** MC coexistence policy: independent sources/lifetimes, maximum protection,
 * and simultaneous consumption. Hidden shields are not an extra HP pool. */
public final class ShieldLedger {
    private record Key(UUID source,UUID recipient,String effect){}
    private record Shield(double amount,long expires,boolean endsOnSwitch){}
    private final Map<Key,Shield> entries=new HashMap<>();

    public void grant(UUID source,UUID recipient,String effect,double amount,long expires,boolean endsOnSwitch,long now){
        Checks.nonnegative("shield",amount);
        if(amount==0||expires<=now)return;
        entries.put(new Key(source,recipient,effect),new Shield(amount,expires,endsOnSwitch));
    }
    public double effective(UUID recipient,long now){
        double maximum=0;
        for(var entry:entries.entrySet())if(entry.getKey().recipient().equals(recipient)&&entry.getValue().expires()>now)
            maximum=Math.max(maximum,entry.getValue().amount());
        return maximum;
    }
    /** Returns damage left for absorption hearts/HP. Each concurrent shield
     * sees the same incoming amount, so capacities never add together. */
    public double absorb(UUID recipient,double damage,long now){
        Checks.nonnegative("shield incoming damage",damage);prune(now);
        double remaining=Math.max(0,damage-effective(recipient,now));
        for(var iterator=entries.entrySet().iterator();iterator.hasNext();){
            var entry=iterator.next();if(!entry.getKey().recipient().equals(recipient))continue;
            Shield old=entry.getValue();double amount=Math.max(0,old.amount()-damage);
            if(amount==0)iterator.remove();else entry.setValue(new Shield(amount,old.expires(),old.endsOnSwitch()));
        }
        return remaining;
    }
    public void onSwitch(UUID recipient){entries.entrySet().removeIf(e->e.getKey().recipient().equals(recipient)&&e.getValue().endsOnSwitch());}
    public void removeRecipient(UUID recipient){entries.keySet().removeIf(key->key.recipient().equals(recipient));}
    public void prune(long now){entries.values().removeIf(shield->shield.expires()<=now||shield.amount()<=0);}
    public void clear(){entries.clear();}
    public void pauseOneTick(){entries.replaceAll((key,s)->new Shield(s.amount(),CinematicTimeRules.postpone(s.expires(),1),s.endsOnSwitch()));}
}
