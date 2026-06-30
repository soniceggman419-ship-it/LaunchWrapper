package org.mcphackers.launchwrapper;

import java.io.File;
import java.net.MalformedURLException;

import org.mcphackers.launchwrapper.loader.LaunchClassLoader;
import org.mcphackers.launchwrapper.target.LaunchTarget;
import org.mcphackers.launchwrapper.tweak.Tweak;
import org.mcphackers.launchwrapper.util.OS;

public class Launch {

	public static final String VERSION = "1.2.4";
	public static final Logger LOGGER = new Logger();

	/**
	 * Class loader where overwritten classes will be stored
	 */
	private static LaunchClassLoader CLASS_LOADER;
	private static Launch INSTANCE;

	public final LaunchConfig config;

	protected Launch(LaunchConfig config) {
		this.config = config;
		INSTANCE = this;
	}

	public static void main(String[] args) {
		LaunchConfig config = new LaunchConfig(args);
		Launch.create(config).launch();
	}

	public void launch() {
		LaunchClassLoader loader = getLoader();
		addJARs(loader);
		Tweak mainTweak = getTweak();
		if (mainTweak == null) {
			if (config.tweakClass.get() == null) {
				LOGGER.logErr("No suitable tweak found. Is Minecraft on classpath?");
			} else {
				LOGGER.logErr("Specified tweak does not exist.");
			}
			return;
		}
		mainTweak.prepare(loader);
		try {
			if (!mainTweak.transform(loader)) {
				LOGGER.logErr("Tweak could not be applied");
				if (!mainTweak.handleError(loader, null)) { // if handled successfully, continue
					return;
				}
			}
		} catch (Throwable t) {
			LOGGER.logErr("Tweak could not be applied", t);
			if (!mainTweak.handleError(loader, t)) {
				return;
			}
		}
		mainTweak.transformResources(loader);
		LaunchTarget target = mainTweak.getLaunchTarget();
		if (target == null) {
			LOGGER.logErr("Could not find launch target");
			return;
		}
		loader.setLoaderTweakers(mainTweak.getTweakers());
		target.launch(loader);
	}

	protected Tweak getTweak() {
		return Tweak.get(getLoader(), config);
	}

	protected LaunchClassLoader getLoader() {
		if (CLASS_LOADER == null) {
			CLASS_LOADER = LaunchClassLoader.instantiate();
			CLASS_LOADER.setDebugOutput(config.debugClassDump.get());
		}
		return CLASS_LOADER;
	}

	public static Launch getInstance() {
		return INSTANCE;
	}

	public static Launch create(LaunchConfig config) {
		return new Launch(config);
	}

	protected void addJARs(LaunchClassLoader loader) {
		try {
			File jar = config.gameJar.get();
			if (jar == null) {
				jar = new File(config.gameDir.get(), "minecraft.jar");
			}
			if (jar.isFile()) {
				loader.addURL(jar.toURI().toURL());
			}
		} catch (MalformedURLException e) {
			LOGGER.logErr("Invalid gameJar path", e);
		}
		File lwjglDir = config.lwjglDir.get();
		if (lwjglDir != null && lwjglDir.isDirectory()) {
			try {
				String nativePath = findNativePath(lwjglDir);
				if (nativePath != null) {
					System.setProperty("org.lwjgl.librarypath", nativePath);
					System.setProperty("org.lwjgl.library.path", nativePath);
					LOGGER.logDebug("Set LWJGL native path: " + nativePath);
				}
				File[] jars = lwjglDir.listFiles();
				if (jars != null) {
					for (File f : jars) {
						if (f.getName().endsWith(".jar")) {
							loader.addURL(f.toURI().toURL());
						}
					}
				}
			} catch (MalformedURLException e) {
				LOGGER.logErr("Invalid lwjglDir path", e);
			}
		}
	}

	private static boolean isNativeLibrary(String name) {
		return name.endsWith(".so") || name.endsWith(".dll") || name.endsWith(".dylib") || name.endsWith(".jnilib");
	}

	private static String findNativePath(File lwjglDir) {
		File nativesDir = new File(lwjglDir, "natives");
		if (!nativesDir.isDirectory()) {
			return null;
		}
		// Some LWJGL distributions place natives in platform subdirectories
		// e.g. natives/linux/, natives/windows/, natives/macos/
		File[] entries = nativesDir.listFiles();
		if (entries != null) {
			for (File entry : entries) {
				if (entry.isDirectory()) {
					String dirName = entry.getName().toLowerCase();
					if (!matchesPlatform(dirName)) {
						continue;
					}
					File[] content = entry.listFiles();
					if (content != null) {
						for (File f : content) {
							if (isNativeLibrary(f.getName())) {
								return entry.getAbsolutePath();
							}
						}
					}
				}
			}
		}
		return nativesDir.getAbsolutePath();
	}

	private static boolean matchesPlatform(String dirName) {
		switch (OS.getOs()) {
			case WINDOWS:
				return dirName.contains("windows");
			case OSX:
				return dirName.contains("osx") || dirName.contains("macos");
			case SOLARIS:
				return dirName.contains("solaris");
			case LINUX:
				return dirName.contains("linux");
			default:
				return false;
		}
	}

	public static class Logger {
		private static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("launchwrapper.log", "false"));

		public void log(String format, Object... args) {
			System.out.println("[LaunchWrapper] " + String.format(format, args));
		}

		public void logErr(String format, Object... args) {
			System.err.println("[LaunchWrapper] " + String.format(format, args));
		}

		public void logErr(String msg, Throwable e) {
			System.err.println("[LaunchWrapper] " + msg);
			e.printStackTrace(System.err);
		}

		public void logDebug(String format, Object... args) {
			if (!DEBUG) {
				return;
			}
			System.out.println("[LaunchWrapper] " + String.format(format, args));
		}
	}
}
