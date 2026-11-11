package cn.zgnhit.mcwuwa.client.presentation;

import cn.zgnhit.mcwuwa.McWuwa;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Vector3f;
import org.joml.Matrix4f;

import java.io.Reader;
import java.util.*;

/** Compiles bundled curves and cube models once per resource reload. */
public final class ActionPresentationRegistry {
    public record Pose(Vector3f position, Vector3f rotation, Vector3f scale) {
        public static Pose identity() { return new Pose(new Vector3f(), new Vector3f(), new Vector3f(1)); }
    }
    public record Frame(double time, Vector3f value) {}
    public record Curve(List<Frame> values) {
        public Vector3f sample(double seconds) {
            if (values.isEmpty()) return new Vector3f();
            Frame previous = values.getFirst();
            for (Frame current : values) {
                if (seconds <= current.time()) {
                    double span = current.time() - previous.time();
                    float mix = span <= 0 ? 0 : (float) Math.clamp((seconds - previous.time()) / span, 0, 1);
                    return new Vector3f(previous.value()).lerp(current.value(), mix);
                }
                previous = current;
            }
            return new Vector3f(previous.value());
        }
    }
    public record BoneCurves(Curve position, Curve rotation, Curve scale) {
        public Pose sample(double time) {
            return new Pose(position.sample(time), rotation.sample(time), scale.sample(time));
        }
    }
    public record Clip(double length, Map<String, BoneCurves> bones) {
        public Map<String, Pose> sample(float progress) {
            Map<String, Pose> result = new HashMap<>();
            bones.forEach((name, curves) -> result.put(name, curves.sample(length * progress)));
            return Map.copyOf(result);
        }
    }
    public record Uv(float u, float v, float width, float height) {}
    public record Cube(Vector3f origin, Vector3f size, Vector3f pivot, Vector3f rotation,
                       float inflate, Map<String, Uv> faces,List<Vector3f> authoredVertices) {
        public Cube { authoredVertices=authoredVertices.stream().map(Vector3f::new).toList();
            if(!authoredVertices.isEmpty()&&(authoredVertices.size()!=8||authoredVertices.stream().anyMatch(v->!v.isFinite())))throw new IllegalArgumentException("Authored surface requires eight finite bind vertices"); }
        public Cube(Vector3f origin,Vector3f size,Vector3f pivot,Vector3f rotation,float inflate,Map<String,Uv> faces){this(origin,size,pivot,rotation,inflate,faces,List.of());}
    }
    public record Bone(String name, String parent, Vector3f pivot, Vector3f rotation, List<Cube> cubes) {}
    /** Primitive components keep author data immutable; these rotations right-multiply Rx, Ry, Rz. */
    public record SocketRotation(float x, float y, float z) {}
    public record ItemSocket(String bone, String physicalArm, float x, float y, float z,
                             SocketRotation thirdPersonRotation, SocketRotation firstPersonRotation) {}
    public record WeaponSockets(String primary, String leftHandedPrimary) {
        public static WeaponSockets canonical() { return new WeaponSockets("right_item", "left_item"); }
    }
    /** Model anchor is in pixels; camera anchor is in blocks. Owns no camera or gameplay state. */
    public record FormView(float cameraX,float cameraY,float cameraZ,float modelX,float modelY,float modelZ,float scale) {
        public FormView {
            for(float value:new float[]{cameraX,cameraY,cameraZ,modelX,modelY,modelZ,scale})
                if(!Float.isFinite(value))throw new IllegalArgumentException("Nonfinite form view");
            if(scale<=0||cameraZ>=-.05f)throw new IllegalArgumentException("Invalid form view scale/depth");
        }
        public Matrix4f modelToView(){return new Matrix4f().translate(cameraX,cameraY,cameraZ).scale(scale).translate(-modelX/16,-modelY/16,-modelZ/16);}
    }
    public record Mesh(List<Bone> bones, int textureWidth, int textureHeight,PlayerSkinMesh skin,
                       Map<String,ItemSocket> itemSockets) {
        public Mesh { itemSockets=Map.copyOf(itemSockets); }
        public Mesh(List<Bone> bones,int textureWidth,int textureHeight) { this(bones,textureWidth,textureHeight,null); }
        public Mesh(List<Bone> bones,int textureWidth,int textureHeight,PlayerSkinMesh skin) { this(bones,textureWidth,textureHeight,skin,Map.of()); }
    }
    public record Binding(String actionId, String thirdPersonClip, String firstPersonClip,
                          boolean hideArmor, String reviewState, WeaponSockets weaponSockets,PlayerPosePolicy posePolicy) {
        public Binding(String actionId,String thirdPersonClip,String firstPersonClip,boolean hideArmor,String reviewState) {
            this(actionId,thirdPersonClip,firstPersonClip,hideArmor,reviewState,WeaponSockets.canonical(),PlayerPosePolicy.defaults());
        }
        public Binding(String actionId,String thirdPersonClip,String firstPersonClip,boolean hideArmor,String reviewState,WeaponSockets weaponSockets) {
            this(actionId,thirdPersonClip,firstPersonClip,hideArmor,reviewState,weaponSockets,PlayerPosePolicy.defaults());
        }
    }

    private static Map<String, Clip> clips = Map.of();
    private static Map<String, Clip> modelClips = Map.of();
    private static Map<String, Mesh> meshes = Map.of();
    private static Map<String, Binding> bindings = Map.of();
    private static Map<String, FormView> formViews = Map.of();
    private static int revision;

    private ActionPresentationRegistry() {}
    public static int revision() { return revision; }
    public static Optional<Clip> clip(String actionId, boolean firstPerson) {
        Binding binding = bindings.get(stripNamespace(actionId));
        if (binding == null) return Optional.empty();
        return Optional.ofNullable(clips.get(stripNamespace(firstPerson ? binding.firstPersonClip() : binding.thirdPersonClip())));
    }
    public static Optional<Mesh> mesh(String name) { return Optional.ofNullable(meshes.get(name)); }
    public static Optional<FormView> formView(String name){return Optional.ofNullable(formViews.get(name));}
    public static Optional<Binding> binding(String actionId) { return Optional.ofNullable(bindings.get(stripNamespace(actionId))); }
    public static Map<String, Pose> modelPose(String name, float progress) {
        return modelPose(name,"attack",progress);
    }
    public static Map<String, Pose> modelPose(String name, String animation, float progress) {
        Clip clip = modelClips.get("animation." + name + "." + animation);
        return clip == null ? Map.of() : clip.sample(progress);
    }
    /** Loop in authored seconds rather than stretching every model idle to four seconds. */
    public static float modelLoopProgress(String name,String animation,double elapsedTicks){
        Clip clip=modelClips.get("animation."+name+"."+animation);
        if(clip==null)return 0;
        double duration=clip.length()*20;
        return (float)((Math.max(0,elapsedTicks)%duration)/duration);
    }
    public static boolean available(String actionId) { return clip(actionId, false).isPresent() && clip(actionId, true).isPresent(); }

    public static void reload(ResourceManager resources) {
        FirstPersonWeaponView.reload(resources);
        Map<String, Clip> loadedClips = new HashMap<>();
        Map<String, Clip> loadedModelClips = new HashMap<>();
        Map<String, Mesh> loadedMeshes = new HashMap<>();
        Map<String, Binding> loadedBindings = new HashMap<>();
        Map<String, FormView> loadedFormViews = new HashMap<>();
        resources.listResources("player_animations", id -> id.getNamespace().equals("mc_wuwa") && id.getPath().endsWith(".json")).forEach((id, resource) -> {
            try (Reader reader = resource.openAsReader()) {
                JsonObject animations = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("animations");
                animations.entrySet().forEach(entry -> loadedClips.put(entry.getKey(), parseClip(entry.getValue().getAsJsonObject())));
            } catch (Exception error) { McWuwa.LOGGER.error("Cannot load animation {}", id, error); }
        });
        resources.listResources("geo", id -> id.getNamespace().equals("mc_wuwa") && id.getPath().endsWith(".json")).forEach((id, resource) -> {
            try (Reader reader = resource.openAsReader()) {
                String key = id.getPath().substring("geo/".length()).replace(".geo.json", "");
                loadedMeshes.put(key, parseMesh(JsonParser.parseReader(reader).getAsJsonObject()));
            } catch (Exception error) { McWuwa.LOGGER.error("Cannot load model {}", id, error); }
        });
        // The project-owned weighted skin format is loaded separately from Gecko's rigid cube format.
        // If either canonical player rig is missing/invalid, keep the vanilla player visible.
        loadedMeshes.remove("rigs/player_wide");loadedMeshes.remove("rigs/player_slim");
        resources.listResources("presentation/rigs", id -> id.getNamespace().equals("mc_wuwa") && id.getPath().endsWith(".skin.json")).forEach((id,resource)-> {
            try(Reader reader=resource.openAsReader()) {
                String key="rigs/"+id.getPath().substring("presentation/rigs/".length()).replace(".skin.json","");
                loadedMeshes.put(key,parsePlayerMesh(JsonParser.parseReader(reader).getAsJsonObject()));
            } catch(Exception error) { McWuwa.LOGGER.error("Cannot load continuous player skin {}; retaining vanilla visibility",id,error); }
        });
        resources.listResources("animations", id -> id.getNamespace().equals("mc_wuwa") && id.getPath().endsWith(".json")).forEach((id, resource) -> {
            try (Reader reader = resource.openAsReader()) {
                JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("animations").entrySet()
                    .forEach(entry -> loadedModelClips.put(entry.getKey(), parseClip(entry.getValue().getAsJsonObject())));
            } catch (Exception error) { McWuwa.LOGGER.error("Cannot load model animation {}", id, error); }
        });
        resources.listResources("presentation/characters", id -> id.getNamespace().equals("mc_wuwa") && id.getPath().endsWith(".json")).forEach((id, resource) -> {
            try (Reader reader = resource.openAsReader()) {
                for (JsonElement element : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("actions")) {
                    JsonObject action = element.getAsJsonObject();
                    String key = stripNamespace(action.get("actionId").getAsString());
                    // Autonomous fields/projectiles own their presentation.
                    // An explicit empty-mask feedback entry is not a player
                    // animation binding and must not replay the active body.
                    if(action.has("feedbackOnly")&&action.get("feedbackOnly").getAsBoolean()
                        &&action.has("boneMask")&&action.getAsJsonArray("boneMask").isEmpty())continue;
                    try {
                        Clip tp=loadedClips.get(stripNamespace(requiredName(action,"thirdPersonClip")));
                        Clip fp=loadedClips.get(stripNamespace(requiredName(action,"firstPersonClip")));
                        if(tp==null||fp==null)throw new IllegalArgumentException("Missing TP/FP clip");
                        Binding binding = parseBinding(action,tp,fp);
                        validateWeaponSockets(binding.weaponSockets(),loadedMeshes.get("rigs/player_wide"));
                        validateWeaponSockets(binding.weaponSockets(),loadedMeshes.get("rigs/player_slim"));
                        if(loadedBindings.putIfAbsent(key,binding)!=null)throw new IllegalArgumentException("Duplicate action binding");
                    } catch(Exception error) {
                        loadedBindings.remove(key);
                        McWuwa.LOGGER.error("Action {} has invalid presentation; vanilla visibility will be retained",key,error);
                    }
                }
            } catch (Exception error) { McWuwa.LOGGER.error("Cannot load presentation binding {}", id, error); }
        });
        resources.getResource(ResourceLocation.fromNamespaceAndPath("mc_wuwa","presentation/models.json")).ifPresent(resource->{
            try(Reader reader=resource.openAsReader()) {
                for(JsonElement element:JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("models")) {
                    JsonObject entry=element.getAsJsonObject();if(!entry.has("firstPersonView"))continue;
                    String name=stripNamespace(requiredName(entry,"modelId"));
                    try {
                        if(!loadedMeshes.containsKey(name))throw new IllegalArgumentException("Missing form mesh "+name);
                        if(loadedFormViews.putIfAbsent(name,parseFormView(entry.getAsJsonObject("firstPersonView")))!=null)
                            throw new IllegalArgumentException("Duplicate form view "+name);
                    } catch(Exception error) {loadedFormViews.remove(name);McWuwa.LOGGER.error("Cannot load first-person form view {}",name,error);}
                }
            } catch(Exception error) {McWuwa.LOGGER.error("Cannot load first-person form views",error);}
        });
        clips = Map.copyOf(loadedClips);
        modelClips = Map.copyOf(loadedModelClips);
        meshes = Map.copyOf(loadedMeshes);
        bindings = Map.copyOf(loadedBindings);
        formViews=Map.copyOf(loadedFormViews);
        revision++;
        McWuwa.LOGGER.info("Loaded {} action presentations, {} clips and {} meshes; visual acceptance is tracked separately", bindings.size(), clips.size(), meshes.size());
    }

    private static String stripNamespace(String id) { return id.startsWith("mc_wuwa:") ? id.substring(8) : id; }
    static FormView parseFormView(JsonObject json) {
        JsonElement version=json.get("schemaVersion"),scale=json.get("scale");
        if(version==null||!version.isJsonPrimitive()||!version.getAsJsonPrimitive().isNumber()||version.getAsDouble()!=1)
            throw new IllegalArgumentException("Unsupported form view version");
        if(scale==null||!scale.isJsonPrimitive()||!scale.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Missing form view scale");
        Vector3f camera=socketVector(json.get("cameraAnchor"),"cameraAnchor"),model=socketVector(json.get("modelAnchor"),"modelAnchor");
        return new FormView(camera.x,camera.y,camera.z,model.x,model.y,model.z,scale.getAsFloat());
    }
    private static Clip parseClip(JsonObject json) {
        Map<String, BoneCurves> bones = new HashMap<>();
        json.getAsJsonObject("bones").entrySet().forEach(entry -> {
            JsonObject bone = entry.getValue().getAsJsonObject();
            bones.put(entry.getKey(), new BoneCurves(parseCurve(bone.get("position"), 0), parseCurve(bone.get("rotation"), 0), parseCurve(bone.get("scale"), 1)));
        });
        return new Clip(json.get("animation_length").getAsDouble(), Map.copyOf(bones));
    }
    private static Curve parseCurve(JsonElement json, float defaultValue) {
        if (json == null) return new Curve(List.of(new Frame(0, new Vector3f(defaultValue))));
        if (json.isJsonArray()) return new Curve(List.of(new Frame(0, vector(json))));
        List<Frame> frames = new ArrayList<>();
        json.getAsJsonObject().entrySet().forEach(entry -> {
            JsonElement value = entry.getValue();
            if (value.isJsonObject()) value = value.getAsJsonObject().get("post");
            frames.add(new Frame(Double.parseDouble(entry.getKey()), vector(value)));
        });
        frames.sort(Comparator.comparingDouble(Frame::time));
        return new Curve(List.copyOf(frames));
    }
    private static Mesh parseMesh(JsonObject json) {
        JsonObject geometry = json.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        JsonObject description = geometry.getAsJsonObject("description");
        List<Bone> bones = new ArrayList<>();
        for (JsonElement entry : geometry.getAsJsonArray("bones")) {
            JsonObject bone = entry.getAsJsonObject();
            List<Cube> cubes = new ArrayList<>();
            if (bone.has("cubes")) for (JsonElement cubeElement : bone.getAsJsonArray("cubes")) {
                JsonObject cube = cubeElement.getAsJsonObject();
                Map<String, Uv> faces = new HashMap<>();
                cube.getAsJsonObject("uv").entrySet().forEach(face -> {
                    JsonObject uv = face.getValue().getAsJsonObject();
                    JsonArray point = uv.getAsJsonArray("uv"), size = uv.getAsJsonArray("uv_size");
                    faces.put(face.getKey(), new Uv(point.get(0).getAsFloat(), point.get(1).getAsFloat(), size.get(0).getAsFloat(), size.get(1).getAsFloat()));
                });
                var vertices=new ArrayList<Vector3f>();
                if(cube.has("mc_wuwa_vertices"))for(var point:cube.getAsJsonArray("mc_wuwa_vertices"))vertices.add(vector(point));
                cubes.add(new Cube(vector(cube.get("origin")), vector(cube.get("size")), vector(cube.has("pivot") ? cube.get("pivot") : bone.get("pivot")), vector(cube.get("rotation")), cube.has("inflate") ? cube.get("inflate").getAsFloat() : 0, Map.copyOf(faces),vertices));
            }
            bones.add(new Bone(bone.get("name").getAsString(), bone.has("parent") ? bone.get("parent").getAsString() : "", vector(bone.get("pivot")), vector(bone.get("rotation")), List.copyOf(cubes)));
        }
        var ordered=orderedBones(bones);
        return new Mesh(ordered,description.get("texture_width").getAsInt(),description.get("texture_height").getAsInt(),null,
            json.has("itemSockets")?parseItemSockets(json,ordered):Map.of());
    }
    static Mesh parsePlayerMesh(JsonObject json) {
        Mesh bind=parseMesh(json.getAsJsonObject("bindRig"));
        Map<String,ItemSocket> sockets=parseItemSockets(json,bind.bones());
        return new Mesh(bind.bones(),bind.textureWidth(),bind.textureHeight(),PlayerSkinMesh.parse(json,bind.bones()),sockets);
    }
    static Binding parseBinding(JsonObject action,Clip tp,Clip fp) {
        JsonObject sockets=action.getAsJsonObject("weaponSockets");
        if(sockets==null)throw new IllegalArgumentException("Missing weaponSockets");
        // segmentedPrimary used a separate, unparsed locator convention. Accept no silent fallback.
        if(sockets.has("segmentedPrimary"))throw new IllegalArgumentException("Obsolete segmentedPrimary socket reference");
        WeaponSockets refs=new WeaponSockets(requiredName(sockets,"primary"),requiredName(sockets,"leftHandedPrimary"));
        return new Binding(stripNamespace(requiredName(action,"actionId")),requiredName(action,"thirdPersonClip"),
            requiredName(action,"firstPersonClip"),action.getAsJsonObject("visibilityPolicy").get("hideArmor").getAsBoolean(),
            requiredName(action,"reviewState"),refs,PlayerPosePolicy.parse(action,tp,fp));
    }
    static void validateWeaponSockets(WeaponSockets refs,Mesh mesh) {
        if(mesh==null)throw new IllegalArgumentException("Missing canonical player mesh");
        ItemSocket right=mesh.itemSockets().get(refs.primary()),left=mesh.itemSockets().get(refs.leftHandedPrimary());
        if(right==null||!right.physicalArm().equals("right")||left==null||!left.physicalArm().equals("left"))
            throw new IllegalArgumentException("Weapon sockets must resolve to physical right and left hands");
    }
    private static Map<String,ItemSocket> parseItemSockets(JsonObject json,List<Bone> bones) {
        if(!json.has("socketVersion")||!json.get("socketVersion").isJsonPrimitive()
            ||!json.get("socketVersion").getAsJsonPrimitive().isNumber()||json.get("socketVersion").getAsDouble()!=1)
            throw new IllegalArgumentException("Unsupported or missing socketVersion");
        if(!json.has("socketRotationOrder")||!json.get("socketRotationOrder").getAsString().equals("postmultiply_xyz_degrees"))
            throw new IllegalArgumentException("Unsupported socketRotationOrder");
        JsonObject sockets=json.getAsJsonObject("itemSockets");
        if(sockets==null||sockets.size()==0)throw new IllegalArgumentException("Missing itemSockets");
        Map<String,Bone> byName=new HashMap<>();for(Bone bone:bones)byName.put(bone.name(),bone);
        Map<String,ItemSocket> result=new HashMap<>();
        for(var entry:sockets.entrySet()) {
            if(entry.getKey().isBlank())throw new IllegalArgumentException("Blank socket name");
            JsonObject socket=entry.getValue().getAsJsonObject();String boneName=requiredName(socket,"bone");
            String arm=requiredName(socket,"physicalArm");
            if(!arm.equals("right")&&!arm.equals("left"))throw new IllegalArgumentException("Unknown physical arm "+arm);
            Bone bone=byName.get(boneName);
            if(bone==null)throw new IllegalArgumentException("Missing socket bone "+boneName);
            Bone ancestor=bone;
            while(ancestor!=null&&!ancestor.name().equals(arm+"_forearm"))ancestor=byName.get(ancestor.parent());
            if(ancestor==null||bone.name().equals(arm+"_forearm"))throw new IllegalArgumentException("Socket requires an item bone below its physical forearm");
            Vector3f point=socketVector(socket.get("bindPoint"),"bindPoint");
            if(point.distanceSquared(bone.pivot())>1e-10f)throw new IllegalArgumentException("Socket bone pivot must equal its absolute bindPoint: "+entry.getKey());
            Vector3f tp=socketVector(socket.get("thirdPersonRotation"),"thirdPersonRotation");
            Vector3f fp=socketVector(socket.get("firstPersonRotation"),"firstPersonRotation");
            result.put(entry.getKey(),new ItemSocket(boneName,arm,point.x,point.y,point.z,
                new SocketRotation(tp.x,tp.y,tp.z),new SocketRotation(fp.x,fp.y,fp.z)));
        }
        return Map.copyOf(result);
    }
    private static Vector3f socketVector(JsonElement value,String field) {
        if(value==null||!value.isJsonArray()||value.getAsJsonArray().size()!=3)
            throw new IllegalArgumentException("Socket "+field+" requires three components");
        for(JsonElement component:value.getAsJsonArray())
            if(!component.isJsonPrimitive()||!component.getAsJsonPrimitive().isNumber())
                throw new IllegalArgumentException("Socket "+field+" requires numeric components");
        Vector3f result=vector(value);
        if(!result.isFinite())throw new IllegalArgumentException("Non-finite socket "+field);
        return result;
    }
    private static String requiredName(JsonObject json,String field) {
        if(!json.has(field)||!json.get(field).isJsonPrimitive()||!json.get(field).getAsJsonPrimitive().isString()
            ||json.get(field).getAsString().isBlank())throw new IllegalArgumentException("Missing or blank "+field);
        return json.get(field).getAsString();
    }
    static List<Bone> orderedBones(List<Bone> bones) {
        Map<String,Bone> byName=new LinkedHashMap<>();
        for(Bone bone:bones) {
            if(byName.putIfAbsent(bone.name(),bone)!=null)throw new IllegalArgumentException("Duplicate bone "+bone.name());
            if(!bone.pivot().isFinite()||!bone.rotation().isFinite())throw new IllegalArgumentException("Non-finite bind transform "+bone.name());
        }
        List<Bone> ordered=new ArrayList<>();Set<String> visiting=new HashSet<>(),done=new HashSet<>();
        for(Bone bone:bones)orderBone(bone,byName,visiting,done,ordered);
        return List.copyOf(ordered);
    }
    private static void orderBone(Bone bone,Map<String,Bone> byName,Set<String> visiting,Set<String> done,List<Bone> output) {
        if(done.contains(bone.name()))return;
        if(!visiting.add(bone.name()))throw new IllegalArgumentException("Cyclic parent chain at "+bone.name());
        if(bone.parent()!=null&&!bone.parent().isEmpty()) {
            Bone parent=byName.get(bone.parent());
            if(parent==null)throw new IllegalArgumentException("Missing parent "+bone.parent()+" for "+bone.name());
            orderBone(parent,byName,visiting,done,output);
        }
        visiting.remove(bone.name());done.add(bone.name());output.add(bone);
    }
    private static Vector3f vector(JsonElement value) {
        if (value == null) return new Vector3f();
        JsonArray array = value.getAsJsonArray();
        return new Vector3f(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat());
    }
}
