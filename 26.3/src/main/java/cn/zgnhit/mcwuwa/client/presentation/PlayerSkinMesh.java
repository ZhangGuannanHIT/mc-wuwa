package cn.zgnhit.mcwuwa.client.presentation;

import com.google.gson.*;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.*;

/** Project-owned continuous skin surfaces. No Gecko/PAL format is assumed to support weights. */
public final class PlayerSkinMesh {
    private record Vertex(Vector3f point,float u,float v,int sectionId) {}
    private record Surface(String part,boolean outer,List<Vertex> vertices) {}
    private record SectionKey(float x,float y,float z,int a,int b,float weight,float derivative) {}
    private record Section(Vector3f point,int a,int b,float weight,float derivative,int pairId,float radiusX,float radiusZ) {}
    private record BonePair(int a,int b) {}
    private final List<Surface> surfaces;
    private final List<Section> sections;
    private final List<BonePair> bonePairs;
    private final Set<String> rigidBones;
    private final List<String> boneNames;

    private PlayerSkinMesh(List<Surface> surfaces,List<Section> sections,List<BonePair> bonePairs,
                           Set<String> rigidBones,List<String> boneNames) {
        this.surfaces=List.copyOf(surfaces);this.rigidBones=Set.copyOf(rigidBones);this.boneNames=List.copyOf(boneNames);
        this.sections=List.copyOf(sections);this.bonePairs=List.copyOf(bonePairs);
    }
    public boolean replaces(String bone) { return !rigidBones.contains(bone); }
    static PlayerSkinMesh parse(JsonObject json,List<ActionPresentationRegistry.Bone> bones) {
        if(json.get("schemaVersion").getAsInt()!=1||json.get("rigVersion").getAsInt()!=2||json.get("meshVersion").getAsInt()!=2
            ||!json.get("units").getAsString().equals("model_pixels")||!json.get("jointCorrection").getAsString().equals("parent_local_euler_inner_bend"))
            throw new IllegalArgumentException("Unsupported player skin mesh contract");
        List<String> names=bones.stream().map(ActionPresentationRegistry.Bone::name).toList();
        Map<String,Integer> indices=new HashMap<>();for(int i=0;i<names.size();i++)indices.put(names.get(i),i);
        Set<String> rigid=new HashSet<>();for(JsonElement name:json.getAsJsonArray("rigidBones")) {
            if(!indices.containsKey(name.getAsString()))throw new IllegalArgumentException("Unknown rigid bone "+name);
            rigid.add(name.getAsString());
        }
        List<Surface> surfaces=new ArrayList<>();
        List<Section> sections=new ArrayList<>();Map<SectionKey,Integer> sectionIds=new HashMap<>();
        List<Vector3f> sectionExtents=new ArrayList<>();
        List<BonePair> bonePairs=new ArrayList<>();Map<BonePair,Integer> pairIds=new HashMap<>();
        for(JsonElement entry:json.getAsJsonArray("surfaces")) {
            JsonObject object=entry.getAsJsonObject();String part=object.get("part").getAsString();
            if(!indices.containsKey(part))throw new IllegalArgumentException("Unknown skin part "+part);
            List<Vertex> vertices=new ArrayList<>();
            for(JsonElement raw:object.getAsJsonArray("vertices")) {
                JsonObject v=raw.getAsJsonObject();JsonArray influences=v.getAsJsonArray("bones"),weights=v.getAsJsonArray("weights"),uv=v.getAsJsonArray("uv");
                if(influences.size()!=2||weights.size()!=2||uv.size()!=2)throw new IllegalArgumentException("Exactly two influences and one UV required");
                Integer a=indices.get(influences.get(0).getAsString()),b=indices.get(influences.get(1).getAsString());
                if(a==null||b==null)throw new IllegalArgumentException("Unknown skin influence "+influences);
                var first=bones.get(a);var second=bones.get(b);
                if(a!=b&&!Objects.equals(first.parent(),second.name())&&!Objects.equals(second.parent(),first.name()))
                    throw new IllegalArgumentException("Skin influences must be adjacent bones "+influences);
                float wa=weights.get(0).getAsFloat(),wb=weights.get(1).getAsFloat(),u=uv.get(0).getAsFloat()/64f,t=uv.get(1).getAsFloat()/64f;
                JsonArray derivatives=v.getAsJsonArray("weightDerivatives");
                if(derivatives==null||derivatives.size()!=2)throw new IllegalArgumentException("Two weight derivatives required");
                float da=derivatives.get(0).getAsFloat(),db=derivatives.get(1).getAsFloat();
                if(!Float.isFinite(da)||!Float.isFinite(db)||Math.abs(da+db)>1e-5)throw new IllegalArgumentException("Invalid weight derivatives");
                if(!Float.isFinite(wa)||!Float.isFinite(wb)||wa<0||wb<0||Math.abs(wa+wb-1)>1e-5
                    ||!Float.isFinite(u)||!Float.isFinite(t)||u<0||u>1||t<0||t>1)throw new IllegalArgumentException("Invalid skin weight/UV");
                // Canonical parent -> child order. The correction must inherit the parent's
                // complete world transform, including ancestor scale, exactly once.
                if(a!=b&&Objects.equals(first.parent(),second.name())) { int swap=a;a=b;b=swap;wb=wa;db=da; }
                Vector3f point=vector(v.getAsJsonArray("position")),section=vector(v.getAsJsonArray("section"));
                SectionKey key=new SectionKey(section.x,section.y,section.z,a,b,wb,db);
                Integer sectionId=sectionIds.get(key);
                if(sectionId==null) {
                    int pairId=-1;
                    if(a!=b) {
                        BonePair pair=new BonePair(a,b);Integer existing=pairIds.get(pair);
                        if(existing==null) { existing=bonePairs.size();bonePairs.add(pair);pairIds.put(pair,existing); }
                        pairId=existing;
                    }
                    sectionId=sections.size();sections.add(new Section(section,a,b,wb,db,pairId,0,0));sectionIds.put(key,sectionId);
                    sectionExtents.add(new Vector3f());
                }
                Vector3f extent=sectionExtents.get(sectionId);
                extent.x=Math.max(extent.x,Math.abs(point.x-section.x));extent.z=Math.max(extent.z,Math.abs(point.z-section.z));
                vertices.add(new Vertex(point,u,t,sectionId));
            }
            if(vertices.size()!=4)throw new IllegalArgumentException("Skin surface must be a quad");
            Vector3f n1=new Vector3f(vertices.get(1).point).sub(vertices.get(0).point)
                .cross(new Vector3f(vertices.get(2).point).sub(vertices.get(0).point));
            Vector3f n2=new Vector3f(vertices.get(2).point).sub(vertices.get(0).point)
                .cross(new Vector3f(vertices.get(3).point).sub(vertices.get(0).point));
            if(n1.lengthSquared()<1e-12f||n2.lengthSquared()<1e-12f||n1.dot(n2)<=0)
                throw new IllegalArgumentException("Degenerate or folded bind skin surface "+part);
            surfaces.add(new Surface(part,object.get("outer").getAsBoolean(),List.copyOf(vertices)));
        }
        if(surfaces.isEmpty())throw new IllegalArgumentException("Empty player skin mesh");
        for(int i=0;i<sections.size();i++) {
            Section s=sections.get(i);Vector3f extent=sectionExtents.get(i);
            sections.set(i,new Section(s.point,s.a,s.b,s.weight,s.derivative,s.pairId,extent.x,extent.z));
        }
        return new PlayerSkinMesh(surfaces,sections,bonePairs,rigid,names);
    }
    private static Vector3f vector(JsonArray array) {
        if(array.size()!=3)throw new IllegalArgumentException("Skin point needs three coordinates");
        var result=new Vector3f(array.get(0).getAsFloat(),array.get(1).getAsFloat(),array.get(2).getAsFloat());
        if(!result.isFinite())throw new IllegalArgumentException("Non-finite skin vertex");return result;
    }
    /** Detached, immutable submitted array; points are converted once to block units. */
    public static final class Prepared {
        private final float[] vertices;
        private Prepared(float[] vertices) { this.vertices=vertices; }
        public int vertexCount() { return vertices.length/8; }
        float[] data() { return vertices; }
        float[] copyData() { return vertices.clone(); }
    }
    Prepared prepare(Map<String,Matrix4f> matrices,Map<String,ActionPresentationRegistry.Pose> localPoses,
                     AvatarRenderState flags,boolean firstPerson) {
        Matrix4f[] transforms=new Matrix4f[boneNames.size()];
        ActionPresentationRegistry.Pose[] locals=new ActionPresentationRegistry.Pose[boneNames.size()];
        for(int i=0;i<transforms.length;i++) {
            transforms[i]=Objects.requireNonNull(matrices.get(boneNames.get(i)),"Missing skin bone "+boneNames.get(i));
            locals[i]=Objects.requireNonNull(localPoses.get(boneNames.get(i)),"Missing local skin transform "+boneNames.get(i));
        }
        Matrix4f[] relative=new Matrix4f[bonePairs.size()];
        for(int i=0;i<relative.length;i++) {
            BonePair pair=bonePairs.get(i);
            relative[i]=new Matrix4f(transforms[pair.a]).invert().mul(transforms[pair.b]);
        }
        // Section ids are immutable resource data. All evaluated transforms belong to this
        // preparation only; no later frame can change already-submitted vertex snapshots.
        SectionFrame[] sectionFrames=new SectionFrame[sections.size()];
        int count=0;for(Surface s:surfaces)if(visible(s,flags,firstPerson))count+=32;
        float[] output=new float[count];int write=0;
        Vector3f[] points={new Vector3f(),new Vector3f(),new Vector3f(),new Vector3f()};
        Vector3f radial=new Vector3f(),normal=new Vector3f(),edge=new Vector3f();
        for(Surface surface:surfaces)if(visible(surface,flags,firstPerson)) {
            for(int j=0;j<4;j++) {
                Vertex v=surface.vertices.get(j);
                Section section=sections.get(v.sectionId);
                SectionFrame frame=sectionFrames[v.sectionId];
                if(frame==null) {
                    frame=prepareSection(section,transforms,locals,relative);sectionFrames[v.sectionId]=frame;
                }
                frame.transform(v.point,section.point,radial,points[j]);
            }
            normal.set(points[1]).sub(points[0]);edge.set(points[2]).sub(points[0]);normal.cross(edge);
            if(normal.lengthSquared()<1e-16f||!normal.isFinite())throw new IllegalArgumentException("Degenerate player skin surface "+surface.part);
            normal.normalize();
            for(int j=0;j<4;j++) {
                var v=surface.vertices.get(j);var p=points[j];
                output[write++]=p.x;output[write++]=p.y;output[write++]=p.z;output[write++]=v.u;output[write++]=v.v;
                output[write++]=normal.x;output[write++]=normal.y;output[write++]=normal.z;
            }
        }
        return new Prepared(output);
    }

    /** Trigonometry is shared by a section, with the same per-axis float operations as JOML. */
    private record SectionRotation(float sx,float cx,float sy,float cy,float sz,float cz) {
        static SectionRotation of(float x,float y,float z) {
            float sx=org.joml.Math.sin(x),sy=org.joml.Math.sin(y),sz=org.joml.Math.sin(z);
            return new SectionRotation(sx,org.joml.Math.cosFromSin(sx,x),sy,org.joml.Math.cosFromSin(sy,y),sz,org.joml.Math.cosFromSin(sz,z));
        }
        void apply(Vector3f v) {
            float y=v.y*cx-v.z*sx,z=v.y*sx+v.z*cx;v.set(v.x,y,z);
            float x=v.x*cy+v.z*sy;z=-v.x*sy+v.z*cy;v.set(x,v.y,z);
            x=v.x*cz-v.y*sz;y=v.x*sz+v.y*cz;v.set(x,y,v.z);
        }
    }
    private record SectionFrame(Matrix4f transform,Vector3f center,Vector3f scale,SectionRotation rotation,
                                float omegaX,float omegaZ,float denominator,float centerShift,float bendScale,float bendOffset) {
        void transform(Vector3f point,Vector3f section,Vector3f radial,Vector3f output) {
            if(rotation==null) { transform.transformPosition(point,output).div(16);return; }
            radial.set(point).sub(section).mul(scale.x,scale.y,scale.z);
            if(denominator>1e-12f) {
                float b=omegaZ*radial.x-omegaX*radial.z;
                if(bendScale<1) {
                    float correction=((bendScale-1)*b+bendOffset)/denominator;
                    radial.add(correction*omegaZ,0,-correction*omegaX);
                }
                radial.add(centerShift*omegaZ,0,-centerShift*omegaX);
            }
            rotation.apply(radial);transform.transformDirection(radial);
            output.set(center).add(radial).div(16);
        }
    }
    private static SectionFrame prepareSection(Section section,Matrix4f[] transforms,
                                               ActionPresentationRegistry.Pose[] locals,Matrix4f[] relative) {
        if(section.a==section.b||section.weight==0||section.weight==1)
            return new SectionFrame(transforms[section.weight==1?section.b:section.a],null,null,null,0,0,0,0,1,0);
        Vector3f center=transforms[section.a].transformPosition(section.point,new Vector3f());
        Vector3f end=transforms[section.b].transformPosition(section.point,new Vector3f());
        center.lerp(end,section.weight);
        // Follow the continuous authored local Euler path; a world-quaternion shortest slerp
        // jumps when a shoulder crosses 180 degrees. Ancestor scale remains in the parent.
        var local=locals[section.b];float w=section.weight,radians=(float)(Math.PI/180)*w;
        float rx=local.rotation().x*radians,ry=local.rotation().y*radians,rz=local.rotation().z*radians;
        Vector3f scale=new Vector3f(1+(local.scale().x-1)*w,1+(local.scale().y-1)*w,1+(local.scale().z-1)*w);
        float omegaX=0,omegaZ=0,denominator=0,centerShift=0,bendScale=1,bendOffset=0;
        if(section.derivative!=0&&local.scale().equals(1,1,1)) {
            Matrix4f child=relative[section.pairId];
            Vector3f tangent=child.transformDirection(0,1,0,new Vector3f()).mul(w).add(0,1-w,0);
            Vector3f delta=child.transformPosition(section.point,new Vector3f()).sub(section.point).mul(section.derivative);tangent.add(delta);
            tangent.rotateZ(-rz).rotateY(-ry).rotateX(-rx);
            float derivativeRadians=(float)(Math.PI/180)*section.derivative;
            Vector3f omega=new Vector3f(local.rotation().x*derivativeRadians,0,0);
            Vector3f angularPart=new Vector3f(0,local.rotation().y*derivativeRadians,0).rotateX(-rx);omega.add(angularPart);
            angularPart.set(0,0,local.rotation().z*derivativeRadians).rotateY(-ry).rotateX(-rx);omega.add(angularPart);
            omegaX=omega.x;omegaZ=omega.z;denominator=omega.z*omega.z+omega.x*omega.x;
            if(denominator>1e-12f) {
                // Keep an entire section affine: sharp nonlinear radial compression creates
                // twisted GPU quads even when its continuous Jacobian stays positive. Map the
                // complete [-B,B] section range to [-h,B], preserving the outermost boundary
                // and at least half its width. Extents include hidden skin layers as well.
                centerShift=Math.max(0,.25f-tangent.y)/denominator;
                float innerLimit=.5f*(tangent.y+centerShift*denominator);
                float bound=Math.abs(omegaZ)*section.radiusX+Math.abs(omegaX)*section.radiusZ;
                if(bound>innerLimit) {
                    bendScale=(bound+innerLimit)/(2*bound);bendOffset=(bound-innerLimit)/2;
                }
            }
        }
        return new SectionFrame(transforms[section.a],center,scale,SectionRotation.of(rx,ry,rz),
            omegaX,omegaZ,denominator,centerShift,bendScale,bendOffset);
    }
    private static boolean visible(Surface s,AvatarRenderState flags,boolean firstPerson) {
        if(firstPerson&&!s.part.endsWith("_arm"))return false;
        if(!s.outer||flags==null)return true;
        return switch(s.part) {
            case "torso"->flags.showJacket;
            case "left_arm"->flags.showLeftSleeve;
            case "right_arm"->flags.showRightSleeve;
            case "left_leg"->flags.showLeftPants;
            case "right_leg"->flags.showRightPants;
            default->false;
        };
    }
}
