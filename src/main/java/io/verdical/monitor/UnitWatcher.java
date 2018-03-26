// Copyright 2018 Verdical, Inc.

package io.verdical.monitor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class UnitWatcher {
	
	private static final Logger logger = Logger.getLogger(UnitWatcher.class.getName());

	// I have seen it take up to half an hour for an unplugged Photon to register in the Particle cloud.
	// In production, don't scan more often than once every 30 minutes.
	private static int minutesBetweenDeviceScans = 30;
	private static int minutesAllowedOffline = 24 * 60;

	private static int minutesBetweenMessageScans = 15;

	private String accountName;
	private String accountPw;

	private final boolean isDebug = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().toString()
			.indexOf("jdwp") >= 0;;
	private final int sleepFactor = 2; // increase for slower computers.
	private final int sleepSeconds = 10 * sleepFactor;

	private WebDriver driver;
	private WebDriverWait wait;
	
    private Map<String, LocalDateTime> unitLastConnected = new HashMap<String, LocalDateTime>();

	private final String logFileName = getLogFileName();
	private final SimpleDateFormat logDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");
	private final LocalDateTime serverStarted = LocalDateTime.now();;

	public UnitWatcher(String[] args) throws Exception {
		log("server starting up.");
		if (isDebug) {
			minutesBetweenDeviceScans = 5;
			minutesBetweenMessageScans = 1;
			minutesAllowedOffline = 3;
		}
		logger.info("Logging to " + logFileName);
		unitLastConnected.put("bobcat_pizza", null);
		unitLastConnected.put("CatDev", null);
		unitLastConnected.put("JARDINIERE", null);
		unitLastConnected.put("MCDS-ver_2", null);
		unitLastConnected.put("PMH-1", null);
		unitLastConnected.put("Saha_2", null);
		unitLastConnected.put("Saha-1", null);
		unitLastConnected.put("test-4a", null);
		unitLastConnected.put("test-8EJV", null);
		unitLastConnected.put("TSWE", null);
	}

	private String getHomeDir() throws Exception {
		String d = System.getProperty("user.home");
		if (d == null || d.length() == 0) {
			throw new IllegalArgumentException(
					"Unable to determine user.home directory from System.getProperty(\"user.home\")");
		}
		return d;
	}

	private String getCredentialsFileName() throws Exception {
		return getHomeDir() + File.separator + "Documents" + File.separator + "particle-credentials.txt";
	}

	private String getLogFileName() throws Exception {
		String d = getHomeDir();
		String path = d + File.separator + "Documents" + File.separator + "Github";
		File dir = new File(path);
		if (!dir.exists()) {
			throw new IllegalArgumentException(
					"No such directory : " + path);
		}
		// Append to existing log file to get better long term data.
		String fn = path + File.separator + "particle_log.txt";
		return fn;
    }

	private void log(String s) {
		logger.info(s);
		String d = logDateFormat.format(new java.util.Date());
		try {
			String msg = d + "\t" + s;
			System.out.println(msg);
			FileWriter fstream = new FileWriter(logFileName, true);
			fstream.write(msg + System.getProperty("line.separator"));
			fstream.close();
		} catch (Exception e) {
			System.out
					.println(d + "\tError writing log file : " + logFileName + "\t" + e.toString());
		}
	}

	private void login() throws Exception {
		driver.get("https://build.particle.io/");
		Thread.sleep(2 * 1000);
		WebElement name = driver.findElement(By.name("username"));
		name.sendKeys(accountName);
		Thread.sleep(2 * 1000);
		WebElement pw = driver.findElement(By.name("password"));
		pw.sendKeys(accountPw);
	}

	private void goToTool(String className) throws Exception {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(By.className(className)));
			WebElement devicesEl = driver.findElement(By.className(className));
			devicesEl.click();
		} catch (Exception e) {
			throw new Exception("Error waiting for elementToBeClickable By.className(\"" + className + "\"). Are you logged into build.particle.io?");
		}
	}

	private void goToDevices() throws Exception {
		goToTool("ion-pinpoint");
		try {
			wait.until(ExpectedConditions.elementToBeClickable(By.className("newcore")));
		} catch (Exception e) {
			throw new Exception("Error waiting for elementToBeClickable By.className(\"newcore\"). Are the 'Particle Devices' visible?");
		}
	}

	// Use if code is needed to navigate to Particle console and record 'pump on' messages.
	@SuppressWarnings("unused")
	private void recordPumpMessages() throws Exception {
	    LocalDateTime wakeUp = LocalDateTime.now().plusHours(1).withMinute(0);
		Duration duration = Duration.between(wakeUp, LocalDateTime.now());
		Thread.sleep(duration.toMinutes() * 60 * 1000);

		for (int seconds = 0; seconds < 300; seconds += 10) {
			// loop until all "pump on" messages are found.
		}
	}

	// java.time.Date parsing works differently from java.util.Date.
	// Run some tests to figure it out.
	@SuppressWarnings("unused")
	private void testParsing() {
		String[] digits = {"8", "08"};
		for (String hour : digits) {
			String[] days = {"4", "10"};
			for (String day : days) {
				String input = "March " + day + "th at " + hour + ":50:11 AM";
				// March 25th at 9:25:36 am
				try {
					toDate(input);
					System.out.println("Pass : " + input);
				} catch (Exception e) {
					System.out.println("FAIL : " + input + " : " + e.getMessage());
				}
			}
		}
	}

	final private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'hh:mm:ss' 'a");

	private LocalDateTime toDate(String particleFormat) throws Exception {
		String input = particleFormat;
		particleFormat = particleFormat.replace(" at ", "T");
		particleFormat = particleFormat.replace("January ", "01-");
		particleFormat = particleFormat.replace("February ", "02-");
		particleFormat = particleFormat.replace("March ", "03-");
		particleFormat = particleFormat.replace("April ", "04-");
		particleFormat = particleFormat.replace("May ", "05-");
		particleFormat = particleFormat.replace("June ", "06-");
		particleFormat = particleFormat.replace("July ", "07-");
		particleFormat = particleFormat.replace("August ", "08-");
		particleFormat = particleFormat.replace("September ", "09-");
		particleFormat = particleFormat.replace("October ", "10-");
		particleFormat = particleFormat.replace("November ", "11-");
		particleFormat = particleFormat.replace("December ", "12-");
		particleFormat = particleFormat.replace("nd", "");
		particleFormat = particleFormat.replace("st", "");
		particleFormat = particleFormat.replace("th", "");
		particleFormat = particleFormat.replace("rd", "");
		particleFormat = particleFormat.replace("am", "AM");
		particleFormat = particleFormat.replace("pm", "PM");
		particleFormat = LocalDateTime.now().getYear() + "-" + particleFormat;
		if (particleFormat.charAt(9) == 'T') {
			String prefix = particleFormat.substring(0, 8);
			String suffix = particleFormat.substring(8);
			particleFormat = prefix + "0" + suffix;
		}
		if (particleFormat.charAt(12) == ':') {
			particleFormat = particleFormat.replace("T", "T0");
		}
		try {
			return LocalDateTime.parse(particleFormat, formatter);
		} catch (Exception e) {
			System.out.println("input:          " + input);
			System.out.println("particleFormat: " + particleFormat);
			System.out.println(formatter);
			throw e;
		}
	}

	final private DateTimeFormatter googleSheetsDateFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
	private Map<String, String> dataAlreadyLogged = new HashMap<String, String>();

	private void monitorMessages(String deviceName) throws Exception {
		List<WebElement> messages = driver.findElements(By.className("event-data"));
		for (WebElement myElement : messages) {
			try {
				WebElement parent = myElement.findElement(By.xpath(".."));
				WebElement timestamp = parent.findElement(By.className("event-timestamp"));
				if (dataAlreadyLogged.get(timestamp.getText()) == null) {
					WebElement eventname = parent.findElement(By.className("event-name"));
					LocalDateTime d = toDate(timestamp.getText());
					log(eventname.getText()
							+ "\t" + myElement.getText()
							+ "\t" + googleSheetsDateFormat.format(d)
							+ "\t" + deviceName);
					dataAlreadyLogged.put(timestamp.getText(), myElement.getText());
				}
			} catch (Exception e) {
				// Pain in the ass browser(s)...
				if (!e.getClass().equals(StaleElementReferenceException.class)) {
					log("monitorMessages : " + e);
				}
			}
		}
	}

	private void watchUnits() throws Exception {
		goToDevices();
	    LocalDateTime currentTime = LocalDateTime.now();
	    List<String> unitsAlive = new ArrayList<String>();

		WebElement listEl = driver.findElement(By.className("cores"));
		List<WebElement> photons = listEl.findElements(By.className("breathing"));
		for (WebElement myElement : photons) {
			// div > div > li
			WebElement greatgrandparent = myElement.findElement(By.xpath("../../.."));
			WebElement title = greatgrandparent.findElement(By.className("title"));
			unitsAlive.add(title.getText());
		}
		for (String name : unitsAlive) {
			unitLastConnected.put(name, currentTime);
		}
	    LocalDateTime limit = LocalDateTime.now().minusMinutes(minutesAllowedOffline);
	    if (limit.isAfter(serverStarted)) {
			for (String key : unitLastConnected.keySet()) {
				if (unitLastConnected.get(key) == null) {
					// Detect units that haven't connected since this server started.
					unitLastConnected.put(key, serverStarted);
				}
			}
	    }
		for (String key : unitLastConnected.keySet()) {
			String status = "unknown";
			if (unitLastConnected.get(key) == null) {
				status = "not connected yet";
			} else if (unitLastConnected.get(key).isBefore(limit)) {
				ZonedDateTime zdt = unitLastConnected.get(key).atZone(ZoneId.systemDefault());
				// TODO : convert to java.time.LocalDateTime if we need this code again.
				java.util.Date output = java.util.Date.from(zdt.toInstant());
				String d = logDateFormat.format(output);
				status = "disconnected since " + d;
			} else {
				status = "connected";
			}
			StringBuilder sb = new StringBuilder(key);
			sb.append("\t").append(status);
			log(sb.toString());
		}
	}

	private void doSleep(int nMinutes) throws Exception {
		int secs = nMinutes * 60;
		logger.info("About to sleep for " + secs + " seconds.");
	    Thread.sleep(secs * 1000);
	}

	private void initBrowserDriver() {
		if (driver == null) {
			driver = new ChromeDriver();
			driver.manage().timeouts().implicitlyWait(sleepSeconds, TimeUnit.SECONDS);
			wait = new WebDriverWait(driver, sleepSeconds, 1000);
		}
	}

	private void askForCredentials() throws Exception {
		System.out.println("Enter account name.");
		byte[] name = new byte[256];
		System.in.read(name);
		System.out.println("Enter account password.");
		byte[] pw = new byte[256];
		System.in.read(pw);
		accountName = new String(name);
		accountPw = new String(pw);
	}

	private void getCredentials() throws Exception {
		String credentialsFileName = getCredentialsFileName();
		File f = new File(credentialsFileName);
		if (f.exists()) {
			try {
				BufferedReader br = new BufferedReader(new FileReader(credentialsFileName));
				try {
					String creds[] = br.readLine().split(" ");
					accountName = creds[0];
					accountPw = creds[1] + "\r\n"; // TODO : probably only works on Windows.
				} finally {
					br.close();
				}
			} catch (Exception e) {
				askForCredentials();
			}
		} else {
			System.out.println("No credentials file : " + credentialsFileName);
			askForCredentials();
		}
	}

	private void takeScreenshot() {
		try {
			File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
			String fn = getHomeDir() + File.separator + "Documents" + File.separator + "Github" + File.separator
					+ "screenshot_" + UUID.randomUUID().toString() + ".png";
			FileUtils.copyFile(src, new File(fn));
			log("takeScreenshot() : " + fn);
		} catch (Exception e) {
			log("takeScreenshot() exception : " + e.toString());
		}
	}

	@SuppressWarnings("unused")
	private void monitorDevices() {
		while (true) {
			try {
				initBrowserDriver();
				login();
				watchUnits();
				doSleep(minutesBetweenDeviceScans);
				driver.quit();
			} catch (Exception e) {
				takeScreenshot();
				log("run() : " + e.toString());
			} finally {
				// Try to leave previous instance of browser running for diagnostic purposes.
				driver = null;
			}
		}
	}

	private void clickOnDevice(String deviceName) {
		List<WebElement> devices = driver.findElements(By.className("device"));
		for (WebElement myElement : devices) {
			WebElement name = myElement.findElement(By.className("device-name"));
			if (name.getText().equals(deviceName)) {
				name.click();
				break;
			}
		}
	}

	private LocalDateTime goToDevice(String deviceName) throws Exception {
		initBrowserDriver();
		login();
		Thread.sleep(5 * 1000);
		driver.get("https://console.particle.io/devices");
		Thread.sleep(5 * 1000);
		clickOnDevice(deviceName);
		Thread.sleep(5 * 1000);
		return LocalDateTime.now();
	}

	private void monitorMsgs(String deviceName) throws Exception {
		getCredentials();
		LocalDateTime mostRecent = goToDevice(deviceName);
		while (true) {
			monitorMessages(deviceName);
			Duration d = Duration.between(LocalDateTime.now(), mostRecent);
			if (d.getSeconds() > 60 * 60) {
				// Web page stops updating after a while. Restart it after an hour.
				driver.quit();
				driver = null;
				mostRecent = goToDevice(deviceName);
			}
			doSleep(minutesBetweenMessageScans);
		}
	}

	public void run() {
		try {
			// testParsing();
			monitorMsgs("test-8EJV");
			// monitorDevices();
		} catch (Throwable e) {
			log("run() : " + e.getClass().getName() + " " + e.getMessage());
			driver = null;
		} finally {
			if (driver != null) {
				driver.quit();
				driver = null;
			}
		}
	}

	public static void main(String[] args) {
		try {
			new UnitWatcher(args).run();
		} catch (Exception e) {
			System.out.println("main() : " + LocalDateTime.now().toString() + "\t" + e.getClass().getName() + " " + e.getMessage());
		}
	}
}
