package cn.zgnhit.mcwuwa.client.presentation;

import org.joml.Vector3f;

/** Local front-camera visibility; does not alter attack geometry or timing. */
final class FrontCameraLightFade {
    private FrontCameraLightFade(){}
    static float point(Vector3f camera,float x,float y,float z){
        if(camera==null)return 1;
        float distance=camera.distance(x,y,z);
        float t=Math.clamp((distance-1.35f)/.65f,0,1);
        return t*t*(3-2*t);
    }
    static float segment(Vector3f camera,Vector3f first,Vector3f next){
        if(camera==null)return 1;
        var delta=new Vector3f(next).sub(first);
        float length=delta.lengthSquared();
        float t=length==0?0:Math.clamp(new Vector3f(camera).sub(first).dot(delta)/length,0,1);
        var closest=new Vector3f(first).fma(t,delta);
        return point(camera,closest.x,closest.y,closest.z);
    }
}
