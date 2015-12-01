package com.ra4king.music_visualizer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

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
	private FloatBuffer freqs;
	
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
		
		visualizer = new ShaderProgram(readFromFile("visualizer.vert"), readFromFile("visualizer.frag"));
		
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
		}
		catch(Exception exc) {
			throw new RuntimeException(exc);
		}
		
		try {
			AudioFormat format = new AudioFormat(Encoding.PCM_SIGNED, (float)wavFile.getSampleRate(), 16, wavFile.getNumChannels(), 2 * wavFile.getNumChannels(), (float)wavFile.getSampleRate(), true);
			audioDataLine = AudioSystem.getSourceDataLine(format);
			audioDataLine.open(format);
			audioDataLine.start();
			
			System.out.println(Arrays.toString(audioDataLine.getControls()));
			
			audioData = ByteBuffer.allocate((int)(wavFile.getSampleRate() * 2 * wavFile.getNumChannels()));
			audioData.order(ByteOrder.BIG_ENDIAN);
			
			freqs = BufferUtils.createFloatBuffer((int)(wavFile.getSampleRate() / 100 * wavFile.getNumChannels()));
		} catch(Exception exc) {
			throw new RuntimeException(exc);
		}
	}
	
	/**
	 * The Goertzel algorithm computes the k-th DFT coefficient of the input signal using a second-order filter.
	 * http://ptolemy.eecs.berkeley.edu/papers/96/dtmf_ict/www/node3.html.
	 * Basiclly it just does a DFT of the frequency we want to check, and none of the others (FFT calculates for all frequencies).
	 */
	private float goertzel(double x[], int count, float frequency, int samplerate) {
		double Skn, Skn1, Skn2;
		Skn = Skn1 = Skn2 = 0;
		
		for(int i = 0; i < count; i++) {
			Skn2 = Skn1;
			Skn1 = Skn;
			Skn = 2 * Math.cos(2 * Math.PI * frequency / samplerate) * Skn1 - Skn2 + x[i];
		}
		
		double WNk = Math.exp(-2 * Math.PI * frequency / samplerate);
		return (float)(Skn - WNk * Skn1);
	}
	
	@Override
	public void render() {
		visualizer.begin();
		
		glUniform2f(visualizer.getUniformLocation("resolution"), getWidth(), getHeight());
		
		int frameCount = (int)(wavFile.getSampleRate() / (getLastFps() == 0 ? 60 : getLastFps()));
		double[][] samples = new double[wavFile.getNumChannels()][frameCount];
		
		int numRead = 0;
		try {
			numRead = wavFile.readFrames(samples, frameCount);
		} catch(Exception exc) {
			exc.printStackTrace();
			samples = null;
		}
		
		audioData.clear();
		for(int i = 0; i < numRead; i++) {
			for(int j = 0; j < wavFile.getNumChannels(); j++) {
				audioData.putShort((short)(samples[j][i] * 0x7FFF));
			}
		}
		audioDataLine.write(audioData.array(), 0, numRead * 2 * wavFile.getNumChannels());
		
		if(samples != null) {
			final float maxFreq = 20000;
			
			freqs.clear();
			
			float step = maxFreq / (freqs.capacity() / wavFile.getNumChannels());
			for(float i = 0; i < maxFreq && freqs.remaining() >= wavFile.getNumChannels(); i += step) {
				for(int j = 0; j < wavFile.getNumChannels(); j++) {
					try {
						freqs.put(goertzel(samples[j], numRead, i, (int)wavFile.getSampleRate()));
					} catch(Exception exc) {
						System.out.println(numRead + " " + freqs.position());
						throw exc;
					}
				}
			}
			freqs.flip();
			
			glUniform1(visualizer.getUniformLocation("frequencies"), freqs);
			glUniform1i(visualizer.getUniformLocation("freqCount"), freqs.capacity() / wavFile.getNumChannels());
			glUniform1f(visualizer.getUniformLocation("numChannels"), wavFile.getNumChannels());
		}
		
		glBindVertexArray(fullScreenQuadVao);
		glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_SHORT, 0);
		glBindVertexArray(0);
		
		visualizer.end();
	}
	
	@Override
	public void update(long deltaTime) {
		super.update(deltaTime);
	}
}
