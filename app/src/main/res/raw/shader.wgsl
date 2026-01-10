struct Global {
    view_proj: mat4x4<f32>,
};
@group(0) @binding(0)
var<uniform> global: Global;

struct VertexInput {
    @location(0) position: vec3<f32>,
    @location(1) color: vec3<f32>,
}

struct VertexOutput {
    @builtin(position) clip_position: vec4<f32>,
    @location(1) color: vec3<f32>,
}

@vertex fn vertexMain(model: VertexInput) -> VertexOutput {
    var out: VertexOutput;
    out.clip_position = global.view_proj * vec4<f32>(model.position.xyz, 1.0);
    out.color = model.color;
    return out;
}

@fragment fn fragmentMain(in: VertexOutput) -> @location(0) vec4<f32> {
    return vec4(in.color.rgb, 1.0);
}