package jp.ats.relay;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.blendee.jdbc.BTransaction;

import jp.ats.relay.ConcurrentExecutor.Disposer;

public abstract class QueueProcess implements ShellClient {

	//ロック内で使用するロガー
	private static final Logger logger = LogManager.getLogger(QueueProcess.class.getName());

	//次処理起動用のインターバルをとるための処理件数
	private static final int chunkCount = 100;

	private static final AtomicInteger threadCounter = new AtomicInteger(0);

	/**
	 * @return 対象となるファイルを格納するキューディレクトリ
	 */
	protected abstract Path getQueueDirectory();

	/**
	 * @return 自分が処理中であることを表すロックディレクトリ
	 */
	protected abstract Path getLockDirectory();

	/**
	 * @return 次処理のコマンドパス
	 */
	protected abstract String getNextCommandPath();

	/**
	 * @return 次処理が存在するかどうか
	 */
	protected abstract boolean hasNext();

	/**
	 * @return 次処理のロックディレクトリ
	 */
	protected abstract Path getNextCommandLockDirectory();

	/**
	 * @return 次処理のキューディレクトリ
	 */
	protected abstract Path getNextCommandQueueDirectory();

	/**
	 * @return 最大同時実行スレッド数
	 */
	protected abstract int getMaxConcurrency();

	/**
	 * 自処理を実行する
	 * process内では<br>
	 * {@link Shell#transaction()}<br>
	 * {@link Shell#config()}<br>
	 * {@link Shell#args()}<br>
	 * を使用することが可能
	 * @param next キューディレクトリ内の次の対象ファイル
	 * @return 次処理のために移動するファイル
	 */
	protected abstract Path process(Path next);

	/**
	 * 各処理対象の処理を始める前に行う処理のフック<br>
	 * mainスレッドが実行<br>
	 * ロック取得後に呼び出される
	 */
	protected void preProcessWithLock() {}

	/**
	 * 各処理対象の処理がすべて終了した際に行う処理のフック<br>
	 * mainスレッドが実行<br>
	 * ロック解放前に呼び出される<br>
	 */
	protected void postProcessWithLock() {}

	/**
	 * @return 処理速度記録ファイルのパス
	 */
	protected abstract String getSpeedFileName();

	@Override
	public void execute() {
		throw new UnsupportedOperationException();
	}

	public static int countQueueDirectory(Path queueDirectory) {
		//ファイルディスクリプタがオープンのまま溜まるので都度クローズする
		try (Stream<Path> stream = stream(queueDirectory)) {
			return (int) stream.count();
		}
	}

	@Override
	public void start() {
		//ロックして全件処理中に、前処理がファイルを置いた場合に備えてループ
		while (count() > 0) { //対象ファイルの取得は、ロック解放状態でやらなければならない
			Path lockDirectory = getLockDirectory();
			try {
				//ロックを取得
				Files.createDirectory(lockDirectory);
			} catch (FileAlreadyExistsException e) {
				//既にロックが取得されていた場合、自プロセスは終了する
				return;
			} catch (IOException e) {
				//ロック外なのでShellのLoggerを使用
				Shell.handleException(e);
				return;
			}

			try {
				/*
				 * !!注意!!
				 * ロック外は複数プロセスが実行している可能性がある
				 * 複数プロセスからログ出力を行うと、ログファイルが破壊されてしまうので
				 * ロックを取得したこの中でのみログ出力を行うこと
				 */
				if (!processWithLock()) return;
			} finally {
				try {
					//ロック開放
					Files.delete(lockDirectory);
				} catch (IOException e) {
					//ロック外なのでShellのLoggerを使用
					Shell.handleException(e);
				}
			}
		}
	}

	//ロック中に行う処理
	protected boolean processWithLock() {
		preProcessWithLock();

		int maxConcurrency = getMaxConcurrency();
		int concurrency = computeConcurrency(maxConcurrency);

		AtomicLong speedCounter = new AtomicLong(0);

		ConcurrentExecutor<Path> executor = createConcurrentExecutor(concurrency, speedCounter);
		executor.start();

		//計測開始
		final long startSpeedNanos = System.nanoTime();

		try {
			//ロックを取得した状態で対象を取得
			//一覧取得後ロックを取得してしまうと、他処理によりファイルが処理されてなくなってしまうことがあるため
			for (int i = count(); i > 0; i = count()) {
				if (halted()) {
					logger.warn("process halted.");
					//処理を中断
					return false;
				}

				Runnable interval = () -> {
					//定期的に記録
					record(speedCounter, startSpeedNanos);

					if (halted()) return;

					//定期的に次処理起動
					if (hasNext()) {
						//次処理が起動中でなければ起動する
						NextCommand next = NextCommand.getInstance();
						if (next.canExecute(getNextCommandLockDirectory()))
							next.execute(getNextCommandPath());
					}
				};

				//指定数ずつ処理していき、その間隔で次処理を起動し、プロセスの多重起動を避ける
				executor.execute(sorted(), chunkCount, interval);

				//今回処理の処理対象がすべて完了するまでwait
				executor.waitUntilDrained();

				interval.run();

				//最初の並列数取得時に処理対象が少なく、その後大量に処理対象が増えた場合、スレッドが少ないまま
				//処理してしまうため、一旦リターンし、このメソッドを再実行することで並列処理数を増やす
				//処理対象がまだ残っていれば、並列数を増加させてこのメソッドが再実行される
				if (concurrency < computeConcurrency(maxConcurrency)) return true;
			}
		} catch (InterruptedException e) {
			logger.warn("interrpted.", e);
		} finally {
			executor.shutdown();

			record(speedCounter, startSpeedNanos);

			postProcessWithLock();
		}

		//処理を続行
		return true;
	}

	protected ConcurrentExecutor<Path> createConcurrentExecutor(int concurrency, AtomicLong speedCounter) {
		Disposer<Path> disposer = new Disposer<Path>() {

			@Override
			public void onEvent(Throwable t, long sequence, Path value) {
				logger.error("exception occurred on [" + value + "]", t);
			}

			@Override
			public void onStart(Throwable t) {
				logger.error(t.getMessage(), t);
			};

			@Override
			public void onShutdown(Throwable t) {
				logger.error(t.getMessage(), t);
			};
		};

		return ConcurrentExecutor.getInstance(
			concurrency,
			path -> consumePath(path, speedCounter),
			runnable -> {
				Shell shell = new Shell(QueueProcess.this);
				shell.setRunnable(runnable);
				return new Thread(shell, "t-" + threadCounter.incrementAndGet());
			},
			disposer);
	}

	private int computeConcurrency(int maxConcurrency) {
		//処理予定数が設定値より小さい場合は並列処理数を小さくする
		int willProcess = count();
		//最低1はあるように
		willProcess = willProcess == 0 ? 1 : willProcess;

		return willProcess > maxConcurrency ? maxConcurrency : willProcess;
	}

	private int count() {
		return countQueueDirectory(getQueueDirectory());
	}

	private Stream<Path> sorted() {
		//ディレクトリをオープンしすぎないようにstreamを一旦クローズ
		try (Stream<Path> stream = stream(getQueueDirectory())) {
			return stream.sorted(QueueProcess::compareLastModifiedTime).collect(Collectors.toList()).stream();
		}
	}

	//mainスレッドしか使用しないこと
	private Path speedFile;

	//mainスレッドが実行
	//ファイル書き込みが発生するので、ロック内で実行
	private void record(AtomicLong speedCounter, long startNanos) {
		long nanos = System.nanoTime() - startNanos;
		if (speedFile == null) {
			speedFile = Shell.resourceManager().resolvePath(Shell.config().getAssessmentDirectory()).resolve(getSpeedFileName());
		}

		try {
			Files.write(speedFile, (speedCounter.get() + " " + nanos).getBytes());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	//このメソッドの使用側はtry-with-resourceで使用し、ディレクトリをクローズすること!!
	private static Stream<Path> stream(Path queue) {
		try {
			return Files.list(queue)
				.filter(QueueProcess::isNotSkippedErrorFile)
				.filter(f -> !Files.isDirectory(f));
		} catch (IOException e) {
			throw handleIOException(e);
		}
	}

	private static ThreadLocal<Path> halt = new ThreadLocal<>();

	private static boolean halted() {
		Path path = halt.get();
		if (path == null) {
			path = Shell.resourceManager().resolvePath(Shell.config().getHaltFile());
			halt.set(path);
		}

		return Files.exists(path);
	}

	private Path invokeProcess(Path path) {
		if (usesDatabase()) {
			BTransaction transaction = Shell.transaction();
			try {
				Path result = process(path);
				//一件処理するごとにcommit
				transaction.commit();
				return result;
			} catch (Throwable t) {
				//エラー発生時はここでロールバック
				transaction.rollback();
				throw t;
			}
		} else {
			return process(path);
		}
	}

	/**
	 * workerスレッドが実行
	 */
	private void consumePath(Path f, AtomicLong speedCounter) {
		if (halted()) {
			return;
		}

		Path moveToNextFile;
		try {
			moveToNextFile = invokeProcess(f);
		} catch (Skip s) {
			//スキップされた処理対象は、次回も処理対象とするため、ここでは何もしない
			return;
		} catch (Throwable t) {//Error系もキャッチしないと、ここを抜けてしまいスレッドが停止してしまう
			String message;
			if (Files.exists(f)) {
				//想定外のエラーが発生した場合、今回の対象ファイルを
				//対象ファイル名 -> 対象ファイル名.ERROR.yyyyMMddHHmmss として退避
				//ログにその旨出力して今回の対象ファイルをスキップし、次のファイルを処理する
				String timestamp = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
				String errorFileName = f.getFileName() + ".ERROR." + timestamp;

				//退避ファイルは同じディレクトリ内に
				Path errorPath = f.getParent().resolve(errorFileName);
				try {
					Files.move(f, errorPath);
				} catch (IOException ioe) {
					throw handleIOException(ioe);
				}

				message = "想定外のエラー発生により、ファイルをスキップしました。 スキップしたファイル: " + errorFileName;
			} else {
				message = "想定外のエラーが発生しました";
			}

			//例外の発生は報告するが、throwして処理を中断することはしない
			logger.error(message, t);
			Shell.sendSystemErrorMail(t);

			//エラーが発生した場合、処理対象はリネームされているので以降の処理は行わない
			return;
		}

		//正常に処理できた場合のみカウントアップ
		speedCounter.incrementAndGet();

		if (hasNext() && moveToNextFile != null) {
			//次処理にファイルを引き継ぐ場合、移動
			move(moveToNextFile, getNextCommandQueueDirectory().toString());
		}

		try {
			//上で移動したファイルが元のファイルと違う場合もある
			//そのまま残すと次も処理対象となるので削除する
			if (Files.exists(f)) Files.delete(f);
		} catch (IOException e) {
			throw handleIOException(e);
		}
	}

	private static Path move(Path targetFile, String moveToDirectory) {
		Path moveTo;
		do {
			moveTo = Paths.get(moveToDirectory, System.currentTimeMillis() + "." + UUID.randomUUID().toString());
		} while (Files.exists(moveTo));

		//ファイルを移動し、次工程が使用できるようにする
		try {
			Files.move(targetFile, moveTo);
		} catch (IOException e) {
			throw handleIOException(e);
		}

		return moveTo;
	}

	private static RuntimeException handleIOException(IOException e) {
		logger.error("想定外のエラーが発生しました", e);
		Shell.sendSystemErrorMail(e);
		return new RuntimeException(e);
	}

	private static final Pattern skippedErrorFilePattern = Pattern.compile("\\.ERROR\\.\\d{14}$");

	private static boolean isNotSkippedErrorFile(Path path) {
		return !skippedErrorFilePattern.matcher(path.getFileName().toString()).find();
	}

	private static int compareLastModifiedTime(Path path1, Path path2) {
		try {
			return Files.getLastModifiedTime(path1).compareTo(Files.getLastModifiedTime(path2));
		} catch (IOException e) {
			throw new Error();
		}
	}

	/**
	 * workerが、処理対象をスキップし、他workerもしくは自分自身の再処理対象とするために投げる例外
	 */
	@SuppressWarnings("serial")
	protected static class Skip extends RuntimeException {}
}
