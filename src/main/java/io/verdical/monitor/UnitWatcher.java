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
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class UnitWatcher {
	
	private static final Logger logger = Logger.getLogger(UnitWatcher.class.getName());

	// I have seen it take up to half an hour for an unplugged Photon to register in the Particle cloud.
	// In production, don't scan more often than once every 30 minutes.
	private static int minutesBetweenScans = 30;
	private static int minutesAllowedOffline = 24 * 60;

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
		if (isDebug) {
			minutesBetweenScans = 5;
			minutesAllowedOffline = 3;
		}
		logger.info("Logging to " + logFileName);
		unitLastConnected.put("Saha_2", null);
		unitLastConnected.put("Saha-1", null);
		unitLastConnected.put("JARDINIERE", null);
		unitLastConnected.put("bobcat_pizza", null);
		unitLastConnected.put("MCDS-ver_2", null);
		unitLastConnected.put("TSWE", null);
		unitLastConnected.put("test-4a", null);
		unitLastConnected.put("TEST-5", null);
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
		String fn = path + File.separator + "particle_log_" + UUID.randomUUID().toString() + ".txt";
		File f = new File(fn);
		if (f.exists()) {
			throw new IllegalArgumentException(
					"File already exists : " + fn);
		}
		return fn;
    }

	private void log(String s) {
		logger.info(s);
		String d = logDateFormat.format(new Date());
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

	private void setUpPage() throws Exception {
		driver.get("https://build.particle.io/");
		Thread.sleep(2 * 1000);
		WebElement name = driver.findElement(By.name("username"));
		name.sendKeys(accountName);
		Thread.sleep(2 * 1000);
		WebElement pw = driver.findElement(By.name("password"));
		pw.sendKeys(accountPw);
		goToDevices();
	}

	private void goToDevices() throws Exception {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(By.className("ion-pinpoint")));
			WebElement devicesEl = driver.findElement(By.className("ion-pinpoint"));
			devicesEl.click();
		} catch (Exception e) {
			throw new Exception("Error waiting for elementToBeClickable By.className(\"ion-pinpoint\"). Are you logged into build.particle.io?");
		}
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

	private void watchUnits() throws Exception {
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
				Date output = Date.from(zdt.toInstant());
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

	private void doSleep() throws Exception {
		int secs = minutesBetweenScans * 60;
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

	public void run() {
		try {
			getCredentials();
			while (true) {
				initBrowserDriver();
				setUpPage();
				watchUnits();
				doSleep();
				driver.quit();
				driver = null;
			}
		} catch (Throwable e) {
			log("run() : " + e.toString());
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
			System.out.println(new Date().toString() + "\t" + e.getMessage());
		}
	}
}
