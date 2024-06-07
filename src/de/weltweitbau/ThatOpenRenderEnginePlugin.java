package de.weltweitbau;

import java.io.IOException;
import java.nio.file.Path;

import org.bimserver.models.store.BooleanType;
import org.bimserver.models.store.ObjectDefinition;
import org.bimserver.models.store.ParameterDefinition;
import org.bimserver.models.store.PrimitiveDefinition;
import org.bimserver.models.store.PrimitiveEnum;
import org.bimserver.models.store.StoreFactory;
import org.bimserver.models.store.StringType;
import org.bimserver.plugins.PluginConfiguration;
import org.bimserver.plugins.PluginContext;
import org.bimserver.plugins.renderengine.RenderEngine;
import org.bimserver.plugins.renderengine.RenderEngineException;
import org.bimserver.plugins.renderengine.VersionInfo;
import org.bimserver.shared.exceptions.PluginException;
import org.ifcopenshell.IfcGeomServerClient;
import org.ifcopenshell.IfcOpenShellEngine;
import org.ifcopenshell.IfcOpenShellEnginePlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThatOpenRenderEnginePlugin extends IfcOpenShellEnginePlugin {
	private static final Logger LOGGER = LoggerFactory.getLogger(ThatOpenRenderEnginePlugin.class);
	
	private static final String DEFAULT_VERSION = "0.0.54-0";
	private static final String VERSION_SETTING = "branch";
	private static final String DEFAULT_COMMIT_SHA = "d226204";
	private static final String COMMIT_SHA_SETTING = "commitsha";
	private static final String CALCULATE_QUANTITIES_SETTING = "calculatequantities";
	private static final String APPLY_LAYER_SETS = "applylayersets";
	private static final String DISABLE_OPENING_SUBTRACTIONS = "disableopeningsubtrations";
	
	private Path executableFilename;
	private VersionInfo versionInfo;
	private boolean calculateQuantities = true;
	private boolean applyLayerSets = true;
	private boolean disableOpeningSubtractions = false;
	
	@Override
	public void init(PluginContext pluginContext, PluginConfiguration systemSettings) throws PluginException {
		// Make sure an executable is downloaded before invoking the plug-in using multiple threads.
		// This also checks whether the version of the executable matches the java source.

		if (systemSettings != null) {
			calculateQuantities = systemSettings.getBoolean(CALCULATE_QUANTITIES_SETTING, false);
			applyLayerSets = systemSettings.getBoolean(APPLY_LAYER_SETS, false);
			disableOpeningSubtractions = systemSettings.getBoolean(DISABLE_OPENING_SUBTRACTIONS, false);
		}
		
		String commitSha = getSetting(COMMIT_SHA_SETTING, DEFAULT_COMMIT_SHA, systemSettings);
		String version = getSetting(VERSION_SETTING, DEFAULT_VERSION, systemSettings);
		
		try (IfcGeomServerClient test = new ThatOpenGeomServerClient(version, commitSha, pluginContext.getTempDir())) {
			executableFilename = test.getExecutableFilename();
			versionInfo = new VersionInfo(version, commitSha, test.getVersion(), test.getBuildDateTime(), IfcGeomServerClient.getPlatform());
		}
		
		LOGGER.info("Using " + executableFilename);
	}
	
	private String getSetting(String setting, String defaultValue, PluginConfiguration systemSettings) {
		if (systemSettings != null && systemSettings.getString(setting) != null && !systemSettings.getString(setting).trim().contentEquals("")) {
			return systemSettings.getString(setting);
		}
		
		return defaultValue;
	}
	
	@Override
	public RenderEngine createRenderEngine(PluginConfiguration pluginConfiguration, String schema) throws RenderEngineException {
		try {
			return new IfcOpenShellEngine(executableFilename, calculateQuantities, applyLayerSets, disableOpeningSubtractions);
		} catch (IOException e) {
			throw new RenderEngineException(e);
		}
	}
	
	@Override
	public VersionInfo getVersionInfo() {
		return versionInfo;
	}
	
	@Override
	public ObjectDefinition getSystemSettingsDefinition() {
		ObjectDefinition settings = StoreFactory.eINSTANCE.createObjectDefinition();
		
		PrimitiveDefinition stringType = StoreFactory.eINSTANCE.createPrimitiveDefinition();
		stringType.setType(PrimitiveEnum.STRING);

		PrimitiveDefinition booleanType = StoreFactory.eINSTANCE.createPrimitiveDefinition();
		booleanType.setType(PrimitiveEnum.BOOLEAN);
		
		StringType defaultSha = StoreFactory.eINSTANCE.createStringType();
		defaultSha.setValue(DEFAULT_COMMIT_SHA);
		
		StringType defaultVersion = StoreFactory.eINSTANCE.createStringType();
		defaultVersion.setValue(DEFAULT_VERSION);

		BooleanType defaultTrue = StoreFactory.eINSTANCE.createBooleanType();
		defaultTrue.setValue(true);

		BooleanType defaultFalse = StoreFactory.eINSTANCE.createBooleanType();
		defaultFalse.setValue(false);
		
		ParameterDefinition versionParameter = StoreFactory.eINSTANCE.createParameterDefinition();
		versionParameter.setIdentifier(VERSION_SETTING);
		versionParameter.setName("engine_web-ifc version");
		versionParameter.setDescription("Version of ThatOpen/engine_web-ifc, this overrules the default for the currently installated plugin");
		versionParameter.setType(stringType);
		versionParameter.setRequired(false);
		versionParameter.setDefaultValue(defaultSha);

		ParameterDefinition commitShaParameter = StoreFactory.eINSTANCE.createParameterDefinition();
		commitShaParameter.setIdentifier(COMMIT_SHA_SETTING);
		commitShaParameter.setName("Commit Sha");
		commitShaParameter.setDescription("Commit sha of ThatOpenIfcGeomServer binary, this overrules the default for the currently installated plugin");
		commitShaParameter.setType(stringType);
		commitShaParameter.setRequired(false);
		commitShaParameter.setDefaultValue(defaultSha);

		ParameterDefinition calculateQuantities = StoreFactory.eINSTANCE.createParameterDefinition();
		calculateQuantities.setIdentifier(CALCULATE_QUANTITIES_SETTING);
		calculateQuantities.setName("Calculate Quantities");
		calculateQuantities.setDescription("Calculates volumes and areas, Takes a bit more time (about 15%)");
		calculateQuantities.setType(booleanType);
		calculateQuantities.setRequired(false);
		calculateQuantities.setDefaultValue(defaultTrue);

		ParameterDefinition applyLayerSets = StoreFactory.eINSTANCE.createParameterDefinition();
		applyLayerSets.setIdentifier(APPLY_LAYER_SETS);
		applyLayerSets.setName("Apply Layer Sets");
		applyLayerSets.setDescription("Splits certain objects into several layers, depending on the model can take about 10x more processing time, and results in more geometry");
		applyLayerSets.setType(booleanType);
		applyLayerSets.setRequired(false);
		applyLayerSets.setDefaultValue(defaultTrue);

		ParameterDefinition disableOpeningSubtractions = StoreFactory.eINSTANCE.createParameterDefinition();
		disableOpeningSubtractions.setIdentifier(DISABLE_OPENING_SUBTRACTIONS);
		disableOpeningSubtractions.setName("Disable Opening Subtractions");
		disableOpeningSubtractions.setDescription("Omits subtractions of voiding opening elements form the voided elements.");
		disableOpeningSubtractions.setType(booleanType);
		disableOpeningSubtractions.setRequired(false);
		disableOpeningSubtractions.setDefaultValue(defaultFalse);
		
		settings.getParameters().add(commitShaParameter);
		settings.getParameters().add(calculateQuantities);
		settings.getParameters().add(applyLayerSets);
		settings.getParameters().add(disableOpeningSubtractions);
		return settings;
	}
}
