package cn.zgnhit.mcwuwa.client.presentation;

import org.joml.Vector3f;

/** Width axes for a nonzero light segment. Callers discard zero-length segments. */
final class LightRibbonBasis {
    record Basis(Vector3f across,Vector3f crossed){}
    private LightRibbonBasis(){}
    /** Share a width frame at each polyline vertex so adjacent strips meet.
     * Closed rings use the same tangent at both copies of their seam vertex. */
    static Basis[] along(java.util.List<Vector3f> points){
        var result=new Basis[points.size()];
        if(points.size()<2)return result;
        int last=points.size()-1;
        boolean closed=last>2&&points.get(0).distanceSquared(points.get(last))<.000001f;
        for(int i=0;i<=last;i++){
            int before=i==0?(closed?last-1:0):i-1;
            int after=i==last?(closed?1:last):i+1;
            var tangent=new Vector3f(points.get(after)).sub(points.get(before));
            if(tangent.lengthSquared()<.000001f){
                tangent.set(points.get(i)).sub(points.get(before));
                if(tangent.lengthSquared()<.000001f)tangent.set(0,0,1);
            }
            result[i]=of(tangent);
            // The world-up construction changes sign when a curve passes a
            // vertical tangent. Keep the two edges on their previous sides
            // rather than twisting the connecting quad through its centre.
            if(i>0&&result[i-1].across().dot(result[i].across())<0){
                result[i].across().negate();
                result[i].crossed().negate();
            }
        }
        return result;
    }
    static Basis of(Vector3f direction){
        var unit=new Vector3f(direction).normalize();
        var across=new Vector3f(-unit.z,0,unit.x);
        if(across.lengthSquared()<.000001f)across.set(1,0,0);else across.normalize();
        return new Basis(across,new Vector3f(unit).cross(across).normalize());
    }
}
