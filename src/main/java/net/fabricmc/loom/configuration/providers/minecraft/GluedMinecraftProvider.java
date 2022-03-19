/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fabricmc.loom.configuration.providers.minecraft;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.ConfigContext;
import net.fabricmc.loom.util.TinyRemapperHelper;
import net.fabricmc.stitch.commands.CommandFixNesting;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.NonClassCopyMode;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;

public final class GluedMinecraftProvider extends MergedMinecraftProvider {
	private File minecraftClientGlueJar;
	private File minecraftServerGlueJar;

	public GluedMinecraftProvider(ConfigContext configContext) {
		super(configContext);
	}

	@Override
	protected void initFiles() {
		super.initFiles();
		minecraftClientGlueJar = file("minecraft-client-glue.jar");
		minecraftServerGlueJar = file("minecraft-server-glue.jar");
	}

	@Override
	protected void mergeJars(File clientJar, File serverJar) throws IOException {
		Path mappings = getExtension().getMappingConfiguration().tinyMappings;

		if (!minecraftClientGlueJar.exists()) {
			getLogger().lifecycle(":Gluing client");
			remapJar(clientJar.toPath(), minecraftClientGlueJar.toPath(), mappings, MappingsNamespace.CLIENT, MappingsNamespace.GLUE);
		}

		if (!minecraftServerGlueJar.exists()) {
			getLogger().lifecycle(":Gluing server");
			remapJar(serverJar.toPath(), minecraftServerGlueJar.toPath(), mappings, MappingsNamespace.SERVER, MappingsNamespace.GLUE);
		}

		super.mergeJars(minecraftClientGlueJar, minecraftServerGlueJar);

		CommandFixNesting.run(getMergedJar().toFile());
	}

	private void remapJar(Path input, Path output, Path mappingsPath, MappingsNamespace fromM, MappingsNamespace toM) {
		getLogger().lifecycle(":Remapping minecraft (TinyRemapper, " + fromM + " -> " + toM + ')');

		IMappingProvider mappings = TinyUtils.createTinyMappingProvider(mappingsPath, fromM.toString(), toM.toString());

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
