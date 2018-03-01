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
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class UnitWatcher {
	
	private static final Logger logger = Logger.getLogger(UnitWatcher.class.getName());

	final boolean isDebug;
	final int sleepFactor = 2; // increase for slower computers.
	final int sleepSeconds = 30 * sleepFactor;

	private WebDriver driver;
	private WebDriverWait wait;
	
    private Map<String, LocalDateTime> unitLastAlive = new HashMap<String, LocalDateTime>();

	public UnitWatcher(String[] args) throws Exception {
		isDebug = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().toString()
				.indexOf("jdwp") >= 0;
		String d = System.getProperty("user.home");
		if (d == null || d.length() == 0) {
			throw new IllegalArgumentException(
					"Unable to determine user.home directory from System.getProperty(\"user.home\")");
		}
	}

	private void setUpPage() throws Exception {
		driver.get("https://build.particle.io/");
		try {
			wait.until(ExpectedConditions.elementToBeClickable(By.className("ion-pinpoint")));
			WebElement devicesEl = driver.findElement(By.className("ion-pinpoint"));
			devicesEl.click();
		} catch (Exception e) {
			throw new Exception("Error waiting for element By.className(\"ion-pinpoint\"). Are you logged into build.particle.io?");
		}
		try {
			wait.until(ExpectedConditions.elementToBeClickable(By.className("btn newcore")));
		} catch (Exception e) {
			throw new Exception("Error waiting for element By.className(\"btn newcore\"). Are you logged into build.particle.io?");
		}
	}

	private void watchUnits() throws Exception {
		while (true) {
			// Use when code is added to navigate to console and see messages.
		    LocalDateTime currentTime = LocalDateTime.now();
//		    LocalDateTime date2 = currentTime.withMinute(59);
//		    Duration duration = Duration.between(date2, currentTime);
//		    Thread.sleep(duration.toMinutes() * 60 * 1000);
		    
		    List<String> unitsAlive = new ArrayList<String>();
			for (int seconds = 0; seconds < 300; seconds += 10) {
				WebElement listEl = driver.findElement(By.className("cores"));
				List<WebElement> photons = listEl.findElements(By.className("breathing"));
				for (WebElement myElement : photons) {
					WebElement parent = myElement.findElement(By.xpath(".."));
					WebElement gp = parent.findElement(By.xpath(".."));
					WebElement title = gp.findElement(By.className("title"));
					unitsAlive.add(title.getText());
				}
			}
			for (String name : unitsAlive) {
				logger.info(name + " : alive");
				unitLastAlive.put(name, currentTime);
			}
			currentTime = LocalDateTime.now();
			// TODO : may fail on Jan 1?
		    LocalDateTime limit = currentTime.withDayOfYear(currentTime.getDayOfYear() - 1);
			for (String key : unitLastAlive.keySet()) {
				if (unitLastAlive.get(key).isBefore(limit)) {
					logger.severe(key + " : hasn't had a pump on since " + limit);
				}
			}
		    Thread.sleep(60 * 60 * 1000);
		}
	}

	private void initBrowserDriver() {
		if (driver == null) {
			driver = new FirefoxDriver();
			driver.manage().timeouts().implicitlyWait(sleepSeconds, TimeUnit.SECONDS);
			wait = new WebDriverWait(driver, sleepSeconds, 1000);
		}
	}

	public void run() {
		try {
			initBrowserDriver();
			setUpPage();
			watchUnits();
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
