package matcher.srcprocess;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.jar.Manifest;

import org.jetbrains.java.decompiler.main.decompiler.BaseDecompiler;
import org.jetbrains.java.decompiler.main.decompiler.PrintStreamLogger;
import org.jetbrains.java.decompiler.main.extern.IContextSource;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;

import matcher.NameType;
import matcher.type.ClassFeatureExtractor;
import matcher.type.ClassInstance;

public class Quiltflower implements Decompiler {
	@Override
	public String decompile(ClassInstance cls, ClassFeatureExtractor env, NameType nameType) {
		// invoke Qf with on-demand class lookup into matcher's state and string based output
		Map<String, Object> properties = new HashMap<>(IFernflowerPreferences.DEFAULTS);
		properties.put(IFernflowerPreferences.REMOVE_BRIDGE, "0");
		properties.put(IFernflowerPreferences.REMOVE_SYNTHETIC, "0");
		properties.put(IFernflowerPreferences.INDENT_STRING, "\n");
		properties.put(IFernflowerPreferences.THREADS, String.valueOf(Math.max(1, Runtime.getRuntime().availableProcessors() - 2)));
		properties.put(IFernflowerPreferences.LOG_LEVEL, "WARN");

		try (ResultSaver resultSaver = new ResultSaver()) {
			BaseDecompiler decompiler = new BaseDecompiler(resultSaver, properties, new PrintStreamLogger(System.out));
			decompiler.addSource(new ClassSource(cls, env, nameType, resultSaver));
			decompiler.decompileContext();
			return resultSaver.results.get(cls.getName(nameType));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static class ClassSource implements IContextSource {
		private final ClassInstance cls;
		private final NameType nameType;
		private final ResultSaver resultSaver;

		ClassSource(ClassInstance cls, ClassFeatureExtractor env, NameType nameType, ResultSaver resultSaver) {
			this.cls = cls;
			this.nameType = nameType;
			this.resultSaver = resultSaver;
		}

		@Override
		public IOutputSink createOutputSink(IResultSaver saver) {
			return resultSaver;
		}

		@Override
		public String getName() {
			return "Matcher QF Class Source";
		}

		private static String getName(ClassInstance cls, NameType nameType) {
			return Objects.requireNonNullElse(cls.getName(nameType), cls.getName());
		}

		private final Map<String, byte[]> cache = new HashMap<>();

		@Override
		public Entries getEntries() {
			List<Entry> entries = new ArrayList<>();
			String name = getName(cls, nameType);
			entries.add(Entry.parse(name));
			cache.put(name, cls.serialize(nameType));

			for (ClassInstance innerCls : cls.getInnerClasses()) {
				String innerName = getName(innerCls, nameType);
				entries.add(Entry.parse(innerName));
				cache.put(innerName, innerCls.serialize(nameType));
			}

			return new Entries(entries, List.of(), List.of());
		}

		@Override
		public InputStream getInputStream(String resource) throws IOException {
			resource = resource.substring(0, resource.length() - ".class".length());

			if (cache.get(resource) != null) {
				return new ByteArrayInputStream(cache.get(resource));
			} else {
				throw new IOException("Resource not found: "+resource);
			}
		}
	}

	private static class ResultSaver implements IResultSaver, IContextSource.IOutputSink {
		@Override
		public void saveFolder(String path) { }
		@Override
		public void copyFile(String source, String path, String entryName) { }
		@Override
		public void createArchive(String path, String archiveName, Manifest manifest) { }
		@Override
		public void saveDirEntry(String path, String archiveName, String entryName) { }
		@Override
		public void copyEntry(String source, String path, String archiveName, String entry) { }
		@Override
		public void closeArchive(String path, String archiveName) { }
		@Override
		public void saveClassEntry(String path, String archiveName, String qualifiedName, String entryName, String content) { }

		@Override
		public void saveClassFile(String path, String qualifiedName, String entryName, String content, int[] mapping) {
			if (DEBUG) System.out.printf("saveClassFile(%s, %s, %s, %s, %s)%n", path, qualifiedName, entryName, content, Arrays.toString(mapping));

			results.put(qualifiedName, content);
		}

		Map<String, String> results = new HashMap<>();

		@Override
		public void begin() { }

		@Override
		public void acceptClass(String qualifiedName, String fileName, String content, int[] mapping) {
			if (DEBUG) System.out.printf("acceptClass(%s, %s, %s, %s)%n", qualifiedName, fileName, content, Arrays.toString(mapping));

			results.put(qualifiedName, content);
		}

		@Override
		public void acceptDirectory(String directory) { }

		@Override
		public void acceptOther(String path) { }

		@Override
		public void close() throws IOException { }
	}

	private static final boolean DEBUG = false;
}
