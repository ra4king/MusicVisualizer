package com.ra4king.music_visualizer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL11.glGetInteger;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL42.*;
import static org.lwjgl.opengl.GL43.*;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFormat.Encoding;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL30;

import com.ra4king.opengl.util.GLProgram;
import com.ra4king.opengl.util.ShaderProgram;

import wavfile.WavFile;

/**
 * @author Roi Atalla
 */
public class MusicVisualizer extends GLProgram {
	public static void main(String[] args) {
		new MusicVisualizer().run(true);
	}
	
	private ShaderProgram visualizer;
	private int fullScreenQuadVao;
	
	private WavFile wavFile;
	private SourceDataLine audioDataLine;
	private ByteBuffer audioData;
	
	private double[][] samples;
	private int numRead;
	
	private ShaderProgram visualizerCompute;
	
	private FloatBuffer samplesBuffer;
	
	public MusicVisualizer() {
		super("Music Visualizer", 800, 600, true);
	}
	
	@Override
	public void init() {
		System.out.println(glGetString(GL_VERSION));
		System.out.println(glGetString(GL_VENDOR));
		System.out.println(glGetString(GL_RENDERER));
		
		setPrintDebug(true);
		setFPS(60);
		
		FloatBuffer quadBuffer = BufferUtils.createFloatBuffer(4 * 2);
		quadBuffer.put(new float[] {
		  1, 1,
		  1, -1,
		  -1, -1,
		  -1, 1
		});
		quadBuffer.flip();
		
		ShortBuffer quadIndices = BufferUtils.createShortBuffer(6);
		quadIndices.put(new short[] { 0, 1, 2, 2, 3, 0 });
		quadIndices.flip();
		
		fullScreenQuadVao = glGenVertexArrays();
		glBindVertexArray(fullScreenQuadVao);
		
		int quadVbo = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, quadVbo);
		glBufferData(GL_ARRAY_BUFFER, quadBuffer, GL_STATIC_DRAW);
		
		glEnableVertexAttribArray(0);
		glVertexAttribPointer(0, 2, GL_FLOAT, false, 0, 0);
		
		int quadIdxVbo = glGenBuffers();
		glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, quadIdxVbo);
		glBufferData(GL_ELEMENT_ARRAY_BUFFER, quadIndices, GL_STATIC_DRAW);
		
		glBindVertexArray(0);
		glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
		
		try {
			wavFile = WavFile.openWavFile(new File(getClass().getResource("Stairway to Heaven.wav").toURI()));
			wavFile.display();
			
			samples = new double[wavFile.getNumChannels()][5 * (int)(wavFile.getSampleRate())];
		}
		catch(Exception exc) {
			throw new RuntimeException(exc);
		}
		
		visualizer = new ShaderProgram(readFromFile("visualizer.vert"), readFromFile("visualizer.frag"));
		visualizerCompute = new ShaderProgram(readFromFile("visualizer.comp"));
		visualizerCompute.begin();
		glUniform1f(visualizerCompute.getUniformLocation("samplerate"), wavFile.getSampleRate());
		visualizerCompute.end();
		
		glActiveTexture(GL_TEXTURE0);
		
		int freqsTex = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, freqsTex);
		glTexStorage2D(GL_TEXTURE_2D, 1, GL_R32F, 2000, wavFile.getNumChannels());
		
		glBindImageTexture(0, freqsTex, 0, false, 0, GL_WRITE_ONLY, GL_R32F);
		
		int samplesBufferName = glGenBuffers();
		glBindBuffer(GL_SHADER_STORAGE_BUFFER, samplesBufferName);
		glBufferData(GL_SHADER_STORAGE_BUFFER, 0, GL_DYNAMIC_DRAW);
		
		glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, samplesBufferName);
		
		try {
			AudioFormat format = new AudioFormat(Encoding.PCM_SIGNED, (float)wavFile.getSampleRate(), 16, wavFile.getNumChannels(), 2 * wavFile.getNumChannels(), (float)wavFile.getSampleRate(), true);
			audioDataLine = AudioSystem.getSourceDataLine(format);
			audioDataLine.open(format);
			audioDataLine.start();
			
			System.out.println(Arrays.toString(audioDataLine.getControls()));
			
			audioData = ByteBuffer.allocate(samples.length * samples[0].length * 2);
			audioData.order(ByteOrder.BIG_ENDIAN);
			
			samplesBuffer = BufferUtils.createFloatBuffer(1000 * wavFile.getNumChannels());
		}
		catch(Exception exc) {
			throw new RuntimeException(exc);
		}
		
		System.out.println("GL_MAX_COMPUTE_WORK_GROUP_COUNT: (" + GL30.glGetInteger(GL_MAX_COMPUTE_WORK_GROUP_COUNT, 0) + ", "
				                   + GL30.glGetInteger(GL_MAX_COMPUTE_WORK_GROUP_COUNT, 1) + ", "
				                   + GL30.glGetInteger(GL_MAX_COMPUTE_WORK_GROUP_COUNT, 2) + ")");
		System.out.println("GL_MAX_COMPUTE_WORK_GROUP_SIZE: (" + GL30.glGetInteger(GL_MAX_COMPUTE_WORK_GROUP_SIZE, 0) + ", "
				                   + GL30.glGetInteger(GL_MAX_COMPUTE_WORK_GROUP_SIZE, 1) + ", "
				                   + GL30.glGetInteger(GL_MAX_COMPUTE_WORK_GROUP_SIZE, 2) + ")");
		System.out.println("GL_MAX_COMPUTE_WORK_GROUP_INVOCATIONS: " + glGetInteger(GL_MAX_COMPUTE_WORK_GROUP_INVOCATIONS));
		System.out.println("GL_MAX_COMPUTE_SHARED_MEMORY_SIZE: " + glGetInteger(GL_MAX_COMPUTE_SHARED_MEMORY_SIZE));
	}
	
	@Override
	public void render() {
		samplesBuffer.clear();
		for(int i = 0; i < numRead; i++) {
			for(int j = 0; j < wavFile.getNumChannels(); j++) {
				audioData.putShort((short)(samples[j][i] * 0x7FFF));
				if(samplesBuffer.hasRemaining())
					samplesBuffer.put((float)samples[j][i]);
			}
		}
		samplesBuffer.flip();
		
		int totalBytes = numRead * 2 * wavFile.getNumChannels();
		int lenToWrite = Math.min(totalBytes, audioDataLine.available());
		audioDataLine.write(audioData.array(), 0, lenToWrite);
		audioData.flip().position(lenToWrite);
		audioData.compact();
		
		glBufferData(GL_SHADER_STORAGE_BUFFER, samplesBuffer, GL_DYNAMIC_DRAW);
		
		visualizerCompute.begin();
		glDispatchCompute(40, wavFile.getNumChannels(), 1);
		
		visualizer.begin();
		glUniform2f(visualizer.getUniformLocation("resolution"), getWidth(), getHeight());
		
		glBindVertexArray(fullScreenQuadVao);
		glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_SHORT, 0);
		glBindVertexArray(0);
	}
	
	@Override
	public void update(long deltaTime) {
		super.update(deltaTime);
		
		int frameCount = (int)(wavFile.getSampleRate() / (1e9 / deltaTime));
		
		int toRead = Math.min(Math.min(frameCount, samples[0].length), audioData.remaining() / (2 * wavFile.getNumChannels()));
		
		try {
			numRead = wavFile.readFrames(samples, 0, toRead);
		}
		catch(Exception exc) {
			System.out.println("Frame count: " + frameCount + ", len: " + samples[0].length);
			exc.printStackTrace();
			numRead = 0;
		}
	}
}
