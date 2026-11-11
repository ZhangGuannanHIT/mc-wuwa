package cn.zgnhit.mcwuwa.client.presentation;

import com.mojang.blaze3d.vertex.PoseStack;
import cn.zgnhit.mcwuwa.client.presentation.GeometryBuffers;
import cn.zgnhit.mcwuwa.client.presentation.AvatarPoseState;
import cn.zgnhit.mcwuwa.client.presentation.HeldItemDraw;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.*;

/** Pixel-space skeleton -> private submitted snapshots; it never moves an entity. */
public final class SegmentedMeshRenderer {
    private static final String[] FACES = {"north", "south", "west", "east", "down", "up"};
    private static final int[][] CORNERS = {{1,0,2,3},{4,5,7,6},{0,4,6,2},{5,1,3,7},{0,1,5,4},{3,2,6,7}};
    private static final float[][] NORMALS = {{0,0,-1},{0,0,1},{-1,0,0},{1,0,0},{0,-1,0},{0,1,0}};
    private static final float RADIANS = (float)(Math.PI/180);
    // One cube stores eight transformed positions and six transformed normals, shared by its quads.
    private static final int CUBE_FLOATS = 8*3 + 6*3;
    private static final ReferenceQueue<ActionPresentationRegistry.Mesh> STALE_MESHES = new ReferenceQueue<>();
    private static final Map<MeshKey, Geometry> GEOMETRY = new HashMap<>();

    /** Identity keys avoid walking the whole record/cube tree for Mesh.hashCode every frame. */
    private static final class MeshKey extends WeakReference<ActionPresentationRegistry.Mesh> {
        private final int hash;
        MeshKey(ActionPresentationRegistry.Mesh mesh, ReferenceQueue<ActionPresentationRegistry.Mesh> queue) {
            super(mesh, queue); hash = System.identityHashCode(mesh);
        }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof MeshKey key && get() != null && get() == key.get();
        }
    }
    // All cached arrays are privately owned. No Geometry value retains its weak Mesh key or source records.
    private record CubeGeometry(float[] positions, float[] normals, float[] uv, int faces, int faceCount) {}
    private record BoneGeometry(String name, String mirroredName, int parent, float px, float py, float pz,
                                float rx, float ry, float rz, int cubeStart, int cubeEnd, boolean firstPersonArm) {}
    private record SocketGeometry(String name,int bone,float x,float y,float z,
                                  float tpx,float tpy,float tpz,float fpx,float fpy,float fpz) {}
    private record Geometry(BoneGeometry[] bones, CubeGeometry[] cubes,PlayerSkinMesh skin,SocketGeometry[] sockets) {}

    /** Arrays and JOML matrices never escape; submitted frames are not reused by later preparations. */
    public static final class Prepared {
        private final Geometry geometry;
        private final float[] transformed;
        private final boolean[] visible;
        private final Matrix4f[] matrices;
        private final int vertexCount;
        private final PlayerSkinMesh.Prepared skin;
        private final Map<String,Matrix4f> itemSockets;
        private final ActionPresentationRegistry.WeaponSockets weaponSockets;
        private Prepared(Geometry geometry, float[] transformed, boolean[] visible, Matrix4f[] matrices, int vertexCount,PlayerSkinMesh.Prepared skin,
                         Map<String,Matrix4f> itemSockets,ActionPresentationRegistry.WeaponSockets weaponSockets) {
            this.geometry=geometry; this.transformed=transformed; this.visible=visible; this.matrices=matrices; this.vertexCount=vertexCount;this.skin=skin;
            this.itemSockets=itemSockets;this.weaponSockets=weaponSockets;
        }
        public int vertexCount() { return vertexCount; }
        /** Reuse a construct's real posed subtree (e.g. dragon head) without drawing a full
         * world-size body across the combat camera. Bone transforms and sockets are preserved. */
        public Prepared onlySubtree(String name) {
            if(skin!=null)throw new IllegalArgumentException("Construct subtree cannot hide a player skin");
            int root=-1;for(int i=0;i<geometry.bones.length;i++)if(geometry.bones[i].name.equals(name)){root=i;break;}
            if(root<0)throw new IllegalArgumentException("Unknown model subtree: "+name);
            boolean[] selected=visible.clone();int count=0;
            for(int boneIndex=0;boneIndex<geometry.bones.length;boneIndex++) {
                int ancestor=boneIndex;while(ancestor>=0&&ancestor!=root)ancestor=geometry.bones[ancestor].parent;
                var bone=geometry.bones[boneIndex];
                for(int cube=bone.cubeStart;cube<bone.cubeEnd;cube++) {
                    selected[cube]&=ancestor==root;if(selected[cube])count+=geometry.cubes[cube].faceCount*4;
                }
            }
            return new Prepared(geometry,transformed,selected,matrices,count,null,itemSockets,weaponSockets);
        }
        /** Authored pixel point transformed by this same frame's real bone hierarchy. */
        public Optional<Vector3f> bonePoint(String name,float x,float y,float z) {
            for(int i=0;i<geometry.bones.length;i++)if(geometry.bones[i].name.equals(name))
                return Optional.of(matrices[i].transformPosition(new Vector3f(x,y,z)).div(16));
            return Optional.empty();
        }
        public Optional<Matrix4f> boneTransform(String name) {
            for(int i=0;i<geometry.bones.length;i++)if(geometry.bones[i].name.equals(name))return Optional.of(new Matrix4f(matrices[i]));
            return Optional.empty();
        }
        float[] copySkinData() { return skin==null?new float[0]:skin.copyData(); }
        // Inspection is intentionally a copy, and is never used by the per-frame rendering path.
        float[] copyVertexData() {
            float[] result = new float[vertexCount*8]; int write=0;
            for (int index=0; index<geometry.cubes.length; index++) if (visible[index]) {
                CubeGeometry cube=geometry.cubes[index]; int base=index*CUBE_FLOATS;
                for (int face=0; face<6; face++) if ((cube.faces & (1<<face)) != 0) {
                    int normal=base+24+face*3;
                    for (int corner=0; corner<4; corner++) {
                        int point=base+CORNERS[face][corner]*3, uv=face*8+corner*2;
                        result[write++]=transformed[point]; result[write++]=transformed[point+1]; result[write++]=transformed[point+2];
                        result[write++]=cube.uv[uv]; result[write++]=cube.uv[uv+1];
                        result[write++]=transformed[normal]; result[write++]=transformed[normal+1]; result[write++]=transformed[normal+2];
                    }
                }
            }
            if(skin!=null)System.arraycopy(skin.data(),0,result,write,skin.data().length);
            return result;
        }
        Map<String, Matrix4f> copyMatrices() {
            Map<String, Matrix4f> result=new HashMap<>();
            for(int i=0;i<matrices.length;i++) result.put(geometry.bones[i].name,new Matrix4f(matrices[i]));
            return Map.copyOf(result);
        }
        /** Same-frame item submission basis, before Minecraft applies the item's display transform. */
        public Optional<Matrix4f> itemSocket(String name) {
            Matrix4f matrix=itemSockets.get(name);
            return matrix==null?Optional.empty():Optional.of(new Matrix4f(matrix));
        }
        public Optional<Matrix4f> heldItemSocket(HumanoidArm arm) {
            return itemSocket(arm==HumanoidArm.RIGHT?weaponSockets.primary():weaponSockets.leftHandedPrimary());
        }
    }
    private SegmentedMeshRenderer() {}

    public static Prepared prepare(ActionPresentationRegistry.Mesh mesh, Map<String, ActionPresentationRegistry.Pose> pose,
                                   AvatarPoseState skinFlags, boolean firstPerson, boolean mirrored) {
        return prepare(mesh,pose,skinFlags,firstPerson,mirrored,null);
    }
    public static Prepared prepare(ActionPresentationRegistry.Mesh mesh, Map<String, ActionPresentationRegistry.Pose> pose,
                                   AvatarPoseState skinFlags, boolean firstPerson, boolean mirrored,ActionPresentationRegistry.Binding binding) {
        var weaponSockets=binding==null?ActionPresentationRegistry.WeaponSockets.canonical():binding.weaponSockets();
        if(binding!=null)ActionPresentationRegistry.validateWeaponSockets(weaponSockets,mesh);
        Geometry geometry=geometry(mesh);
        Matrix4f[] matrices=new Matrix4f[geometry.bones.length];
        float[] transformed=new float[geometry.cubes.length*CUBE_FLOATS];
        boolean[] visible=new boolean[geometry.cubes.length];
        Map<String,ActionPresentationRegistry.Pose> skinLocals=geometry.skin==null?Map.of():new HashMap<>();
        Vector3f scratch=new Vector3f(); int vertexCount=0;
        for(int boneIndex=0;boneIndex<geometry.bones.length;boneIndex++) {
            BoneGeometry bone=geometry.bones[boneIndex];
            var transform=pose.get(mirrored?bone.mirroredName:bone.name);
            float px=transform==null?0:transform.position().x, py=transform==null?0:transform.position().y, pz=transform==null?0:transform.position().z;
            float rx=(transform==null?0:transform.rotation().x)+bone.rx;
            float ry=(transform==null?0:transform.rotation().y)+bone.ry;
            float rz=(transform==null?0:transform.rotation().z)+bone.rz;
            if(mirrored) { px=-px; ry=-ry; rz=-rz; }
            if(geometry.skin!=null)skinLocals.put(bone.name,new ActionPresentationRegistry.Pose(new Vector3f(px,py,pz),
                new Vector3f(rx,ry,rz),transform==null?new Vector3f(1):new Vector3f(transform.scale())));
            Matrix4f matrix=bone.parent<0?new Matrix4f():new Matrix4f(matrices[bone.parent]);
            matrix.translate(px,py,pz).translate(bone.px,bone.py,bone.pz).rotateZYX(rz*RADIANS,ry*RADIANS,rx*RADIANS);
            if(transform==null) matrix.scale(1,1,1); else matrix.scale(transform.scale());
            matrix.translate(-bone.px,-bone.py,-bone.pz); matrices[boneIndex]=matrix;
            if(firstPerson&&!bone.firstPersonArm) continue;
            if(geometry.skin!=null&&geometry.skin.replaces(bone.name))continue;
            boolean outerVisible=skinFlags==null||outerLayerVisible(bone.name,skinFlags);
            for(int index=bone.cubeStart;index<bone.cubeEnd;index++) {
                if(index>bone.cubeStart&&!outerVisible) continue;
                CubeGeometry cube=geometry.cubes[index]; int base=index*CUBE_FLOATS;
                visible[index]=true; vertexCount+=cube.faceCount*4;
                for(int point=0;point<24;point+=3) {
                    matrix.transformPosition(cube.positions[point],cube.positions[point+1],cube.positions[point+2],scratch).div(16);
                    transformed[base+point]=scratch.x; transformed[base+point+1]=scratch.y; transformed[base+point+2]=scratch.z;
                }
                for(int face=0;face<6;face++) if((cube.faces&(1<<face))!=0) {
                    int normal=face*3,write=base+24+normal;
                    matrix.transformDirection(cube.normals[normal],cube.normals[normal+1],cube.normals[normal+2],scratch).normalize();
                    transformed[write]=scratch.x; transformed[write+1]=scratch.y; transformed[write+2]=scratch.z;
                }
            }
        }
        PlayerSkinMesh.Prepared skin=geometry.skin==null?null:geometry.skin.prepare(matrixMap(geometry,matrices),skinLocals,skinFlags,firstPerson);
        if(skin!=null)vertexCount+=skin.vertexCount();
        Map<String,Matrix4f> sockets=new HashMap<>();
        for(SocketGeometry socket:geometry.sockets) {
            // Bone matrices transform absolute bind coordinates around their pivots.
            // Do not subtract the pivot again or reinterpret the matrix translation as the anchor.
            Matrix4f transform=new Matrix4f().scaling(1f/16).mul(matrices[socket.bone])
                .translate(socket.x,socket.y,socket.z).scale(16)
                .rotateX(firstPerson?socket.fpx:socket.tpx)
                .rotateY(firstPerson?socket.fpy:socket.tpy)
                .rotateZ(firstPerson?socket.fpz:socket.tpz);
            sockets.put(socket.name,transform);
        }
        return new Prepared(geometry,transformed,visible,matrices,vertexCount,skin,Map.copyOf(sockets),weaponSockets);
    }
    private static Map<String,Matrix4f> matrixMap(Geometry geometry,Matrix4f[] matrices) {
        Map<String,Matrix4f> result=new HashMap<>();
        for(int i=0;i<geometry.bones.length;i++)result.put(geometry.bones[i].name,matrices[i]);return result;
    }

    private static synchronized Geometry geometry(ActionPresentationRegistry.Mesh mesh) {
        MeshKey stale;
        while((stale=(MeshKey)STALE_MESHES.poll())!=null) GEOMETRY.remove(stale);
        MeshKey lookup=new MeshKey(mesh,null); Geometry cached=GEOMETRY.get(lookup);
        if(cached!=null) return cached;
        Geometry compiled=compile(mesh); GEOMETRY.put(new MeshKey(mesh,STALE_MESHES),compiled); return compiled;
    }
    private static Geometry compile(ActionPresentationRegistry.Mesh mesh) {
        List<BoneGeometry> bones=new ArrayList<>(); List<CubeGeometry> cubes=new ArrayList<>();
        Map<String,Integer> parents=new HashMap<>();
        for(var bone:ActionPresentationRegistry.orderedBones(mesh.bones())) {
            int cubeStart=cubes.size();
            for(var cube:bone.cubes()) {
                Matrix4f local=new Matrix4f().translate(cube.pivot());
                local.rotateZYX(cube.rotation().z*RADIANS,cube.rotation().y*RADIANS,cube.rotation().x*RADIANS);
                local.translate(-cube.pivot().x,-cube.pivot().y,-cube.pivot().z);
                float[] positions=new float[24], normals=new float[18], uv=new float[48]; Vector3f point=new Vector3f();
                for(int z=0;z<2;z++) for(int y=0;y<2;y++) for(int x=0;x<2;x++) {
                    if(cube.authoredVertices().isEmpty())point.set(cube.origin()).add(x*cube.size().x+(x==0?-cube.inflate():cube.inflate()),
                        y*cube.size().y+(y==0?-cube.inflate():cube.inflate()),z*cube.size().z+(z==0?-cube.inflate():cube.inflate()));
                    else point.set(cube.authoredVertices().get(z*4+y*2+x));
                    local.transformPosition(point); int offset=(z*4+y*2+x)*3;
                    positions[offset]=point.x;positions[offset+1]=point.y;positions[offset+2]=point.z;
                }
                int faces=0,faceCount=0;
                for(int face=0;face<6;face++) {
                    var source=cube.faces().get(FACES[face]); if(source==null) continue;
                    faces|=1<<face;faceCount++;
                    if(cube.authoredVertices().isEmpty())local.transformDirection(NORMALS[face][0],NORMALS[face][1],NORMALS[face][2],point);
                    else {
                        int a=CORNERS[face][0]*3,b=CORNERS[face][1]*3,c=CORNERS[face][3]*3;
                        point.set(positions[b]-positions[a],positions[b+1]-positions[a+1],positions[b+2]-positions[a+2]);
                        point.cross(positions[c]-positions[a],positions[c+1]-positions[a+1],positions[c+2]-positions[a+2]);
                        if(point.lengthSquared()>1e-10f)point.normalize();else point.set(0,1,0);
                    }
                    normals[face*3]=point.x;normals[face*3+1]=point.y;normals[face*3+2]=point.z;
                    for(int corner=0;corner<4;corner++) {
                        uv[face*8+corner*2]=(source.u()+((corner==0||corner==3)?0:source.width()))/mesh.textureWidth();
                        uv[face*8+corner*2+1]=(source.v()+(corner<2?source.height():0))/mesh.textureHeight();
                    }
                }
                cubes.add(new CubeGeometry(positions,normals,uv,faces,faceCount));
            }
            int parent=bone.parent()==null||bone.parent().isEmpty()?-1:parents.get(bone.parent());
            // A transformed claw, palm or held mesh belongs to the arm subtree
            // even when its own name does not contain "arm". Cache the decision
            // with geometry; do not walk the skeleton again for every frame.
            boolean firstPersonArm=bone.name().contains("arm")||(parent>=0&&bones.get(parent).firstPersonArm);
            bones.add(new BoneGeometry(bone.name(),swap(bone.name()),parent,
                bone.pivot().x,bone.pivot().y,bone.pivot().z,bone.rotation().x,bone.rotation().y,bone.rotation().z,cubeStart,cubes.size(),firstPersonArm));
            parents.put(bone.name(),bones.size()-1);
        }
        List<SocketGeometry> sockets=new ArrayList<>();
        mesh.itemSockets().forEach((name,socket)-> {
            Integer bone=parents.get(socket.bone());
            if(bone==null)throw new IllegalArgumentException("Missing compiled socket bone "+socket.bone());
            var tp=socket.thirdPersonRotation();var fp=socket.firstPersonRotation();
            sockets.add(new SocketGeometry(name,bone,socket.x(),socket.y(),socket.z(),
                tp.x()*RADIANS,tp.y()*RADIANS,tp.z()*RADIANS,fp.x()*RADIANS,fp.y()*RADIANS,fp.z()*RADIANS));
        });
        return new Geometry(bones.toArray(BoneGeometry[]::new),cubes.toArray(CubeGeometry[]::new),mesh.skin(),sockets.toArray(SocketGeometry[]::new));
    }

    public static void submit(Prepared prepared, ResourceLocation texture, PoseStack poses, GeometryBuffers collector, int light) {
        submit(prepared,texture,poses,collector,light,false);
    }
    public static void submit(Prepared prepared, ResourceLocation texture, PoseStack poses, GeometryBuffers collector, int light,boolean spectral) {
        submit(prepared,texture,poses,collector,light,spectral?0xa087ffe4:0xffffffff);
    }
    /** Local visual tint/opacity; does not alter the tracked model or its hit logic. */
    public static void submit(Prepared prepared, ResourceLocation texture, PoseStack poses, GeometryBuffers collector, int light,int tint) {
        int alpha=tint>>>24,red=tint>>>16&255,green=tint>>>8&255,blue=tint&255;
        collector.submitCustomGeometry(poses,alpha<255?RenderType.entityTranslucent(texture):RenderType.entityCutout(texture),(stack,consumer)-> {
            float[] data=prepared.transformed;
            for(int index=0;index<prepared.geometry.cubes.length;index++) if(prepared.visible[index]) {
                CubeGeometry cube=prepared.geometry.cubes[index];int base=index*CUBE_FLOATS;
                for(int face=0;face<6;face++) if((cube.faces&(1<<face))!=0) {
                    int normal=base+24+face*3;
                    for(int corner=0;corner<4;corner++) {
                        int point=base+CORNERS[face][corner]*3,uv=face*8+corner*2;
                        consumer.addVertex(stack,data[point],data[point+1],data[point+2]).setColor(red,green,blue,alpha).setUv(cube.uv[uv],cube.uv[uv+1])
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(stack,data[normal],data[normal+1],data[normal+2]);
                    }
                }
            }
            if(prepared.skin!=null) {
                float[] skin=prepared.skin.data();
                for(int i=0;i<skin.length;i+=8)consumer.addVertex(stack,skin[i],skin[i+1],skin[i+2]).setColor(red,green,blue,alpha)
                    .setUv(skin[i+3],skin[i+4]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                    .setNormal(stack,skin[i+5],skin[i+6],skin[i+7]);
            }
        });
    }

    public static void submitHeldItem(Prepared prepared, HeldItemDraw item, HumanoidArm arm,
                                      PoseStack poses, GeometryBuffers collector, int light) {
        submitHeldItem(prepared,item,arm,poses,collector,light,null);
    }
    public static void submitHeldItem(Prepared prepared, HeldItemDraw item, HumanoidArm arm,
                                      PoseStack poses, GeometryBuffers collector, int light,Matrix4f viewCorrection) {
        if(item.isEmpty()) return;
        Matrix4f transform=prepared.itemSockets.get(arm==HumanoidArm.RIGHT?prepared.weaponSockets.primary():prepared.weaponSockets.leftHandedPrimary());
        if(transform==null) return;
        poses.pushPose();
        poses.mulPose(transform);if(viewCorrection!=null)poses.mulPose(viewCorrection);
        item.submit(poses,collector,light,OverlayTexture.NO_OVERLAY,0); poses.popPose();
    }
    private static String swap(String name) {
        if(name.startsWith("right_")) return "left_"+name.substring(6);
        if(name.startsWith("left_")) return "right_"+name.substring(5);
        return name;
    }
    private static boolean outerLayerVisible(String bone,AvatarPoseState state) {
        if(bone.equals("head")) return state.showHat;
        if(bone.equals("torso")) return state.showJacket;
        if(bone.equals("left_arm")||bone.equals("left_forearm")) return state.showLeftSleeve;
        if(bone.equals("right_arm")||bone.equals("right_forearm")) return state.showRightSleeve;
        if(bone.equals("left_leg")||bone.equals("left_shin")) return state.showLeftPants;
        if(bone.equals("right_leg")||bone.equals("right_shin")) return state.showRightPants;
        return true;
    }
}
