#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    if (texture(Sampler0, texCoord).a < 0.003921569) {
        discard;
    }
    fragColor = ColorModulator;
}
