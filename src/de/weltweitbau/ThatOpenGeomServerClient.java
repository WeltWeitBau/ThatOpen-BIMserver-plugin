package de.weltweitbau;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.text.MessageFormat;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Set;

import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.DateUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.bimserver.plugins.renderengine.RenderEngineException;
import org.bimserver.shared.exceptions.PluginException;
import org.ifcopenshell.IfcGeomServerClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThatOpenGeomServerClient extends IfcGeomServerClient {
	private static final Logger LOGGER = LoggerFactory.getLogger(ThatOpenGeomServerClient.class);
	
	private static String VERSION = "ThatOpen/engine_web-ifc-0.0.54-0";
	
	private GregorianCalendar buildDateTime;

	public ThatOpenGeomServerClient(String version, String commitSha, Path homeDir) throws RenderEngineException {
		super(getExecutableFromUrl(version, commitSha, homeDir));
		
		try {
			FileTime fileTime = (FileTime) Files.getAttribute(getExecutableFilename(), "creationTime");
			buildDateTime = new GregorianCalendar();
			buildDateTime.setTimeInMillis(fileTime.toMillis());
		} catch (Exception e) {
			LOGGER.error("couldnt get buildDateTime", e);
		}
	}

	private static Path getExecutableFromUrl(String version, String commitSha, Path homeDir) throws RenderEngineException {
		try {
			String urlPattern = "https://github.com/WeltWeitBau/ThatOpenIfcGeomServer/releases/download/{0}/ThatOpenIfcGeomServer-{0}-{1}{2}";
			String tag = version + "-" + commitSha;
			String platform = getPlatform();
			String extension = getExecutableExtension();
			String url = MessageFormat.format(urlPattern, tag, platform, extension);
			
			String baseName = new File(new URL(url).getPath()).getName();
			Path executableFilename = homeDir.resolve(baseName);
			
			if (!Files.exists(executableFilename)) {
				LOGGER.info(String.format("Downloading from %s", url));
				Files.createDirectories(executableFilename.getParent());
				LOGGER.info(String.format("Downloading to %s", executableFilename.toString()));
				
				try (CloseableHttpClient httpClient = HttpClients.custom().useSystemProperties().build()) {
					HttpGet httpGet = new HttpGet(url);
					try (CloseableHttpResponse httpResponse = httpClient.execute(httpGet)) {
						if (httpResponse.getStatusLine().getStatusCode() == 200) {
							Header lastModified = httpResponse.getFirstHeader("Last-Modified");
							LOGGER.info("IfcOpenShell Last Modified: " + lastModified.getValue());
							GregorianCalendar buildDateTime = new GregorianCalendar();
							buildDateTime.setTime(DateUtils.parseDate(lastModified.getValue()));

							Files.copy(httpResponse.getEntity().getContent(), executableFilename);
							
							Files.setAttribute(executableFilename, "creationTime", FileTime.fromMillis(buildDateTime.getTimeInMillis()));
						} else {
							LOGGER.error(httpResponse.getStatusLine().toString());
							LOGGER.error("File not found " + url);
							throw new RenderEngineException("File not found " + url);
						}
					}
				}
				
				try {
					Set<PosixFilePermission> permissions = new HashSet<>();
					permissions.add(PosixFilePermission.OWNER_EXECUTE);
					permissions.add(PosixFilePermission.OWNER_READ);
					permissions.add(PosixFilePermission.OWNER_WRITE);
					Files.setPosixFilePermissions(executableFilename, permissions);
				} catch (Exception e) {}
			}
			
			return executableFilename;
		} catch (IOException | PluginException e) {
			throw new RenderEngineException(e);
		}
	}
	
	@Override
	public GregorianCalendar getBuildDateTime() {
		return buildDateTime;
	}
	
	public String getVersion() {
		return VERSION;
	}
}
