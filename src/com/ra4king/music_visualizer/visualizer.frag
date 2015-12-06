#version 430

uniform vec2 resolution;

layout(r32f, binding = 0) uniform readonly image2D frequencies;

out vec4 fragColor;

void main() {
	vec2 coord = gl_FragCoord.xy / resolution;
	
	int idx = int(coord.x * imageSize(frequencies).x);
	float c1 = imageLoad(frequencies, ivec2(idx, 0)).r / 75.0;
	float c2 = imageLoad(frequencies, ivec2(idx, 1)).r / 75.0;
	
	fragColor = vec4(1.0 - abs((coord.y - 0.75) / clamp(c1, -0.25, 0.25)), 0.0, 1.0 - abs((coord.y - 0.25) / clamp(c2, -0.25, 0.25)), 1.0);
}
