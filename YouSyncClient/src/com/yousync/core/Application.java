/**
 * 
 */
package com.yousync.core;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

import com.yousync.model.SoftwareObject;
import com.yousync.ui.ApplicationWindow;
import com.yousync.ui.ProcessBarDialog;
import com.yousync.util.AdbUtils;
import com.yousync.util.LogUtils;
import com.yousync.util.StringUtil;

/**
 * @author quanzhi
 * 
 */
public class Application {

	public final static long CHECK_DEVICE_CONNECT_INTERVAL = 5000;

	private static ApplicationWindow applicationWindow;
	private static String applicationPath;
	public final static String softWareUrl = "http://yousync.ignag.com/yousync/api/softdb";
	public final static String versionUrl = "http://yousync.ignag.com/yousync/api/version";
	// public final static String softWareUrl =
	// "http://localhost:8080/yousync/api/softdb";
	// public final static String versionUrl =
	// "http://localhost:8080/yousync/api/version";
	// public final static String softWareUrl =
	// "http://img1.kyimg.com/imgp/ys.db";
	// public final static String versionUrl =
	// "http://img1.kyimg.com/imgp/version.txt";

	private static List<SoftwareObject> models = new ArrayList<SoftwareObject>();
	private static String cacheFileDir = "";
	public static String deviceNum = "";
	public static String deviceBrand = "";
	public static String macAddr = "";
	public static String channel = "";

	public static void setModels(List<SoftwareObject> objs) {
		models = objs;
		applicationWindow.setSoftModels(objs);
	}

	public static List<SoftwareObject> getModels() {
		return models;
	}

	public static void initialization(ApplicationWindow applicationWindowObj) {
		applicationWindow = applicationWindowObj;
		loadProperties();
	}

	public static void loadDataModel() {
		startSoftUpdateThread(applicationPath + File.separator + "configs"
				+ File.separator + "ys.db", applicationPath + File.separator
				+ "configs" + File.separator + "version.txt");
	}

	public static void unLoadDataModel() {
		applicationWindow.setSoftModels(new ArrayList());
	}

	private static void loadProperties() {
		macAddr = getMac();
		applicationPath = System.getProperty("user.dir");
		initChannel();
		LogUtils.init(applicationPath + File.separator + "configs"
				+ File.separator + "logs");
		AdbUtils.init(applicationPath + File.separator + "configs"
				+ File.separator + "adb");
		cacheFileDir = applicationPath + File.separator + "cache";
		checkCacheFileDirExist();

		startDeviceCheckThread();
	}

	static String hexByte(byte b) {
		// String s = "000000" + Integer.toHexString(b);
		// return s.substring(s.length() - 2);
		return String.format("%02x", b); // 网友的建议,可修改为这个代码，更简单通用一些
	}

	public static void updateChannel(String pChannel) {
		channel = pChannel;
		File channelFile = new File(applicationPath + File.separator + "c.txt");
		try {
			FileUtils.writeStringToFile(channelFile, pChannel, "utf-8");
			startSoftUpdateThread(applicationPath + File.separator + "configs"
					+ File.separator + "ys.db", applicationPath
					+ File.separator + "configs" + File.separator
					+ "version.txt");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void initChannel() {
		File channelFile = new File(applicationPath + File.separator + "c.txt");
		if (channelFile.exists()) {
			try {
				channel = FileUtils.readFileToString(channelFile, "utf-8");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}

	public static String getMac() {
		try {
			Enumeration<NetworkInterface> el = NetworkInterface
					.getNetworkInterfaces();
			while (el.hasMoreElements()) {
				byte[] mac = el.nextElement().getHardwareAddress();
				if (mac == null)
					continue;
				StringBuilder builder = new StringBuilder();
				for (byte b : mac) {
					builder.append(hexByte(b));
					builder.append("-");
				}
				if (builder.length() > 1) {
					builder.deleteCharAt(builder.length() - 1);
				}
				return builder.toString();
			}
		} catch (Exception exception) {
			exception.printStackTrace();
		}
		return "";
	}

	private static void checkCacheFileDirExist() {
		File icacheFileDir = new File(cacheFileDir);
		if (!icacheFileDir.exists()) {
			icacheFileDir.mkdir();
		}
	}

	private static void startDeviceCheckThread() {
		Thread thread = new Thread(new DeviceCheckThread());
		thread.start();
	}

	private static void startSoftUpdateThread(String dbFile, String versionFile) {
		Thread thread = new Thread(new CheckSoftwareUpdateThread(dbFile,
				versionFile));
		thread.start();
	}

	public static void close() {
		LogUtils.close();
	}

	public static String checkCacheFileExist(String url, String version) {
		String filePath = getCacheFilePath(url, version);
		File cacheFile = new File(filePath);
		if (cacheFile.exists()) {
			return filePath;
		}
		return "";
	}

	public static String getCacheFilePath(String url, String version) {
		long fp = StringUtil.createKey(url);
		String sep = "";
		if (fp < 0) {
			sep = "q";
			fp = Math.abs(fp);
		}

		String prefix = "";
		try {
			prefix = url.substring(url.lastIndexOf("."));
		} catch (Exception e) {
			e.printStackTrace();
		}

		if (prefix.length() > 5 || prefix.indexOf(File.separator) > 0) {
			prefix = ".apk";
		}
		StringBuilder builder = new StringBuilder();
		builder.append(cacheFileDir);
		builder.append(File.separator);
		builder.append(fp % 1000);
		File dir = new File(builder.toString());
		if (!dir.exists()) {
			dir.mkdirs();
		}
		builder.append(File.separator);
		builder.append(sep);
		builder.append(fp);
		builder.append(version);
		builder.append(prefix);
		return builder.toString();
	}

	public static void installAPK(final SoftwareObject softwareObject,
			final InstallCallBack callBack) {

		String apkFilepath = "";
		if (StringUtils.isNotEmpty(softwareObject.getCacheSoftFile())) {
			apkFilepath = softwareObject.getCacheSoftFile();
		} else {
			String cacheFile = Application.checkCacheFileExist(
					softwareObject.getSoftUrl(), softwareObject.getVersion());
			apkFilepath = cacheFile;
		}
		if (StringUtils.isEmpty(Application.deviceNum)) {
			JOptionPane.showMessageDialog(null, "没有连接设备", "消息",
					JOptionPane.WARNING_MESSAGE);
			return;
		} else if (StringUtils.isNotEmpty(apkFilepath)) {
			// Application.installAPK(softwareObject);
		} else {
			JOptionPane.showMessageDialog(null, "安装包正在下载中，请稍后尝试", "消息",
					JOptionPane.WARNING_MESSAGE);
			callBack.finishInstall();
			return;
		}

		final ProcessBarDialog dialog = new ProcessBarDialog(softwareObject.getSoftName()+" 安装中");
		dialog.setVisible(true);

		applicationWindow.setRightStatus("安装中", true);

		final String fapkFilepath = apkFilepath;

		Thread installThread = new Thread(new Runnable() {
			public void run() {
				AdbUtils.uninstallSoft(softwareObject.getSoftId());
				final boolean installStatus = AdbUtils.installApk(fapkFilepath);
				try {
					SwingUtilities.invokeAndWait(new Runnable() {
						public void run() {

							String status = "";
							if (installStatus) {
								status = softwareObject.getSoftName()+" 安装成功！";
								System.out.println(Application.deviceNum + ":"
										+ Application.deviceBrand + ":"
										+ softwareObject.getSoftId() + ":"
										+ softwareObject.getSoftName());
								BussinessService.installRecord(
										softwareObject.getId(),
										softwareObject.getVersion(),
										Application.deviceNum,
										Application.deviceBrand,
										softwareObject.getSoftName(),
										Application.macAddr);
							} else
								status = softwareObject.getSoftName()+ "安装失败！";
							applicationWindow.setRightStatus(status, false);
							dialog.finishDialog(status);
							laterCloseDialog(dialog, 2000, true);
							callBack.finishInstall();
						}

					});
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		installThread.start();

	}

	public static void installAll() {
		// TODO:
		int softNum = models.size();
		if(softNum > 0){
			installAPK(models.get(0),new InstallCallBack(){
				int index = 0;
				@Override
				public void finishInstall() {
					index++;
					if(index < models.size()){
						installAPK(models.get(index),this);
					}
					
				}});
		}
	}

	private static void laterCloseDialog(final JDialog dialog,
			final long delay, final boolean clearStatus) {
		Thread thread = new Thread(new Runnable() {
			public void run() {
				try {
					Thread.sleep(delay);
					SwingUtilities.invokeAndWait(new Runnable() {
						public void run() {
							dialog.dispose();
							if (clearStatus)
								applicationWindow.setRightStatus("", false);
						}
					});
				} catch (InterruptedException e) {
					e.printStackTrace();
				} catch (InvocationTargetException e) {
					e.printStackTrace();
				}
			}

		});
		thread.start();
	}

	public static void setRightStatus(final String statusValue,
			final boolean isLoading) {
		try {
			SwingUtilities.invokeAndWait(new Runnable() {
				public void run() {
					applicationWindow.setRightStatus(statusValue, isLoading);
				}
			});
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}
	}

	public static void setCenterStatus(final String statusValue) {
		try {
			SwingUtilities.invokeAndWait(new Runnable() {
				public void run() {
					applicationWindow.setCenterStatus(statusValue);
				}
			});
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Show device sn and brand information on the application window status
	 * label
	 * 
	 * @param deviceNum
	 * @param deviceBrand
	 */
	public static void setDeviceStatus(final String deviceNum,
			final String deviceBrand) {
		Application.deviceBrand = deviceBrand;
		Application.deviceNum = deviceNum;
		StringBuilder statusBuilder = new StringBuilder();
		if (StringUtils.isNotEmpty(deviceNum)) {
			statusBuilder.append("手机型号：");
			statusBuilder.append(deviceBrand);
			statusBuilder.append("    ");
			statusBuilder.append("手机SN号：");
			statusBuilder.append(deviceNum);
		} else {
			statusBuilder.append("未连接设备");
		}
		final String status = statusBuilder.toString();
		try {
			SwingUtilities.invokeAndWait(new Runnable() {
				public void run() {
					applicationWindow.setStatus(status);
					LogUtils.log(status);
				}
			});
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		initialization(null);
	}
}
