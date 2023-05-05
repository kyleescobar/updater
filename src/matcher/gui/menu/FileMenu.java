package matcher.gui.menu;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import dev.loadstone.toolbox.asm.ClassGroupRemapper;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Window;

import matcher.NameType;
import matcher.type.ClassInstance;

import matcher.type.FieldInstance;
import matcher.type.LocalClassEnv;

import matcher.type.MethodInstance;

import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.format.MappingFormat;

import matcher.Util;
import matcher.config.Config;
import matcher.gui.Gui;
import matcher.gui.Gui.SelectedFile;
import matcher.gui.menu.LoadMappingsPane.MappingsLoadSettings;
import matcher.gui.menu.LoadProjectPane.ProjectLoadSettings;
import matcher.gui.menu.SaveMappingsPane.MappingsSaveSettings;
import matcher.mapping.Mappings;
import matcher.serdes.MatchesIo;
import matcher.type.ClassEnvironment;
import matcher.type.MatchType;

public class FileMenu extends Menu {
	FileMenu(Gui gui) {
		super("File");

		this.gui = gui;

		init();
	}

	private void init() {
		MenuItem menuItem = new MenuItem("New project");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> newProject());

		getItems().add(new SeparatorMenuItem());

		menuItem = new MenuItem("Export Mapped Jar");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> exportMappedJar());

		/*menuItem = new MenuItem("Load project");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> loadProject());

		getItems().add(new SeparatorMenuItem());

		/*menuItem = new MenuItem("Load mappings");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> loadMappings(null));

		menuItem = new MenuItem("Load mappings (Enigma)");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> loadMappings(MappingFormat.ENIGMA));

		menuItem = new MenuItem("Load mappings (MCP dir)");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> loadMappings(MappingFormat.MCP));

		menuItem = new MenuItem("Save mappings");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> saveMappings(null));

		menuItem = new MenuItem("Save mappings (Enigma)");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> saveMappings(MappingFormat.ENIGMA));

		menuItem = new MenuItem("Clear mappings");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> {
			Mappings.clear(gui.getMatcher().getEnv());
			gui.onMappingChange();
		});

		getItems().add(new SeparatorMenuItem());*/

		menuItem = new MenuItem("Load matches");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> loadMatches());

		menuItem = new MenuItem("Save matches");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> saveMatches());

		getItems().add(new SeparatorMenuItem());

		menuItem = new MenuItem("Exit");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> Platform.exit());
	}

	private void newProject() {
		gui.newProject(Config.getProjectConfig(), true);
	}

	private void loadProject() {
		SelectedFile res = Gui.requestFile("Select matches file", gui.getScene().getWindow(), getMatchesLoadExtensionFilters(), true);
		if (res == null) return;

		ProjectLoadSettings newConfig = requestProjectLoadSettings();
		if (newConfig == null) return;

		gui.getMatcher().reset();
		gui.onProjectChange();

		gui.runProgressTask("Initializing files...",
				progressReceiver -> MatchesIo.read(res.path, newConfig.paths, newConfig.verifyFiles, gui.getMatcher(), progressReceiver),
				() -> gui.onProjectChange(),
				Throwable::printStackTrace);
	}

	public ProjectLoadSettings requestProjectLoadSettings() {
		Dialog<ProjectLoadSettings> dialog = new Dialog<>();
		//dialog.initModality(Modality.APPLICATION_MODAL);
		dialog.setResizable(true);
		dialog.setTitle("Project paths");
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		Node okButton = dialog.getDialogPane().lookupButton(ButtonType.OK);

		LoadProjectPane content = new LoadProjectPane(Config.getInputDirs(), Config.getVerifyInputFiles(), dialog.getOwner(), okButton);
		dialog.getDialogPane().setContent(content);
		dialog.setResultConverter(button -> button == ButtonType.OK ? content.createConfig() : null);

		ProjectLoadSettings settings = dialog.showAndWait().orElse(null);

		if (settings != null && !settings.paths.isEmpty()) {
			Config.setInputDirs(settings.paths);
			Config.setVerifyInputFiles(settings.verifyFiles);
			Config.saveAsLast();
		}

		return settings;
	}

	private void loadMappings(MappingFormat format) {
		Window window = gui.getScene().getWindow();
		Path file;

		if (format == null || format.hasSingleFile()) {
			SelectedFile res = Gui.requestFile("Select mapping file", window, getMappingLoadExtensionFilters(), true); // TODO: pre-select format if non-null
			if (res == null) return; // aborted

			file = res.path;
			format = getFormat(res.filter.getDescription());
		} else {
			file = Gui.requestDir("Select mapping dir", window);
		}

		if (file == null) return;

		try {
			List<String> namespaces = MappingReader.getNamespaces(file, format);

			Dialog<MappingsLoadSettings> dialog = new Dialog<>();
			//dialog.initModality(Modality.APPLICATION_MODAL);
			dialog.setResizable(true);
			dialog.setTitle("Import Settings");
			dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

			LoadMappingsPane content = new LoadMappingsPane(namespaces);
			dialog.getDialogPane().setContent(content);
			dialog.setResultConverter(button -> button == ButtonType.OK ? content.getSettings() : null);
			final MappingFormat loadFormat = format;

			Optional<MappingsLoadSettings> result = dialog.showAndWait();
			if (!result.isPresent()) return;

			MappingsLoadSettings settings = result.get();
			ClassEnvironment env = gui.getMatcher().getEnv();

			Mappings.load(file, loadFormat,
					settings.nsSource, settings.nsTarget,
					settings.fieldSource, settings.fieldTarget,
					(settings.a ? env.getEnvA() : env.getEnvB()),
					settings.replace);
		} catch (IOException e) {
			e.printStackTrace();
			gui.showAlert(AlertType.ERROR, "Load error", "Error while loading mappings", e.toString());
			return;
		}

		gui.onMappingChange();
	}

	private static List<ExtensionFilter> getMappingLoadExtensionFilters() {
		MappingFormat[] formats = MappingFormat.values();
		List<ExtensionFilter> ret = new ArrayList<>(formats.length + 2);
		List<String> supportedExtensions = new ArrayList<>(formats.length);

		for (MappingFormat format : formats) {
			if (format.hasSingleFile()) supportedExtensions.add(format.getGlobPattern());
		}

		ret.add(new FileChooser.ExtensionFilter("All supported", supportedExtensions));
		ret.add(new FileChooser.ExtensionFilter("Any", "*.*"));

		for (MappingFormat format : formats) {
			if (format.hasSingleFile()) ret.add(new FileChooser.ExtensionFilter(format.name, format.getGlobPattern()));
		}

		return ret;
	}

	private void saveMappings(MappingFormat format) {
		Window window = gui.getScene().getWindow();
		Path path;

		if (format == null || format.hasSingleFile()) {
			FileChooser fileChooser = new FileChooser();
			fileChooser.setTitle("Save mapping file");

			for (MappingFormat f : MappingFormat.values()) {
				if (f.hasSingleFile()) {
					FileChooser.ExtensionFilter filter = new FileChooser.ExtensionFilter(f.name, "*."+f.fileExt);
					fileChooser.getExtensionFilters().add(filter);

					if (f == format) fileChooser.setSelectedExtensionFilter(filter);
				}
			}

			File file = fileChooser.showSaveDialog(window);
			if (file == null) return;

			path = file.toPath();
			format = getFormat(fileChooser.getSelectedExtensionFilter().getDescription());
		} else {
			path = Gui.requestDir("Save mapping dir", window);
			if (path == null) return;

			if (Files.exists(path) && !isDirEmpty(path)) { // reusing existing dir, clear out after confirmation
				if (!gui.requestConfirmation("Save Confirmation", "Replace existing data", "The selected save location is not empty.\nDo you want to clear and reuse it?")) return;

				try {
					if (!Util.clearDir(path, file -> !Files.isDirectory(file) && !file.getFileName().toString().endsWith(".mapping"))) {
						gui.showAlert(AlertType.ERROR, "Save error", "Error while preparing save location", "The target directory contains non-mapping files.");
						return;
					}
				} catch (IOException e) {
					e.printStackTrace();
					gui.showAlert(AlertType.ERROR, "Save error", "Error while preparing save location", e.getMessage());
					return;
				}
			}
		}

		if (format == null) {
			format = getFormat(path);
			if (format == null) throw new IllegalStateException("mapping format detection failed");

			if (format.hasSingleFile()) {
				path = path.resolveSibling(path.getFileName().toString()+"."+format.fileExt);
			}
		}

		if (Files.exists(path)) {
			if (Files.isDirectory(path) != !format.hasSingleFile()) {
				gui.showAlert(AlertType.ERROR, "Save error", "Invalid file selection", "The selected file is of the wrong type.");
				return;
			}
		}

		Dialog<MappingsSaveSettings> dialog = new Dialog<>();
		//dialog.initModality(Modality.APPLICATION_MODAL);
		dialog.setResizable(true);
		dialog.setTitle("Mappings export settings");
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		SaveMappingsPane content = new SaveMappingsPane(format.hasNamespaces);
		dialog.getDialogPane().setContent(content);
		dialog.setResultConverter(button -> button == ButtonType.OK ? content.getSettings() : null);
		final Path savePath = path;
		final MappingFormat saveFormat = format;

		dialog.showAndWait().ifPresent(settings -> {
			ClassEnvironment env = gui.getMatcher().getEnv();

			try {
				if (Files.exists(savePath)) {
					Files.deleteIfExists(savePath);
				}

				if (!Mappings.save(savePath, saveFormat, (settings.a ? env.getEnvA() : env.getEnvB()),
						settings.nsTypes, settings.nsNames, settings.verbosity, settings.forAnyInput, settings.fieldsFirst)) {
					gui.showAlert(AlertType.WARNING, "Mapping save warning", "No mappings to save", "There are currently no names mapped to matched classes, so saving was aborted.");
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});
	}

	private static boolean isDirEmpty(Path dir) {
		try (Stream<Path> stream = Files.list(dir)) {
			return !stream.anyMatch(ignore -> true);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	private static MappingFormat getFormat(Path file) {
		if (Files.isDirectory(file)) return MappingFormat.ENIGMA;

		String name = file.getFileName().toString().toLowerCase(Locale.ENGLISH);

		for (MappingFormat format : MappingFormat.values()) {
			if (format.hasSingleFile()
					&& name.endsWith(format.fileExt)
					&& name.length() > format.fileExt.length()
					&& name.charAt(name.length() - 1 - format.fileExt.length()) == '.') {
				return format;
			}
		}

		return null;
	}

	private static MappingFormat getFormat(String selectedName) {
		for (MappingFormat format : MappingFormat.values()) {
			if (format.name.equals(selectedName)) return format;
		}

		return null;
	}

	private void loadMatches() {
		SelectedFile res = Gui.requestFile("Select matches file", gui.getScene().getWindow(), getMatchesLoadExtensionFilters(), true);
		if (res == null) return;

		MatchesIo.read(res.path, null, false, gui.getMatcher(), progress -> { });
		gui.onMatchChange(EnumSet.allOf(MatchType.class));
	}

	private static List<ExtensionFilter> getMatchesLoadExtensionFilters() {
		return Arrays.asList(new FileChooser.ExtensionFilter("Matches", "*.match"));
	}

	private void saveMatches() {
		SelectedFile res = Gui.requestFile("Save matches file", gui.getScene().getWindow(), Arrays.asList(new FileChooser.ExtensionFilter("Matches", "*.match")), false);
		if (res == null) return;

		Path path = res.path;

		if (!path.getFileName().toString().toLowerCase(Locale.ENGLISH).endsWith(".match")) {
			path = path.resolveSibling(path.getFileName().toString()+".match");
		}

		try {
			if (Files.isDirectory(path)) {
				gui.showAlert(AlertType.ERROR, "Save error", "Invalid file selection", "The selected file is a directory.");
			} else if (Files.exists(path)) {
				Files.deleteIfExists(path);
			}

			if (!MatchesIo.write(gui.getMatcher(), path)) {
				gui.showAlert(AlertType.WARNING, "Matches save warning", "No matches to save", "There are currently no matched classes, so saving was aborted.");
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
	}

	private void exportMappedJar() {
		System.out.println("Remapping and exporting matches to jar file.");

		SelectedFile res = Gui.requestFile("Remapped output file", gui.getScene().getWindow(), Arrays.asList(new FileChooser.ExtensionFilter("Jar", "*.jar")), false);
		if(res == null) return;

		Path path = res.path;
		File outputFile = path.toFile();

		File inputFile = Config.getProjectConfig().getPathsA().get(0).toFile();

		try {
			if(outputFile.isDirectory()) {
				gui.showAlert(AlertType.ERROR, "Save Error", "Invalid file selection", "The selected file is a directory.");
			} else if(outputFile.exists()) {
				Files.deleteIfExists(outputFile.toPath());
			}

			HashMap<String, String> mappings = new HashMap<>();

			ClassEnvironment env = gui.getMatcher().getEnv();

			List<ClassInstance> classes = new ArrayList<>(env.getClassesA());
			for(ClassInstance cls : classes) {
				if(!cls.hasMatch()) {
					mappings.put(cls.getName(), cls.getName(NameType.TMP_PLAIN));
				}
			}

			if(classes.isEmpty()) return;

			classes.sort(new Comparator<ClassInstance>() {
				@Override
				public int compare(ClassInstance a, ClassInstance b) {
					if(a.getEnv() != b.getEnv()) {
						return a.getEnv() == env.getEnvA() ? -1 : 1;
					}
					return a.getId().compareTo(b.getId());
				}
			});

			LocalClassEnv envA = env.getEnvA();

			for(ClassInstance cls : classes) {
				assert !cls.isShared();
				if(cls.hasMatch()) {
					mappings.put(cls.getName(), cls.getMatch().getName());

					for(MethodInstance method : cls.getMethods()) {
						if(method.hasMatch()) {
							mappings.put(method.getOwner().getName()+"."+method.getName()+method.getDesc(), method.getMatch().getName());
						}
					}

					for(FieldInstance field : cls.getFields()) {
						if(field.hasMatch()) {
							mappings.put(field.getOwner().getName()+"."+field.getName(), field.getMatch().getName());
						}
					}
				}
			}


			System.out.println("Successfully computed "+mappings.size()+" mappings.");

			ClassGroupRemapper remapper = new ClassGroupRemapper(
					inputFile,
					outputFile,
					ClassEnvironment.getStaticManagerA(),
					mappings
			);
			remapper.apply();
		} catch (Exception e) {
			Alert alert = new Alert(AlertType.ERROR);
			alert.setTitle("Remapping Error");
			alert.setHeaderText("Thrown Exception");
			alert.setContentText("Updater failed to remap and save jar. See the error below.");
			alert.getDialogPane().getStylesheets().add(Config.getTheme().getUrl().toExternalForm());

			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			String exceptionText = sw.toString();

			Label label = new Label("The exception stacktrace was:");

			TextArea textArea = new TextArea(exceptionText);
			textArea.setEditable(false);
			textArea.setWrapText(true);

			textArea.setMaxWidth(Double.MAX_VALUE);
			textArea.setMaxHeight(Double.MAX_VALUE);
			GridPane.setVgrow(textArea, Priority.ALWAYS);
			GridPane.setHgrow(textArea, Priority.ALWAYS);

			GridPane expContent = new GridPane();
			expContent.setMaxWidth(Double.MAX_VALUE);
			expContent.add(label, 0, 0);
			expContent.add(textArea, 0, 1);

			alert.getDialogPane().setExpandableContent(expContent);

			alert.showAndWait();
		}
	}

	private String getStackTraceString(Exception e) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		pw.flush();
		return sw.toString();
	}

	private final Gui gui;
}
