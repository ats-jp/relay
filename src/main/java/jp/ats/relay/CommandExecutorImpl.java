package jp.ats.relay;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

public class CommandExecutorImpl extends CommandExecutor {

	private static final String lineSeparator = System.getProperty("line.separator");

	/**
	 * 外部コマンドを実行する
	 * @param configFunction ProcessBuilderに設定をするラムダ式
	 * @param in null可 外部コマンドに渡す
	 * @param charset 出力の文字コード
	 * @return 外部コマンドの標準出力
	 * @throws InterruptedException
	 */
	@Override
	public List<String> execute(
		Consumer<ProcessBuilder> configFunction,
		InputStream in,
		Charset charset)
		throws InterruptedException {
		ProcessBuilder pb = new ProcessBuilder();
		if (configFunction != null) configFunction.accept(pb);

		Process process;
		try {
			process = pb.start();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		List<String> out = new LinkedList<>();
		List<String> err = new LinkedList<>();

		int result = execute(process, in, out, err, charset);

		if (result != 0 || hasError(err)) {
			synchronized (err) {
				throw new RuntimeException("Process実行エラー: " + String.join(lineSeparator, err));
			}
		}

		synchronized (out) {
			return new LinkedList<>(out);
		}
	}

	private static boolean hasError(List<String> err) {
		synchronized (err) {
			return err.size() > 0;
		}
	}

	private static int execute(
		Process process,
		InputStream in,
		List<String> out,
		List<String> err,
		Charset charset) {
		InputStream outStream = new BufferedInputStream(
			process.getInputStream());
		InputStream errStream = new BufferedInputStream(
			process.getErrorStream());
		OutputStream sendStream = new BufferedOutputStream(
			process.getOutputStream());

		try {
			Thread outThread = skip(outStream, out, charset);
			Thread errThread = skip(errStream, err, charset);

			if (in != null) sendBytes(in, sendStream);

			sendStream.close();

			process.waitFor();

			outThread.join();
			errThread.join();

			return process.exitValue();
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		} finally {
			try {
				outStream.close();
			} catch (IOException e) {}

			try {
				errStream.close();
			} catch (IOException e) {}
		}
	}

	private static Thread skip(InputStream stream, List<String> buffer, Charset charset) {
		Skipper skipper = new Skipper(stream, buffer, charset);
		Thread runner = new Thread(skipper);
		runner.start();
		return runner;
	}

	private static class Skipper implements Runnable {

		private final InputStream stream;

		private final List<String> buffer;

		private final Charset charset;

		private Skipper(InputStream stream, List<String> buffer, Charset charset) {
			this.stream = stream;
			this.buffer = buffer;
			this.charset = charset;
		}

		@Override
		public void run() {
			try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(stream, charset))) {
				synchronized (buffer) {
					for (String line; (line = reader.readLine()) != null;) {
						buffer.add(line);
					}
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	private static final int BUFFER_SIZE = 1024 * 100;

	/**
	 * in から読み込めるだけ読み込み、out へ出力します。
	 * @param in 
	 * @param out 
	 * @throws IOException 
	 */
	private static void sendBytes(InputStream in, OutputStream out)
		throws IOException {
		byte[] b = new byte[BUFFER_SIZE];
		int readed;
		while ((readed = in.read(b, 0, BUFFER_SIZE)) > 0) {
			out.write(b, 0, readed);
		}

		out.flush();
	}
}
