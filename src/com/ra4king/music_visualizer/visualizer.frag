#version 330

uniform vec2 resolution;

uniform float frequencies[1000];
uniform int freqCount;
uniform float numChannels;

out vec4 fragColor;

void main() {
	vec2 coord = gl_FragCoord.xy / resolution;
	
	int idx = int(coord.x * freqCount * numChannels);
	float c1 = frequencies[idx];
	float c2 = frequencies[idx + 1];
	
	fragColor = vec4(vec3(abs((coord.y - 0.5) / c1)), 1.0);
}
