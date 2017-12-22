package jp.ats.relay;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.Properties;

import javax.mail.MessagingException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.blendee.jdbc.BTransaction;
import org.blendee.util.Blendee;

/**
 * DBのトランザクション確立、その他実装側で必要になるものを生成管理するクラスです。
 */
public class Shell implements Runnable {

	public static final Logger SHELL_LOGGER = LogManager.getLogger("shell-logger");

	private static final ThreadLocal<BTransaction> transactionThreadLocal = new ThreadLocal<>();

	private static final ThreadLocal<String[]> argsThreadLocal = new ThreadLocal<>();

	private static final ThreadLocal<ResourceManager> resourceManagerThreadLocal = new ThreadLocal<>();

	private static final ThreadLocal<Config> configThreadLocal = new ThreadLocal<>();

	private final Runnable shell;

	private volatile Runnable process;

	public Shell(ShellClient client) {
		String[] args = argsThreadLocal.get().clone();
		ResourceManager manager = resourceManagerThreadLocal.get();
		Config config = configThreadLocal.get();
		shell = () -> {
			preparePid();

			argsThreadLocal.set(args);
			resourceManagerThreadLocal.set(manager);
			configThreadLocal.set(config);
			try {
				if (client.usesDatabase()) {
					executeWithDatabase(manager, client);
				} else {
					executeWithoutDatabase();
				}
			} finally {
				configThreadLocal.set(null);
				resourceManagerThreadLocal.set(null);
				argsThreadLocal.set(null);
			}
		};
	}

	@Override
	public void run() {
		shell.run();
	}

	public void setRunnable(Runnable process) {
		this.process = process;
	}

	/**
	 * Clientを実行可能になるように準備し、実行します。
	 * @param args 起動パラメータ
	 * @param manager
	 * @param client
	 */
	public static void dispatch(String[] args, ResourceManager manager, ShellClient client) {
		preparePid();

		argsThreadLocal.set(args);
		resourceManagerThreadLocal.set(manager);
		configThreadLocal.set(new Config(manager));

		try {
			client.start();
		} catch (Exception e) {
			handleException(e);
		}
	}

	public static void handleException(Exception e) {
		SHELL_LOGGER.error(e.getMessage(), e);

		try {
			sendSystemErrorMail(e);
		} catch (Exception ee) {
			SHELL_LOGGER.error(ee.getMessage(), ee);
		}
	}

	public static <T> T newInstance(String className) {
		try {
			Class<?> clazz = Class.forName(className);

			@SuppressWarnings("unchecked")
			T instance = (T) clazz.newInstance();

			return instance;
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static void preparePid() {
		RuntimeMXBean rt = ManagementFactory.getRuntimeMXBean();
		String pid = rt.getName();

		int pidIndex = pid.indexOf('@');
		if (pidIndex > 0)
			pid = pid.substring(0, pidIndex);

		ThreadContext.put("PID", pid);
	}

	private static Object lock = new Object();

	private void executeWithDatabase(ResourceManager manager, ShellClient client) {
		//Blendee設定部
		try {
			Properties initValues = new Properties();
			try (InputStream input = manager.load(Constants.DATABASE_PROPERTIES)) {
				initValues.load(input);
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}

			synchronized (lock) {
				Blendee blendee = new Blendee();
				if (!blendee.started())
					blendee.start(initValues);
			}
		} catch (Exception e) {
			handleException(e);
			return;
		}

		//ShellClient実行部
		boolean[] logged = { false };
		try {
			Blendee.execute(t -> {
				transactionThreadLocal.set(t);
				try {
					process.run();
				} catch (Exception e) {
					SHELL_LOGGER.error(e.getMessage(), e);
					logged[0] = true;
					throw e;
				} finally {
					transactionThreadLocal.set(null);
				}
			});
		} catch (Exception e) {
			//ここでは、フレームワーク内で発生した例外のみをログに出力
			if (!logged[0]) SHELL_LOGGER.error(e.getMessage(), e);

			//処理内で発生した不明なエラーすべてが対象
			try {
				sendSystemErrorMail(e);
			} catch (Exception ee) {
				SHELL_LOGGER.error(ee.getMessage(), ee);
			}
		}
	}

	private void executeWithoutDatabase() {
		try {
			process.run();
		} catch (Exception e) {
			handleException(e);
		}
	}

	/**
	 * {@link ShellClient#execute()}内でのみトランザクションインスタンスが取得可能
	 * @return BTransaction
	 */
	public static BTransaction transaction() {
		return transactionThreadLocal.get();
	}

	/**
	 * {@link ShellClient#execute()}内でのみ起動パラメータが取得可能
	 * @return String[]
	 */
	public static String[] args() {
		return argsThreadLocal.get();
	}

	public static ResourceManager resourceManager() {
		return resourceManagerThreadLocal.get();
	}

	public static void sendSystemErrorMail(Throwable throwable) {
		Config config = config();

		if (!Boolean.parseBoolean(config.usesSystemErrorMail())) return;

		String[] mailToAddresses = config.getSystemErrorMailAdresses().split("\\s*,\\s*");
		String from = config.getErrorMailFrom();

		MailBuilder mail = MailBuilder.getInstance();
		try {
			mail.setFrom(from);

			for (String address : mailToAddresses) {
				mail.addMailTo(address);
			}
		} catch (MessagingException e) {
			throw new RuntimeException(e);
		}

		String message = buildMessage(throwable);

		mail.setMessage(message);

		try {
			mail.setSubject("[" + config.getProjectName() + "] システムエラー通知");

			for (String to : mailToAddresses) {
				CommandExecutor.getInstance().execute(
					new ByteArrayInputStream(mail.build()),
					config().getMailSendCommand(),
					from,
					to);
			}
		} catch (IOException | MessagingException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * @return 環境情報等、設定情報
	 */
	static Config config() {
		return configThreadLocal.get();
	};

	private static String buildMessage(Throwable throwable) {
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		String charset = "UTF-8";
		try {
			PrintStream printStream = new PrintStream(stream, true, charset);

			printStream.println("これは、システムの例外発生時に自動で送信されるメールです。");
			printStream.println("このメールに対して返信をしないでください。");
			printStream.println();

			printStream.println("Original:");
			throwable.printStackTrace(printStream);
			printStream.println();

			Throwable rootCause = strip(throwable);
			printStream.println("Root Cause:");
			rootCause.printStackTrace(printStream);
			return new String(stream.toByteArray(), charset);
		} catch (UnsupportedEncodingException e) {
			throw new Error(e);
		}
	}

	private static Throwable strip(Throwable throwable) {
		Throwable cause = throwable.getCause();
		if (cause == null) return throwable;

		return strip(cause);
	}
}
