package perf;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;

import java.text.DecimalFormat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RunScript {

	public CountDownLatch latch;
	public boolean runflag;
	public Pattern pattern;
	public BufferedWriter writer;

	public RunScript() {

		try {
			latch = new CountDownLatch(10);
			runflag = true;
			pattern = Pattern.compile("((AvgDelay)(\\s*:\\s*)(\\d+\\.*\\d*))");
			writer = new BufferedWriter(new FileWriter(new File("test.log")));
		} catch (Exception e) {

		}
	}

	public void run() {
		try {
			int TPS = 0;
			ArrayList<Thread> threads = new ArrayList<Thread>();

			while (runflag) {
				TPS = TPS + 500;

				// 多线程远程执行shell
				Thread thread_1 = new Thread() {
					@Override
					public void run() {
						runJS("", "root", 22, "huawei@123", "test", 60);
					}
				};
				thread_1.start();
				threads.add(thread_1);

				// 此处有循环，循环每个单板都执行
				for (int i = 0; i < 10; i++) {
					Thread thread_2 = new Thread() {
						@Override
						public void run() {
							runTop("", "root", 22, "huawei@123", "root", "java", 65);
							latch.countDown();
						}
					};
					Thread thread_3 = new Thread() {
						@Override
						public void run() {
							runSar("", "root", 22, "huawei@123", 65);
							latch.countDown();
						}
					};
					thread_2.start();
					thread_3.start();
					threads.add(thread_2);
					threads.add(thread_3);
				}

				latch.await();
			}
		} catch (Exception e) {
			// TODO: handle exception
		}
	}

	// run js, jsname = Perf + interName + .js
	public void runJS(String host, String user, int port, String pw, String interName, int seconds) {
		runflag = true;
		JSch jSch = new JSch();
		try {
			Session session = jSch.getSession(user, host, port);
			Properties properties = new Properties();
			properties.setProperty("StrictHostKeyChecking", "no");
			session.setConfig(properties);
			session.setPassword(pw);
			session.connect();
			if (session.isConnected()) {
				ChannelExec channel = (ChannelExec) session.openChannel("exec");
				channel.setCommand("node /home/tools/node/Perf" + interName + ".js");
				channel.connect();

				BufferedReader reader = new BufferedReader(new InputStreamReader(channel.getInputStream()));
				String res = "";
				ArrayList<Double> averageDelays = new ArrayList<Double>();
				int num = 0;

				long start = System.currentTimeMillis();
				long end = start + (seconds * 1000);
				while ((res = reader.readLine()) != null && (System.currentTimeMillis() < end)) {
					System.out.println(res);
					Matcher matcher = pattern.matcher(res);
					if (matcher.find()) {
						double averageDelay = Double.parseDouble(matcher.group(4));
						if (averageDelay > 250) {
							// 时延超标--处理
							averageDelays.add(averageDelay);
							runflag = false;
						} else if (averageDelay > 1000) {
							// 时延严重超标--处理
							averageDelays.add(averageDelay);
							runflag = true;
							break;
						} else {
							// 正常执行
							averageDelays.add(averageDelay);
						}
					} else {
						// 找不到Average信息，累计达1000次表示出错，直接退出
						num++;
						if (num == 1000) {
							break;
						}
					}
				}

				////// 处理平均时延信息//////
				System.out.println(getAverage(averageDelays));

				System.out.println("over");
				while (true) {
					Thread.sleep(1000);
				}

			}
		} catch (JSchException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	// run top
	public void runTop(String host, String user, int port, String pw, String key_1, String key_2, int seconds) {
		JSch jSch = new JSch();
		try {
			Session session = jSch.getSession(user, host, port);
			Properties properties = new Properties();
			properties.setProperty("StrictHostKeyChecking", "no");
			session.setConfig(properties);
			session.setPassword(pw);
			session.connect();
			if (session.isConnected()) {
				ChannelExec channel = (ChannelExec) session.openChannel("exec");
				channel.setCommand("top -d 2 -b -n 500000");
				channel.connect();

				BufferedReader reader = new BufferedReader(new InputStreamReader(channel.getInputStream()));
				String res = "";
				StringBuffer buffer = new StringBuffer();

				long start = System.currentTimeMillis();
				long end = start + (seconds * 1000);
				while ((res = reader.readLine()) != null && (System.currentTimeMillis() < end) && runflag) {
					buffer.append(res + "\r\n");
				}
				reader.close();
				session.disconnect();

				////// 解析top字符串，获取性能信息//////
				ArrayList<Double> cpuIdle = new ArrayList<Double>();
				ArrayList<Double> cpuUsed = new ArrayList<Double>();
				ArrayList<Double> virtUsed = new ArrayList<Double>();
				ArrayList<Double> memUsed = new ArrayList<Double>();

				String[] infos = buffer.toString().split("\r\n");

				for (int i = 0; i < infos.length; i++) {
					String info = infos[i].trim();
					String[] ms = null;
					if (info.startsWith("%Cpu(s)")) {
						try {
							cpuIdle.add(Double.parseDouble(info.trim().split("\\s+")[7].trim().replace("%id,", "")));
						} catch (Exception e) {
							System.out.println("[WARN]解析top信息失败 " + info);
						}
					} else if (info.contains(key_1) && info.contains(key_2)) {
						try {
							ms = info.split("\\s+");
							cpuUsed.add(Double.parseDouble(ms[8].trim()));
							if (ms[4].trim().contains("g") || ms[4].trim().contains("m")) {
								virtUsed.add(Double.parseDouble(ms[4].trim().substring(0, ms[4].trim().length() - 1)));
							} else {
								virtUsed.add(Double.parseDouble(ms[4].trim()));
							}
							memUsed.add(Double.parseDouble(ms[9].trim()));
						} catch (Exception e) {
							System.out.println("[WARN]解析top信息失败 " + info);
						}
					}
				}

				System.out.println(getAverage(cpuIdle));
				System.out.println(getAverage(cpuUsed));
				System.out.println(getAverage(virtUsed));
				System.out.println(getAverage(memUsed));
			}
		} catch (JSchException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// run sar
	public void runSar(String host, String user, int port, String pw, int seconds) {
		JSch jSch = new JSch();
		try {
			Session session = jSch.getSession(user, host, port);
			Properties properties = new Properties();
			properties.setProperty("StrictHostKeyChecking", "no");
			session.setConfig(properties);
			session.setPassword(pw);
			session.connect();
			if (session.isConnected()) {
				ChannelExec channel = (ChannelExec) session.openChannel("exec");
				channel.setCommand("sar -n DEV 1 99999999");
				channel.connect();

				BufferedReader reader = new BufferedReader(new InputStreamReader(channel.getInputStream()));
				String res = "";
				StringBuffer buffer = new StringBuffer();

				long start = System.currentTimeMillis();
				long end = start + (seconds * 1000);
				while ((res = reader.readLine()) != null && (System.currentTimeMillis() < end) && runflag) {
					buffer.append(res + "\r\n");
				}
				reader.close();
				session.disconnect();

				////// 解析sar信息//////
				ArrayList<Double> rxkBList = new ArrayList<Double>();
				ArrayList<Double> txkBList = new ArrayList<Double>();

				String[] infos = buffer.toString().split("\r\n");
				for (String info : infos) {
					if (!info.contains("Average") && info.contains("eth0")) {
						info = info.trim();
						String[] ms = info.split("\\s+");

						try {
							rxkBList.add(Double.parseDouble(ms[4]));
							txkBList.add(Double.parseDouble(ms[5]));
						} catch (Exception e) {
							System.out.println("[WARN]解析sar信息失败 " + info);
						}
					}
				}

				System.out.println(getAverage(rxkBList));
				System.out.println(getAverage(txkBList));
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (JSchException e) {
			e.printStackTrace();
		}
	}

	// getListAverage
	public String getAverage(ArrayList<Double> list) {

		try {
			if (list.size() > 10) {
				Collections.sort(list);

				ArrayList<Double> temp = new ArrayList<Double>();
				for (int i = 2; i < list.size() - 2; i++) {
					temp.add(list.get(i));
				}
				list = temp;
			}

			double sum = 0;
			int size = list.size();
			Iterator<Double> iterator = list.iterator();
			while (iterator.hasNext()) {
				Double d = iterator.next();
				sum = d + sum;
			}

			return Double.parseDouble(new DecimalFormat("#.00").format(sum / size)) + "";
		} catch (Exception e) {
			return "NA";
		}
	}

	public synchronized void logWriter(String s) {
		try {
			writer.write(s);
			writer.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}