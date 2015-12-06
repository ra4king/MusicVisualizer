#version 430

#define PI 3.14159265358979323846
#define TAU (2.0*PI)

uniform float time;
uniform vec2 resolution;

layout(r32f, binding = 0) uniform readonly image2D frequencies;

out vec4 fragColor;

void main() {
	vec2 coord = (gl_FragCoord.xy / resolution) - vec2(0.5); 
	
	float radius = length(coord);
	float angle1 = atan(coord.y, coord.x);
	float angle2 = -atan(coord.y, coord.x);
	float segment1 = radius * angle1;
	float segment2 = radius * angle2;
	float circum = radius * PI;
	
	float div = imageSize(frequencies).x / circum;
	int idx1 = int(segment1 * div);
	int idx2 = int(segment2 * div);
	float c1 = imageLoad(frequencies, ivec2(idx1, 0)).r / 25.0;
	float c2 = imageLoad(frequencies, ivec2(idx2, 1)).r / 25.0;
	
	float sinTime = 0.00001 * sin(4.0 * time) + 0.3;
	float halfSinTime = sinTime * 0.5;
	fragColor = vec4(1.0 - clamp(abs((radius - 0.4) / clamp(c1, -halfSinTime, halfSinTime)), 0.0, 1.0), 0.2 , 1.4 - clamp(abs((radius - 0.4) / clamp(c2, -halfSinTime, halfSinTime)), 0.0, 1.0), 1.0);
}
