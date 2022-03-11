package net.fabricmc.loom.configuration.providers.minecraft;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;

import org.gradle.api.Project;

import net.fabricmc.loom.util.TinyRemapperHelper;
import net.fabricmc.tinyremapper.NonClassCopyMode;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;

public final class GluedMinecraftProvider extends MergedMinecraftProvider {
	private File minecraftClientGlueJar;
	private File minecraftServerGlueJar;

	public GluedMinecraftProvider(Project project) {
		super(project);
	}

	@Override
	protected void initFiles() {
		super.initFiles();
		minecraftClientGlueJar = file("minecraft-client-glue.jar");
		minecraftServerGlueJar = file("minecraft-server-glue.jar");
	}

	@Override
	protected void mergeJars(File clientJar, File serverJar) throws IOException {
		var mappings = getExtension().getMappingsProvider().tinyMappings;

		if (!minecraftClientGlueJar.exists()) {
			getLogger().lifecycle(":Gluing client");
			remapJar(clientJar.toPath(), minecraftClientGlueJar.toPath(), mappings, MappingsNamespace.CLIENT, MappingsNamespace.GLUE);
		}

		if (!minecraftServerGlueJar.exists()) {
			getLogger().lifecycle(":Gluing server");
			remapJar(serverJar.toPath(), minecraftServerGlueJar.toPath(), mappings, MappingsNamespace.SERVER, MappingsNamespace.GLUE);
		}

		super.mergeJars(minecraftClientGlueJar, minecraftServerGlueJar);
	}

	private void remapJar(Path input, Path output, Path mappingsPath, MappingsNamespace fromM, MappingsNamespace toM) {
		getLogger().lifecycle(":Remapping minecraft (TinyRemapper, " + fromM + " -> " + toM + ')');

		var mappings = TinyUtils.createTinyMappingProvider(mappingsPath, fromM.toString(), toM.toString());

		TinyRemapper remapper = TinyRemapper.newRemapper()
				.withMappings(mappings)
				.ignoreConflicts(false)
				.renameInvalidLocals(true)
				.rebuildSourceFilenames(true)
				.build();

		try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(output).build()) {
			remapper.readClassPath(TinyRemapperHelper.getMinecraftDependencies(getProject()));
			remapper.readInputs(input);
			remapper.apply(outputConsumer);
			outputConsumer.addNonClassFiles(input, NonClassCopyMode.FIX_META_INF, remapper);
		} catch (Exception e) {
			throw new RuntimeException("Failed to remap JARs " + input + " with mappings from " + mappings, e);
		} finally {
			remapper.finish();
		}
	}
}
