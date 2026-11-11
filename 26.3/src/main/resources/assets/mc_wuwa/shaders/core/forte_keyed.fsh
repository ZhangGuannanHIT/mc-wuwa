#version 330
#extension GL_ARB_separate_shader_objects : require

// Can't moj_import in things used during startup, when resource packs don't exist.
// This is a copy of dynamicimports.glsl
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 source = texture(Sampler0, texCoord0);
    float high = max(source.r, max(source.g, source.b));
    float low = min(source.r, min(source.g, source.b));
    // Tutorial sheets bake a dark gray checker matte; Jinhsi's sheet uses dark blue.
    // Remove only this display matte at draw time. Original resource pixels stay intact.
    bool grayMatte = high < 0.36 && high - low < 0.065;
    bool blueMatte = source.b > source.r * 1.5 && source.g < 0.45 && source.b < 0.63;
    if (source.a == 0.0 || grayMatte || blueMatte) discard;
    fragColor = source * vertexColor * ColorModulator;
}
