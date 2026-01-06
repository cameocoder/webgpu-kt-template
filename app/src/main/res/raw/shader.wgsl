struct Global {
    view_proj: mat4x4<f32>,
};
@group(0) @binding(0)
var<uniform> global: Global;

struct VertexOutput {
    @builtin(position) clip_position: vec4<f32>,
    @location(1) r_color: f32,
}
const square = array(
    vec3f(-1.0, -1.0, 0.0),
    vec3f(-1.0, 1.0, 0.0),
    vec3f(1.0, -1.0, 0.0),
    vec3f(-1.0, 1.0, 0.0),
    vec3f(1.0, 1.0, 0.0),
    vec3f(1.0, -1.0, 0.0));

@vertex fn vertexMain(@builtin(vertex_index) index : u32) -> VertexOutput {
    var out: VertexOutput;
    out.clip_position = global.view_proj * vec4<f32>(square[index], 1.0);
    if(index >=3) {
        out.r_color = 0.0;
    } else {
        out.r_color = 1.0;
    }
    return out;
}

@fragment fn fragmentMain(in: VertexOutput) -> @location(0) vec4<f32> {
    return vec4f(in.r_color, 1.0, 1.0, 1.0);
}