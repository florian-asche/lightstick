package de.cccmannheim.lightstick.tracker;

import ibxm.INoteListener;
import ibxm.jme.IBXMAdvancedLoader;
import ibxm.jme.IBXMLoader;
import ibxm.jme.NoteInfo;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetInfo;
import com.jme3.asset.plugins.ClasspathLocator;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.audio.AudioKey;
import com.jme3.audio.AudioNode;
import com.jme3.audio.AudioSource.Status;
import com.jme3.input.RawInputListener;
import com.jme3.input.event.JoyAxisEvent;
import com.jme3.input.event.JoyButtonEvent;
import com.jme3.input.event.KeyInputEvent;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.input.event.TouchEvent;
import com.jme3.math.FastMath;
import com.jme3.system.AppSettings;

public class TestIBMXPlayer extends SimpleApplication {

	private static final float	FADE_TIME	= 0.9f;

	public static void main(final String[] args) throws SocketException {
		final AppSettings settings = new AppSettings(true);
		// settings.setGammaCorrection(true);
		final TestIBMXPlayer t = new TestIBMXPlayer();
		t.setSettings(settings);
		t.start();
	}

	private static boolean										TEST_NODE	= true;
	final float[][]												packet		= new float[TestIBMXPlayer.STICK_COUNT][61 * 3 + 4];
	private AudioNode											anode;
	private float												playtime	= 0;
	protected ConcurrentHashMap<Float, Map<NoteInfo, NoteInfo>>	todispatch	= new ConcurrentHashMap<>();

	final static int											STICK_COUNT	= 16;

	DatagramSocket												datagramSocket;
	private File												folder;
	private String												lastFilename;
	private List<File>											fileList;
	int															fileId		= -1;

	public TestIBMXPlayer() throws SocketException {
		this.setShowSettings(false);
		this.datagramSocket = new DatagramSocket();
	}

	@Override
	public void simpleInitApp() {
		this.folder = new File("./music");

		this.setPauseOnLostFocus(false);

		this.flyCam.setDragToRotate(true);
		this.assetManager.registerLoader(IBXMLoader.class, "mod");
		this.assetManager.registerLocator("de/cccmannheim/lightstick/tracker", ClasspathLocator.class);
		this.assetManager.registerLocator(this.folder.getAbsolutePath(), FileLocator.class);

		try {
			this.play("pinball_illusions.mod");
		} catch (final Exception e1) {
			e1.printStackTrace();
		}

		this.inputManager.addRawInputListener(new RawInputListener() {

			@Override
			public void onTouchEvent(final TouchEvent evt) {
			}

			@Override
			public void onMouseMotionEvent(final MouseMotionEvent evt) {
			}

			@Override
			public void onMouseButtonEvent(final MouseButtonEvent evt) {
			}

			@Override
			public void onKeyEvent(final KeyInputEvent evt) {
				if (evt.isReleased() && evt.getKeyCode() == Keyboard.KEY_SPACE) {
					TestIBMXPlayer.this.playRandom();
				}
			}

			@Override
			public void onJoyButtonEvent(final JoyButtonEvent evt) {
			}

			@Override
			public void onJoyAxisEvent(final JoyAxisEvent evt) {
			}

			@Override
			public void endInput() {
			}

			@Override
			public void beginInput() {
			}
		});

		final File[] files = TestIBMXPlayer.this.folder.listFiles();
		this.fileList = Arrays.asList(files);
		Collections.shuffle(this.fileList);
	}

	protected void playRandom() {
		this.fileId++;
		if (this.fileId > this.fileList.size() - 1) {
			this.fileId = 0;
		}
		final File file = this.fileList.get(this.fileId);
		if (file.getName().endsWith(".xm") || file.getName().endsWith(".mod") || file.getName().endsWith(".s3m")) {
			final Path relative = TestIBMXPlayer.this.folder.toPath().relativize(file.toPath());
			TestIBMXPlayer.this.play(relative.toString());
		}
	}

	private void play(final String fname) {
		this.lastFilename = fname;
		Display.setTitle(fname + " trackerLight");
		if (this.anode != null) {
			this.anode.stop();
			this.anode = null;
		}
		this.playtime = 0;
		final AudioKey ak = new AudioKey(fname, true);
		final AssetInfo loadInfo = this.assetManager.locateAsset(ak);
		try {
			final IBXMAdvancedLoader al = new IBXMAdvancedLoader(loadInfo, new INoteListener() {
				@Override
				public void onNote(final float posInSec, final int id, final int volume, final int noteKey, final int fadeoutVol, final int instrumentid, final int panning, int freq) {
					Map<NoteInfo, NoteInfo> notelist = TestIBMXPlayer.this.todispatch.get(posInSec);
					if (notelist == null) {
						notelist = new ConcurrentHashMap<>();
						TestIBMXPlayer.this.todispatch.put(posInSec, notelist);
					}
					NoteInfo vv = new NoteInfo(id, volume, noteKey, fadeoutVol, instrumentid, panning, freq);
					notelist.put(vv, vv);
				}
			});
			this.anode = new AudioNode(al.getAudioData(), ak);
			this.anode.setPositional(false);
			this.anode.play();
		} catch (final IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void simpleUpdate(final float tpf) {

		this.playtime += tpf;
		if (this.anode == null || this.anode.getStatus() == Status.Stopped) {
			this.playRandom();
		}

		final List<Float> removeKeys = new ArrayList<>();

		for (final Entry<Float, Map<NoteInfo, NoteInfo>> ee : this.todispatch.entrySet()) {
			if (ee.getKey() < this.playtime) {
				removeKeys.add(ee.getKey());
				if (ee.getValue().keySet().size() > 200) {
					// song time calculation events!
					continue;
				}

				for (NoteInfo note : ee.getValue().keySet()) {
					final int stickid = Math.abs(note.id) % TestIBMXPlayer.STICK_COUNT;
					final int channel = Math.abs(note.instrumentid) % 3;
					final int ledid = 4 + (10 - Math.abs(note.noteKey + (note.panning / 10)) % 10) * 3 + channel;
					final float ncolor = FastMath.clamp(note.volume / 64f * note.globalVolume / 64f, 0.5f, 1f);
					System.out.println(note + "\t" + ncolor);

					if (((this.packet[stickid][ledid]) + ncolor > 1)) {
						this.packet[stickid][ledid] = 1;
						this.packet[stickid][ledid + (10 * 3)] = this.packet[stickid][ledid];
						this.packet[stickid][ledid + (20 * 3)] = this.packet[stickid][ledid];
						this.packet[stickid][ledid + (30 * 3)] = this.packet[stickid][ledid];
						this.packet[stickid][ledid + (40 * 3)] = this.packet[stickid][ledid];
						this.packet[stickid][ledid + (50 * 3)] = this.packet[stickid][ledid];
					} else {
						this.packet[stickid][ledid] += ncolor;
						this.packet[stickid][ledid + (10 * 3)] += ncolor;
						this.packet[stickid][ledid + (20 * 3)] += ncolor;
						this.packet[stickid][ledid + (30 * 3)] += ncolor;
						this.packet[stickid][ledid + (40 * 3)] += ncolor;
						this.packet[stickid][ledid + (50 * 3)] += ncolor;

					}
				}

			}
		}
		for (final Float r : removeKeys) {
			this.todispatch.remove(r);
		}

		for (int stickid = 0; stickid < TestIBMXPlayer.STICK_COUNT; stickid++) {
			this.packet[stickid][0] = (stickid + 1);
			try {
				final InetAddress address = InetAddress.getByName(TestIBMXPlayer.TEST_NODE ? "127.0.0.1" : ("192.168.23." + (stickid + 1)));
				final byte[] data = new byte[this.packet[stickid].length];
				this.copy(stickid, data);

				// dont copy the metadata!
				data[0] = (byte) this.packet[stickid][0];
				data[1] = 0;
				data[2] = 0;
				data[3] = 0;
				final DatagramPacket pp = new DatagramPacket(data, data.length, address, 2342);
				this.datagramSocket.send(pp);
			} catch (final IOException e) {
				e.printStackTrace();
			}
			for (int i = 4; i < this.packet[stickid].length; i++) {
				this.packet[stickid][i] *= 0.95f;
			}
		}
	}

	private void copy(int stickid, byte[] data) {
		for (int i = 4; i < data.length; i++) {
			data[i] = (byte) (this.packet[stickid][i] * 255);
		}
	}

}
