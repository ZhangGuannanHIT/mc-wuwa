package cn.zgnhit.mcwuwa.client.presentation;

import org.joml.Vector3f;

/** Inverse of ConstructRenderer's root yaw/scale, for a server-owned fixed contact. */
final class FixedLightRay {
    private FixedLightRay(){}
    static Vector3f local(float x,float y,float z,float yaw,float scale){
        return new Vector3f(x,y,z).rotateY((float)Math.toRadians(yaw-180)).div(scale);
    }
}
