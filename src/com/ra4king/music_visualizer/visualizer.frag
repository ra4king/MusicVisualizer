#version 430

#define PI 3.14159265358979323846
#define TAU (2.0*PI)

uniform float time;
uniform vec2 resolution;

layout(r32f, binding = 0) uniform readonly image2D frequencies;

out vec4 fragColor;

void main() {
	vec2 coord = (gl_FragCoord.xy / resolution) - vec2(0.5); 
	
	float angle1 = -atan(coord.x, coord.y);
	float angle2 = -angle1;
	
	float radius = length(coord) + 0.05 * sin(30.0 * time + 20.0 * angle1);
	
	float segment1 = radius * angle1;
	float segment2 = radius * angle2;
	
	float div = imageSize(frequencies).x / (radius * PI);
	int idx1 = int(segment1 * div);
	int idx2 = int(segment2 * div);
	float f1 = imageLoad(frequencies, ivec2(idx1, 0)).r / 25.0;
	float f2 = imageLoad(frequencies, ivec2(idx2, 1)).r / 25.0;
	
	float offset = 0.45;//0.1 * sin(5.0 * time) + 0.25;
	float height = 0.2;
	float c1 = 1.0 - clamp(abs((radius - offset) / clamp(f1, -height, height)), 0.0, 1.0);
	float c2 = 1.0 - clamp(abs((radius - offset) / clamp(f2, -height, height)), 0.0, 1.0);
	
	offset = 0.35;
	height = 0.2;
	float c3 = 1.0 - clamp(abs((radius - offset) / clamp(f1, -height, height)), 0.0, 1.0);
    float c4 = 1.0 - clamp(abs((radius - offset) / clamp(f2, -height, height)), 0.0, 1.0);
    
    offset = 0.25;
    height = 0.1;
    float c5 = 1.0 - clamp(abs((radius - offset) / clamp(f1, -height, height)), 0.0, 1.0);
    float c6 = 1.0 - clamp(abs((radius - offset) / clamp(f2, -height, height)), 0.0, 1.0);
    
    offset = 0.15;
    height = 0.05;
    float c7 = 1.0 - clamp(abs((radius - offset) / clamp(f1, -height, height)), 0.0, 1.0);
    float c8 = 1.0 - clamp(abs((radius - offset) / clamp(f2, -height, height)), 0.0, 1.0);
    
    float c = clamp(c1 + c2 + c3 + c4 + c5 + c6 + c7, 0.0, 1.0);
    
    float r = c1 + c3 + c5 + 0.5 * c6 + c7 - c8;
    float g = c2 + 0.5 * c3 + c5 + c7 + c8;
    float b = c2 + c4 + c6 + c7;
    
	fragColor = vec4(r, g, b, 0);
}
