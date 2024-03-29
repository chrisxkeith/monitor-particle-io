// Copyright 2018 Verdical, Inc.

package io.verdical.monitor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;
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

public class UnitWatcher extends Thread {
	
	private static final Logger logger = Logger.getLogger(UnitWatcher.class.getName());

	private String accountName;
	private String accountPw;
	private String deviceName;
	private Integer expectedIntervalInMinutes;
	private Map<String, String> eventNames = new HashMap<String, String>();

	@SuppressWarnings("unused")
	private final boolean isDebug = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().toString()
			.indexOf("jdwp") >= 0;

	private final int sleepFactor = 2; // increase for slower computers.
	private final int sleepSeconds = 10 * sleepFactor;

	private WebDriver driver;
	private WebDriverWait wait;
	
	private String logFileName;
	private final SimpleDateFormat logDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");

	public UnitWatcher(String creds) throws Exception {
		if (!setCredentials(creds)) {
			return;
		}
		logFileName = getLogFileName();
		init();
	}

	private void init() throws Exception {
		log(deviceName + " : thread starting up.");
		logger.info("Logging to " + logFileName);
	}

	private static String getHomeDir() throws Exception {
		String d = System.getProperty("user.home");
		if (d == null || d.length() == 0) {
			throw new IllegalArgumentException(
					"Unable to determine user.home directory from System.getProperty(\"user.home\")");
		}
		return d;
	}

	private static String getCredentialsFileName() throws Exception {
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
		return path + File.separator + deviceName + "_particle_log.txt";
    }

	private void logRaw(String s) {
		logger.info(s);
		try {
			System.out.println(s);
			FileWriter fstream = new FileWriter(logFileName, true);
			fstream.write(s + System.getProperty("line.separator"));
			fstream.flush();
			fstream.close();
		} catch (Exception e) {
			System.out
					.println(s + "\tError writing log file : " + logFileName + "\t" + e.toString());
		    e.printStackTrace(new PrintStream(System.out));
		}
	}

	private void log(String s) {
		String d = logDateFormat.format(new java.util.Date());
		logRaw(d + "\t" + s);
	}

	private void login() throws Exception {
		boolean loggedIn = false;
		while (!loggedIn) {
			try {
				driver.get("https://build.particle.io/");
				Thread.sleep(2 * 1000);

                wait.until(ExpectedConditions.elementToBeClickable(By.name("username")));
				WebElement name = driver.findElement(By.name("username"));
				name.sendKeys(accountName);
                wait.until(ExpectedConditions.elementToBeClickable(By.name("password")));
				WebElement pw = driver.findElement(By.name("password"));
				pw.sendKeys(accountPw);
				loggedIn = true;
			} catch (Exception e) {
				handleException("Login failed. Retrying infinitely every 5 minutes ...", e);
				doSleep(5);
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
		    e.printStackTrace(new PrintStream(System.out));
			throw e;
		}
	}

	final private DateTimeFormatter googleSheetsDateFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
	private String buildLogString(LocalDateTime d, String eventName, String value, String deviceName) {
		String dateStr = googleSheetsDateFormat.format(d);
		return dateStr
				+ "\t" + eventName
				+ "\t" + value
				+ "\t" + deviceName;
	}

	private Map<String, String> dataAlreadyLogged = new HashMap<String, String>();

	private LocalDateTime monitorMessages(String deviceName) throws Exception {
		LocalDateTime mostRecent = null;
		for (String key : dataAlreadyLogged.keySet()) {
			String[] vals = key.split("!");
			LocalDateTime d = toDate(vals[1]);
			if ((mostRecent == null) || (mostRecent.isBefore(d))) {
				mostRecent = d;
			}
		}
		ConcurrentSkipListMap<String, String> outputRows = new ConcurrentSkipListMap<String, String>();

		List<WebElement> messages = driver.findElements(By.className("event-data"));
		for (WebElement myElement : messages) {
			try {
				WebElement parent = myElement.findElement(By.xpath(".."));
				WebElement timestamp = parent.findElement(By.className("event-timestamp"));
				WebElement eventname = parent.findElement(By.className("event-name"));
				String key = eventname.getText() + "!" + timestamp.getText();
				if (dataAlreadyLogged.get(key) == null) {
					LocalDateTime d = toDate(timestamp.getText());
					outputRows.put(key, buildLogString(d, eventname.getText(), myElement.getText(), deviceName));
					dataAlreadyLogged.put(key, myElement.getText());
					eventNames.put(eventname.getText(), eventname.getText());
					if ((mostRecent == null) || (mostRecent.isBefore(d))) {
						mostRecent = d;
					}
				}
			} catch (Exception e) {
				// Pain in the ass browser(s)...
				if (!e.getClass().equals(StaleElementReferenceException.class)) {
					log("monitorMessages : " + e);
				    e.printStackTrace(new PrintStream(System.out));
				}
			}
		}
		Set<String> keys = outputRows.keySet();
		Iterator<String> itr = keys.iterator();
		while (itr.hasNext()) {
			log(outputRows.get(itr.next()));
		}
		return mostRecent;
	}

	private void doSleep(int nMinutes) {
	    try {
			int secs = nMinutes * 60;
			logger.info(deviceName + " : About to sleep for " + nMinutes + " minutes.");
		    Thread.sleep(secs * 1000);
	    } catch (Exception e2) {
		    handleException("doSleep()", e2);
	    }
	}

	private void initBrowserDriver() {
		if (driver == null) {
			driver = new ChromeDriver();
			driver.manage().timeouts().implicitlyWait(sleepSeconds, TimeUnit.SECONDS);
			wait = new WebDriverWait(driver, sleepSeconds, 1000);
		}
	}

	private static ArrayList<String> readCredentials() throws Exception {
		String credentialsFileName = getCredentialsFileName();
		File f = new File(credentialsFileName);
		if (!f.exists()) {
			System.out.println("No credentials file : " + credentialsFileName);
			System.exit(-7);
		}
		ArrayList<String> creds = new ArrayList<String>(10);
		BufferedReader br = new BufferedReader(new FileReader(credentialsFileName));
		try {
			String s;
			while ((s = br.readLine()) != null) {
				creds.add(s);
			}
		} finally {
			br.close();
		}
		return creds;
	}

	private boolean setCredentials(String c) {
		String creds[] = c.split(" ");
		if ((creds == null) || (creds.length != 4)) {
			System.out.println("bad input : " + c);
			System.out.println("expected : accountName accountPw deviceName expectedIntervalInMinutes");
			return false;
		}
		accountName = creds[0];
		accountPw = creds[1] + "\r\n"; // TODO : may only work on Windows.
		deviceName = creds[2];
		expectedIntervalInMinutes = Integer.parseInt(creds[3]);
		return true;
	}

	private void takeScreenshot() {
		if (driver == null) {
			log("takeScreenshot() : driver not initialized.");
		} else {
			try {
				File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
				String fn = getHomeDir() + File.separator + "Documents" + File.separator + "Github" + File.separator
						+ "screenshot_" + UUID.randomUUID().toString() + ".png";
				FileUtils.copyFile(src, new File(fn));
				log("takeScreenshot() : " + fn);
			} catch (Exception e) {
				log("takeScreenshot() exception : " + e.toString());
			    e.printStackTrace(new PrintStream(System.out));
			}
		}
	}

	void handleException(String message, Throwable e) {
		takeScreenshot();
		log("handleException() : " + message + " : " + e.toString());
	    e.printStackTrace(new PrintStream(System.out));

	    StringWriter writer = new StringWriter();
	    PrintWriter printWriter = new PrintWriter( writer );
	    e.printStackTrace( printWriter );
	    printWriter.flush();
	    log(writer.toString());
	}

	private void clickOnDevice(String deviceName) throws Exception {
		boolean morePages = true;
		while (morePages) {
			Thread.sleep(5 * 2000);
			List<WebElement> devices = driver.findElements(By.className("device"));
			for (WebElement myElement : devices) {
				WebElement name = myElement.findElement(By.className("device-name"));
				if (name.getText().equals(deviceName)) {
					name.click();
					return;
				}
			}
			try {
				WebElement next = driver.findElement(By.className("next"));
				if (next != null) {
					next.findElement(By.linkText("»")).click();
				} else {
					morePages = false;
				}
			} catch (Exception e) {
				morePages = false;
			}
		}
		log("Unable to find unit named : " + deviceName);
	}

	private void doLogin() throws Exception {
		initBrowserDriver();
		login();
		Thread.sleep(5 * 1000);
	}

	private void goToDevice(String deviceName) throws Exception {
		if (driver == null) {
			doLogin();
		}
		driver.get("https://console.particle.io/devices");
		clickOnDevice(deviceName);
		Thread.sleep(5 * 1000);
		log(deviceName + " : Started browser");
	}

	private void logARowWithNoData() {
		String eventName = "unknown event";
		Set<String> names = eventNames.keySet();
		Iterator<String> eventIt = names.iterator();
		while (eventIt.hasNext()) {
			eventName = eventIt.next();
			break;
		}
		log(buildLogString(LocalDateTime.now(), eventName, "", deviceName));
	}

	private void monitorMsgs() throws Exception {
		goToDevice(deviceName);
		while (true) {
			LocalDateTime mostRecent = monitorMessages(deviceName);
			if (mostRecent != null) {
				Duration d = Duration.between(mostRecent, LocalDateTime.now());

				// We could wait 1 interval, but that might introduce noise,
				// since we can't count on the clocks on all the machines being in sync.
				if ((!eventNames.isEmpty()) && (d.getSeconds() > 2 * expectedIntervalInMinutes * 60)) {
					logARowWithNoData();
					// Web page randomly stops updating. Restart it.
					driver.quit();
					driver = null;
					goToDevice(deviceName);
				}
			}
			doSleep(2 * expectedIntervalInMinutes);
		}
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

	private boolean deviceIsConnected(String deviceName) {
		boolean ret = false;
	    try {
			doLogin();
			goToDevices();

			// TODO : will there be any issues (e.g., multiple web pages) with lots of devices?
			WebElement listEl = driver.findElement(By.className("cores"));
			List<WebElement> photons = listEl.findElements(By.className("breathing"));
			for (WebElement myElement : photons) {
				// div > div > li
				WebElement greatgrandparent = myElement.findElement(By.xpath("../../.."));
				WebElement title = greatgrandparent.findElement(By.className("title"));
				if (deviceName.equals(title.getText())) {
					ret = true;
					break;
				}
			}
			Thread.sleep(5 * 1000);
	    } catch (Exception e) {
			// Handle as if not connected.
	    }
	    if (!ret) {
			log(buildLogString(LocalDateTime.now(), "Device is not breathing blue.", "1", deviceName));
	    }
		// TODO : Would be nice to leave browser running to monitor device messages,
		// but will need to figure out how to click on the "Leave page?" dialog when going to device page.
		driver.quit();
		driver = null;
	    return ret;
	}


	public void run() {
		if (accountName == null) {
			return;
		}
		while (!deviceIsConnected(deviceName)) {
			log(deviceName + " : run() : Waiting for device to connect, retrying infinitely every 60 minutes ...");
			doSleep(60);
	    }
		while (true) {
			try {
				monitorMsgs();
			} catch (Throwable e) {
				handleException(deviceName + " : run() : Retrying infinitely every 5 minutes ...", e);
				doSleep(5);
			} finally {
				if (driver != null) {
					driver.quit();
					driver = null;
				}
			}
		}
	}

	public static void main(String[] args) {
		try {
			ArrayList<String> creds = readCredentials();
			for (String c : creds) {
				if (c != null) {
					 (new UnitWatcher(c)).start();
				}
			}
		} catch (Exception e) {
			System.out.println("main() : " + LocalDateTime.now().toString() + "\t" + e.getClass().getName() + " " + e.getMessage());
		    e.printStackTrace(new PrintStream(System.out));
		}
	}
}
