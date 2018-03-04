// Copyright 2018 Verdical, Inc.

package io.verdical.monitor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

	private String accountName;
	private String accountPw;

	final boolean isDebug;
	final int sleepFactor = 2; // increase for slower computers.
	final int sleepSeconds = 10 * sleepFactor;

	private WebDriver driver;
	private WebDriverWait wait;
	
    private Map<String, LocalDateTime> unitLastAlive = new HashMap<String, LocalDateTime>();

	public UnitWatcher(String[] args) throws Exception {
		isDebug = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().toString()
				.indexOf("jdwp") >= 0;
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

	private void watchUnits() throws Exception {
	    LocalDateTime currentTime = LocalDateTime.now();
	    List<String> unitsAlive = new ArrayList<String>();

// Use when code is added to navigate to Particle console and record 'pump on' messages.
//		    LocalDateTime date2 = currentTime.withMinute(59);
//		    Duration duration = Duration.between(date2, currentTime);
//		    Thread.sleep(duration.toMinutes() * 60 * 1000);

//			for (int seconds = 0; seconds < 300; seconds += 10) {
			WebElement listEl = driver.findElement(By.className("cores"));
			List<WebElement> photons = listEl.findElements(By.className("breathing"));
			for (WebElement myElement : photons) {
				// div > div > li 
				WebElement greatgrandparent = myElement.findElement(By.xpath("../../.."));
				WebElement title = greatgrandparent.findElement(By.className("title"));
				unitsAlive.add(title.getText());
			}
//		}
		StringBuilder msg = new StringBuilder("Units alive : ");
		for (String name : unitsAlive) {
			unitLastAlive.put(name, currentTime);
			msg.append(name).append(" ");
		}
		logger.info(msg.toString());
		currentTime = LocalDateTime.now();
		// TODO : may fail on Jan 1?
	    LocalDateTime limit = currentTime.withMinute(currentTime.getMinute() - 1);
		for (String key : unitLastAlive.keySet()) {
			if (unitLastAlive.get(key).isBefore(limit)) {
				logger.severe(key + " : hasn't been heard from since " + limit);
			}
		}
	}

	private void doSleep() throws Exception {
		int secs = 60 * 60;
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
			logger.severe("run() : " + e.toString());
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
