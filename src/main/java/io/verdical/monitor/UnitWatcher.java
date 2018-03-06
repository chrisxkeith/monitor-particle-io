// Copyright 2018 Verdical, Inc.

package io.verdical.monitor;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
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

	private static final int MINUTES_BETWEEN_SCANS = 60;
	private static final int MINUTES_ALLOWED_OFFLINE = 24 * 60;

	private String accountName;
	private String accountPw;

	final boolean isDebug;
	final int sleepFactor = 2; // increase for slower computers.
	final int sleepSeconds = 10 * sleepFactor;

	private WebDriver driver;
	private WebDriverWait wait;
	
    private Map<String, LocalDateTime> unitLastAlive = new HashMap<String, LocalDateTime>();

	final private String logFileName = getLogFileName();
	final private SimpleDateFormat logDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");

	public UnitWatcher(String[] args) throws Exception {
		isDebug = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().toString()
				.indexOf("jdwp") >= 0;
		logger.info("Logging to " + logFileName);
	}

	private String getLogFileName() throws Exception {
		String d = System.getProperty("user.home");
		if (d == null || d.length() == 0) {
			throw new IllegalArgumentException(
					"Unable to determine user.home directory from System.getProperty(\"user.home\")");
		}
		String path = d + File.separator + "Documents" + File.separator + "Github" + File.separator
				+ "monitor-particle-io";
		File dir = new File(path);
		if (!dir.exists()) {
			throw new IllegalArgumentException(
					"No such directory : " + path);
		}
		String fn = path + File.separator + "log_" + UUID.randomUUID().toString();
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
		StringBuilder msg = new StringBuilder("Units alive : ");
		for (String name : unitsAlive) {
			unitLastAlive.put(name, currentTime);
			msg.append(name).append(" ");
		}
		log(msg.toString());
	    LocalDateTime limit = LocalDateTime.now().minusMinutes(MINUTES_ALLOWED_OFFLINE);
		for (String key : unitLastAlive.keySet()) {
			if (unitLastAlive.get(key).isBefore(limit)) {
				logger.severe(key + " : hasn't been heard from since " + limit);
			}
		}
	}

	private void doSleep() throws Exception {
		int secs = MINUTES_BETWEEN_SCANS * 60;
		log("About to sleep for " + secs + " seconds.");
	    Thread.sleep(secs * 1000);
	}

	private void initBrowserDriver() {
		if (driver == null) {
			driver = new ChromeDriver();
			driver.manage().timeouts().implicitlyWait(sleepSeconds, TimeUnit.SECONDS);
			wait = new WebDriverWait(driver, sleepSeconds, 1000);
		}
	}

	public void run() {
		try {
			System.out.println("Enter account name.");
			byte[] name = new byte[256];
			System.in.read(name);
			System.out.println("Enter account password.");
			byte[] pw = new byte[256];
			System.in.read(pw);
			accountName = new String(name);
			accountPw = new String(pw);
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
