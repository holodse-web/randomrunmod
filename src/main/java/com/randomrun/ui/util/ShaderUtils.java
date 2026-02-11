package com.randomrun.ui.util;

import org.lwjgl.opengl.GL20;

public class ShaderUtils {
    public static int createProgram(String vert, String frag) {
        int vertId = createShader(vert, GL20.GL_VERTEX_SHADER);
        int fragId = createShader(frag, GL20.GL_FRAGMENT_SHADER);
        
        int programId = GL20.glCreateProgram();
        GL20.glAttachShader(programId, vertId);
        GL20.glAttachShader(programId, fragId);
        GL20.glLinkProgram(programId);
        
        if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == GL20.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(programId);
            throw new RuntimeException("Shader link error: " + log);
        }
        
        GL20.glDeleteShader(vertId);
        GL20.glDeleteShader(fragId);
        
        return programId;
    }
    
    private static int createShader(String source, int type) {
        int shaderId = GL20.glCreateShader(type);
        GL20.glShaderSource(shaderId, source);
        GL20.glCompileShader(shaderId);
        
        if (GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS) == GL20.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shaderId);
            throw new RuntimeException("Shader compile error: " + log);
        }
        
        return shaderId;
    }
}
